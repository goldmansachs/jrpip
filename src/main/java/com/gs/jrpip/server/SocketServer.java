/*
  Copyright 2017 Goldman Sachs.
  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
 */
//Portions copyright Alexandr Varlamov. Licensed under Apache 2.0 license

package com.gs.jrpip.server;

import com.gs.jrpip.FixedInflaterInputStream;
import com.gs.jrpip.JrpipServiceRegistry;
import com.gs.jrpip.MethodResolver;
import com.gs.jrpip.RequestId;
import com.gs.jrpip.client.JrpipRuntimeException;
import com.gs.jrpip.client.JrpipVmBoundException;
import com.gs.jrpip.util.*;
import com.gs.jrpip.util.stream.CopyOnReadInputStream;
import com.gs.jrpip.util.stream.OutputStreamBuilder;
import com.gs.jrpip.util.stream.VirtualOutputStream;
import com.gs.jrpip.util.stream.VirtualOutputStreamFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SocketServer
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SocketServer.class.getName());

    private static final AtomicInteger CLIENT_ID = new AtomicInteger((int) (Math.random() * 20000.0) * 100000);
    private static final AtomicInteger SOCKET_COUNT = new AtomicInteger();
    private static long vmId;

    private SocketServerConfig config;

    private final HashMap<String, ServiceDefinition> serviceMap = new HashMap<>();
    private final ListenerRegistry listeners = new ListenerRegistry();

    private AtomicInteger pings = new AtomicInteger();
    private AtomicInteger methodInvocations = new AtomicInteger();
    private AtomicInteger thankYous = new AtomicInteger();
    private AtomicInteger resendRequests = new AtomicInteger();
    private long startTime;
    private final HashSet<String> registeredUrls = new HashSet<>();
    private boolean binaryLoggingEnabled;
    private volatile boolean listening;
    private int port;
    private SocketServerThread socketServerThread;
    private ConcurrentHashMap<String, UserNonces> userNonces = new ConcurrentHashMap<>();

    private ConcurrentHashMap<ServerSocketHandler, Object> hanlders = new ConcurrentHashMap();

    public SocketServer(SocketServerConfig config)
    {
        this.config = config;
    }

    public int getPort()
    {
        return port;
    }

    /**
     * Cleanup the service instance.
     */
    public void destroy()
    {
        vmId = 0L;
    }

    /**
     * Starts the server listening on the configured port.
     * @throws IOException most likely, the port couldn't be allocated
     */
    public void start() throws IOException
    {
        this.startTime = System.currentTimeMillis();

        initConfig();

        if (vmId == 0L)
        {
            vmId = System.currentTimeMillis() >> 8 << 32;
        }

        initSocketServer();
    }

    private void initSocketServer() throws IOException
    {
        this.port = this.config.getPort();
        ServerSocket socket;
        synchronized (this)
        {
            socket = new ServerSocket(port);
            if (this.config.getServerSocketTimeout() != 0)
            {
                socket.setSoTimeout(this.config.getServerSocketTimeout());
            }
            if (port == 0)
            {
                port = socket.getLocalPort();
            }
            listening = true;
            this.notifyAll();
        }
        this.socketServerThread = new SocketServerThread(socket);
        socketServerThread.start();
    }

    public void stop()
    {
        if (this.socketServerThread != null)
        {
            this.socketServerThread.shutdown();
            try
            {
                this.socketServerThread.join();
            }
            catch (InterruptedException e)
            {
                //ignore
            }
        }
    }

    public void stopAndTerminateConnections()
    {
        this.stop();
        this.terminateConnections();
    }

    public void terminateConnections()
    {
        for (ServerSocketHandler ssh: this.hanlders.keySet())
        {
            try
            {
                ssh.socket.close();
            }
            catch (Exception ignore)
            {

            }
        }
    }

    public synchronized void waitForStartup()
    {
        while(!listening)
        {
            try
            {
                this.wait();
            }
            catch (InterruptedException e)
            {
                //ignore
            }
        }
    }

    private void initConfig()
    {
        for(int i=0;i< this.config.getListeners().size();i++)
        {
            this.listeners.register("L"+i, this.config.getListeners().get(i));
        }
        for(SingleServiceConfig cfg: this.config.getConfigs())
        {
            Class interfaceClass = cfg.getServiceInterface();
            Class serviceClass = cfg.getServiceClass();
            boolean serviceImplementsInterface = interfaceClass.isAssignableFrom(serviceClass);
            if (!serviceImplementsInterface)
            {
                LOGGER.warn("The class {} does not implement {}. This may be a serious error in your configuration. This class will not be available locally.",
                        serviceClass.getName(), interfaceClass.getName());
            }
            Object service = cfg.getOrConstructService();
            MethodResolver methodResolver = new MethodResolver(serviceClass);
            ServiceDefinition value = new ServiceDefinition(service, methodResolver,
                    this.initializeOutputStreamBuilder(interfaceClass), cfg.isVmBound());
            value.setServiceInterface(interfaceClass);
            this.serviceMap.put(interfaceClass.getName(), value);
        }
        if (this.serviceMap.isEmpty())
        {
            throw new JrpipRuntimeException(
                    "No configuration found!");
        }
    }

    private OutputStreamBuilder initializeOutputStreamBuilder(Class interfaceClass)
    {
        String name = interfaceClass.getSimpleName();
        if (Boolean.parseBoolean(System.getProperty("jrpip.enableBinaryLogs")) || Boolean.parseBoolean(System.getProperty(name + ".enableBinaryLogs")))
        {
            this.binaryLoggingEnabled = true;
            return VirtualOutputStreamFactory.create(name, System.getProperty("jrpip.binaryLogsDirectory", "jrpipBinaryLogs"));
        }
        return VirtualOutputStream.NULL_OUTPUT_STREAM_BUILDER;
    }

    private class SocketServerThread extends Thread
    {
        private final ServerSocket serverSocket;
        private volatile boolean shutdown;

        public SocketServerThread(ServerSocket serverSocket)
        {
            super("JrpipSockerServer Port "+serverSocket.getLocalPort());
            this.serverSocket = serverSocket;
        }

        public void shutdown()
        {
            this.shutdown = true;
        }

        @Override
        public void run()
        {
            long lastLogTime = System.currentTimeMillis();
            String interfaces = "";
            for (Object o : serviceMap.keySet())
            {
                interfaces += o + " ; ";
            }
            LOGGER.info("Waiting for connections on port " + port + " server id is: " + vmId + " Servicing interfaces: "+interfaces);
            while(!shutdown)
            {
                Socket incoming;
                try
                {
                    incoming = serverSocket.accept();
                    ServerSocketHandler serverSocketHandler = new ServerSocketHandler(incoming);
                    serverSocketHandler.start();
                    SocketServer.this.hanlders.put(serverSocketHandler, "1");
                }
                catch (SocketTimeoutException e)
                {
                    //ignore, we're just using this to check the shutdown flag occasionally
                }
                catch(IOException e)
                {
                    boolean closed = this.serverSocket.isClosed();
                    String message = "Could not accept incoming socket.";
                    if (closed)
                    {
                        message += " Server socket is closed. Quitting!";
                    }
                    LOGGER.error(message, e);
                    if (closed)
                    {
                        break;
                    }
                }
                if (config.getLogStatsInterval() > 0 && System.currentTimeMillis() > lastLogTime + config.getLogStatsInterval())
                {
                    logStats();
                    lastLogTime = System.currentTimeMillis();
                }
            }
            quietlyClose(serverSocket);
        }
    }

    private class ServerSocketHandler extends Thread
    {
        private static final int WAITING_FOR_TYPE = 2;
        private static final int READING_REQUEST = 3;
        private final Socket socket;
        private BlockInputStream inputStream;
        private BlockOutputStream outputStream;
        private boolean authorized = false;
        private String username;
        private AuthGenerator authGenerator;
        private CipherOutputStream128 cos;
        private CipherInputStream128 cis;

        public ServerSocketHandler(Socket socket)
        {
            super("Socket Handler-"+SOCKET_COUNT.incrementAndGet());
            this.socket = socket;
        }

        @Override
        public void run()
        {
            int state = WAITING_FOR_TYPE;
            try
            {
                this.socket.setTcpNoDelay(true);
                this.socket.setKeepAlive(true);
                this.socket.setSoLinger(true, 2);
                this.socket.setSoTimeout(config.getIdleSocketCloseTime());
                inputStream = new BlockInputStream(socket.getInputStream());
                outputStream = new BlockOutputStream(socket.getOutputStream());
                while(true)
                {
                    state = WAITING_FOR_TYPE;
                    inputStream.beginConversation();
                    outputStream.beginConversation();
                    byte requestType = inputStream.readByte();
                    state = READING_REQUEST;
                    processRequest(requestType);
                    this.inputStream.endConversation();
                    this.outputStream.endConversation();
                }
            }
            catch(ClassNotFoundException cnfe)
            {
                LOGGER.error("Could not deserializer incoming stream!", cnfe);
            }
            catch(SocketTimeoutException ste)
            {
                if (state != WAITING_FOR_TYPE)
                {
                    LOGGER.warn("Got a socket timeout when reading remote request "+state);
                }
            }
            catch (EOFException eof)
            {
                if (state != WAITING_FOR_TYPE)
                {
                    LOGGER.warn("Got a EOF "+state);
                }
            }
            catch (IOException ioe)
            {
                if (state != WAITING_FOR_TYPE)
                {
                    LOGGER.warn("IOException. while handling incoming socket. Turn on debug to see stack trace " +
                            ioe.getClass().getName() + ": " + ioe.getMessage());
                    LOGGER.debug("IOException while handling incoming socket", ioe);
                }
            }
            catch (Throwable t)
            {
                LOGGER.error("Unexpected exception: ", t);
            }
            finally
            {
                quietlyClose(socket);
                try
                {
                    SocketServer.this.hanlders.remove(this);
                }
                catch (Throwable ignore)
                {

                }
            }
        }

        private void processRequest(byte requestType) throws IOException, ClassNotFoundException
        {
            if (requestType == StreamBasedInvocator.PING_REQUEST)
            {
                pings.incrementAndGet();
                outputStream.write(StreamBasedInvocator.PING_REQUEST);
                return;
            }
            byte reqTypeWithoutMasks = StreamBasedInvocator.withoutMasks(requestType);
            boolean hasAuth = StreamBasedInvocator.hasAuth(requestType);
            boolean hasEncryption = StreamBasedInvocator.hasEncryption(requestType);
            if (reqTypeWithoutMasks == StreamBasedInvocator.INIT_REQUEST)
            {
                this.serviceInitRequest(hasAuth, hasEncryption);
                return;
            }
            if (hasAuth)
            {
                if (!verifyAuth(true, new DataInputStream(this.inputStream), hasEncryption))
                {
                    return;
                }
            }
            boolean compressed = StreamBasedInvocator.hasCompression(requestType) ||
                requestType == StreamBasedInvocator.THANK_YOU_REQUEST;
            FixedInflaterInputStream zipped = null;
            InputStream is = this.inputStream;
            if (hasEncryption)
            {
                this.cis.reset(is);
                is = this.cis;
            }
            if (compressed)
            {
                zipped = new FixedInflaterInputStream(is);
                is = zipped;
            }
            try
            {
                ObjectInput in;
                switch (reqTypeWithoutMasks)
                {
                    case StreamBasedInvocator.INVOKE_REQUEST:
                        if (config.requiresAuth() && !authorized)
                        {
                            this.outputStream.write(StreamBasedInvocator.AUTH_FAILED);
                            return;
                        }
                        if (binaryLoggingEnabled)
                        {
                            CopyOnReadInputStream copyOnReadInputStream = new CopyOnReadInputStream(is);
                            in = new ObjectInputStream(copyOnReadInputStream);
                            this.serviceInvokeRequest(in, copyOnReadInputStream, compressed);
                        }
                        else
                        {
                            in = new ObjectInputStream(is);
                            this.serviceInvokeRequest(in, null, compressed);
                        }
                        break;
                    case StreamBasedInvocator.RESEND_REQUEST:
                        if (config.requiresAuth() && !authorized)
                        {
                            this.outputStream.write(StreamBasedInvocator.AUTH_FAILED);
                            return;
                        }
                        in = new ObjectInputStream(is);
                        this.serviceResendRequest(in);
                        break;
                    case StreamBasedInvocator.THANK_YOU_REQUEST:
                        in = new ObjectInputStream(is);
                        this.serviceThankYou(in);
                        break;
                }
            }
            finally
            {
                if (zipped != null)
                {
                    zipped.finish(); // frees up memory allocated in native zlib library.
                }
            }

        }

        private void serviceInitRequest(boolean auth, boolean encrypt) throws IOException
        {
            DataInputStream dis = new DataInputStream(this.inputStream);
            String url = dis.readUTF();
            synchronized (registeredUrls)
            {
                if (!registeredUrls.contains(url))
                {
                    for(ServiceDefinition serviceDefinition: serviceMap.values())
                    {
                        JrpipServiceRegistry.getInstance().addServiceForUrl(url,
                                serviceDefinition.getServiceInterface(), serviceDefinition.getService());
                    }
                    registeredUrls.add(url);
                }
            }
            if (!verifyAuth(auth, dis, encrypt)) return;
            this.outputStream.write(StreamBasedInvocator.INIT_REQUEST);
            int id = CLIENT_ID.incrementAndGet();
            long vmAndClientId = vmId | (long) id;
            this.outputStream.writeLong(vmAndClientId);
            this.outputStream.writeInt(config.getIdleSocketCloseTime());
        }

        private boolean verifyAuth(boolean auth, DataInputStream dis, boolean encrypt) throws IOException
        {
            boolean verified = false;
            String username = null;
            AuthGenerator generator = null;
            long challenge = 0;
            if (auth)
            {
                if (this.authorized)
                {
                    throw new RuntimeException("Should never auth twice!");
                }
                username = dis.readUTF();
                challenge = dis.readLong();
                int encoded = dis.readInt();
                byte[] token = config.getTokenForUser(username);
                if (token != null)
                {
                    try
                    {
                        generator = new AuthGenerator(token);
                        verified = generator.verifyChallenge(challenge, encoded);
                        if (verified)
                        {
                            verified = userNonces.computeIfAbsent(username, (x) -> new UserNonces()).addIfNotPresent(challenge);
                        }
                    }
                    catch (GeneralSecurityException e)
                    {
                        throw new RuntimeException("Should never get here", e);
                    }
                }
            }
            else if (config.requiresAuth())
            {
                this.outputStream.write(StreamBasedInvocator.AUTH_FAILED);
                return false;
            }
            if (auth)
            {
                if (verified)
                {
                    this.authorized = true;
                    this.username = username;
                    this.authGenerator = generator;
                    if (encrypt)
                    {
                        try
                        {
                            byte[] keyIv = this.authGenerator.generateKeyIv(challenge);
                            SecretKey key = new SecretKeySpec(keyIv, 0, 16, "AES");
                            IvParameterSpec iv = new IvParameterSpec(keyIv, 16, 16);
                            Cipher enc = Cipher.getInstance("AES/CBC/NoPadding");
                            enc.init(Cipher.ENCRYPT_MODE, key, iv);

                            Cipher dec = Cipher.getInstance("AES/CBC/NoPadding");
                            dec.init(Cipher.DECRYPT_MODE, key, iv);

                            this.cos = new CipherOutputStream128(null, enc);
                            this.cis = new CipherInputStream128(null, dec);
                        }
                        catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException e)
                        {
                            throw new RuntimeException("Shouldn't get here", e);
                        }

                    }
                }
                else
                {
                    this.outputStream.write(StreamBasedInvocator.AUTH_FAILED);
                    return false;
                }
            }
            return true;
        }

        private void serviceThankYou(ObjectInput in) throws IOException, ClassNotFoundException
        {
            thankYous.incrementAndGet();
            //if (CAUSE_RANDOM_ERROR) if (Math.random() > ERROR_RATE) throw new IOException("Random error, for testing only!");
            int thankYouNotes = in.readInt();
            for (int i = 0; i < thankYouNotes; i++)
            {
                //if (CAUSE_RANDOM_ERROR) if (Math.random() > ERROR_RATE) throw new IOException("Random error, for testing only!");
                ContextCache.getInstance().removeContext((RequestId) in.readObject());
            }
            outputStream.write(StreamBasedInvocator.THANK_YOU_REQUEST);
        }

        private void serviceResendRequest(ObjectInput in) throws IOException, ClassNotFoundException
        {
            resendRequests.incrementAndGet();
            //if (CAUSE_RANDOM_ERROR) if (Math.random() > ERROR_RATE) throw new IOException("Random error, for testing only!");
            RequestId resendRequestId = (RequestId) in.readObject();
            Context resendContext = ContextCache.getInstance().getContext(resendRequestId);
            if (resendContext == null || resendContext.isCreatedState() || resendContext.isReadingParameters())
            {
                outputStream.write(StreamBasedInvocator.REQUEST_NEVER_ARRVIED_STATUS);
            }
            else
            {
                resendContext.waitForInvocationToFinish();
                resendContext.writeAndLogResponse(this.outputStream, resendRequestId, this.cos);
            }
        }

        private void serviceInvokeRequest(
                ObjectInput in,
                CopyOnReadInputStream copyOnReadInputStream, boolean compressed) throws IOException, ClassNotFoundException
        {
            methodInvocations.incrementAndGet();
            //if (CAUSE_RANDOM_ERROR) if (Math.random() > ERROR_RATE) throw new IOException("Random error, for testing only!");
            RequestId requestId = (RequestId) in.readObject();
            Context invokeContext = ContextCache.getInstance().getOrCreateContext(requestId);
            invokeContext.setCompressed(compressed);
            String serviceInterface = (String) in.readObject();
            ServiceDefinition serviceDefinition = serviceMap.get(serviceInterface);
            if (serviceDefinition == null)
            {
                invokeContext.setReturnValue(new JrpipRuntimeException("Jrpip is not servicing "
                        + serviceInterface), true);
            }
            else
            {
                OutputStreamBuilder outputStreamBuilder = serviceDefinition.getOutputStreamBuilder();
                DataOutputStream copyTo = outputStreamBuilder.newOutputStream();
                copyTo.writeByte(OutputStreamBuilder.REQUEST_HEADER);
                try
                {
                    if (copyOnReadInputStream != null)
                    {
                        copyOnReadInputStream.startCopyingInto(copyTo);
                    }
                    invokeContext.setOutputStreamBuilder(outputStreamBuilder);
                    boolean serviceRequest = true;
                    if (serviceDefinition.isVmBound())
                    {
                        serviceRequest = checkVmBoundCall(requestId, invokeContext, serviceInterface);
                    }
                    if (serviceRequest)
                    {
                        String remoteAddress = socket.getRemoteSocketAddress().toString();
                        JrpipRequestContext requestContext = getJrpipRequestContext(remoteAddress, requestId, this.username);

                        new StreamBasedInvocator().invoke(in,
                                invokeContext,
                                serviceDefinition.getService(),
                                serviceDefinition.getMethodResolver(),
                                remoteAddress,
                                requestId,
                                listeners,
                                copyTo,
                                config.getMethodInterceptor(),
                                requestContext);
                    }
                }
                finally
                {
                    copyTo.close();
                }
            }
            invokeContext.writeAndLogResponse(outputStream, requestId, this.cos);
        }
    }

    private static void quietlyClose(Closeable closeable)
    {
        try
        {
            if (closeable != null)
            {
                closeable.close();
            }
        }
        catch (IOException e)
        {
            LOGGER.debug("Could not close "+closeable.getClass().getName(), e);
        }
    }

    public int getThankYous()
    {
        return this.thankYous.get();
    }


    private JrpipRequestContext getJrpipRequestContext(String remoteAddr, RequestId requestId, String username)
    {
        JrpipRequestContext requestContext = null;
        if (this.config.getMethodInterceptor() != null)
        {
            requestContext = new JrpipRequestContext(
                    requestId,
                    username,
                    remoteAddr,
                    null);
        }
        return requestContext;
    }

    private boolean checkVmBoundCall(
            RequestId requestId,
            Context invokeContext,
            String serviceInterface)
    {
        long requestVmId = requestId.getProxyId() & ~0xffffffffL;
        if (requestVmId != vmId)
        {
            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("request vm id: {} server vm id: {}", requestVmId, vmId);
            }
            invokeContext.setReturnValue(new JrpipVmBoundException("The service instance for "
                    + serviceInterface
                    + " has been recycled. You must restart your client."), true);
            return false;
        }
        return true;
    }

    public void logStats()
    {
        long seconds = (System.currentTimeMillis() - this.startTime) / 1000L;

        LOGGER.info("Invocations: "+methodInvocations.get()+" Resends: "+resendRequests.get()+
                " ThankYous: "+thankYous.get()+" pings: "+pings.get()+" uptime: "+seconds
                + " sec (about " + seconds / 3600L + " hours " + seconds / 60L % 60L + " minutes)");
    }

    private static class UserNonces
    {
        private long[] used = new long[32];
        private int pos = 0;

        public synchronized boolean addIfNotPresent(long v)
        {
            for(long x: used)
            {
                if (x == v)
                {
                    return false;
                }
            }
            used[pos++] = v;
            if (pos == used.length)
            {
                pos = 0;
            }
            return true;
        }

    }
}
