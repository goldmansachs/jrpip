package com.gs.jrpip.client;

import com.gs.jrpip.FixedDeflaterOutputStream;
import com.gs.jrpip.FixedInflaterInputStream;
import com.gs.jrpip.RequestId;
import com.gs.jrpip.server.StreamBasedInvocator;
import com.gs.jrpip.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.SocketException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class SocketMessageTransport implements MessageTransport
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SocketMessageTransport.class.getName());
    private static ConcurrentHashMap<String, Integer> serverInitialized = new ConcurrentHashMap<>();
    private static final SocketPool SOCKET_POOL = new SocketPool();
    private static final int IDLE_CLOSER_PERIOD = 1000;

    private final String username;
    private final byte[] token;
    private final boolean encrypt;

    public SocketMessageTransport()
    {
        this.username = null;
        this.token = null;
        this.encrypt = false;
    }

    public SocketMessageTransport(String username, String base32EncodedToken)
    {
        this(username, base32EncodedToken, false);
    }

    public SocketMessageTransport(String username, String base32EncodedToken, boolean encrypt)
    {
        this.username = username;
        try
        {
            this.token = AuthGenerator.decode(base32EncodedToken);
        }
        catch (ParseException e)
        {
            throw new JrpipRuntimeException("Could not decode base32 encoded token", e);
        }
        this.encrypt = encrypt;
    }

    @Override
    public <T> InvocationHandler createInvocationHandler(Class<T> api, String url, int timeoutMillis, boolean disconnectedMode)
            throws MalformedURLException
    {
        long proxyId = generateRandomProxyId();
        if (disconnectedMode && this.username != null)
        {
            throw new JrpipRuntimeException("Authentication and disconnected mode can't be combined!");
        }
        if (!disconnectedMode)
        {
            JrpipClientSocket socket = null;
            try
            {
                socket = borrowSocket(url);
                proxyId = socket.initRequest(timeoutMillis);
            }
            catch(MalformedURLException mue)
            {
                throw mue;
            }
            catch (Throwable t)
            {
                forceCloseSocket(socket);
                socket = null;
                //ignore
            }
            finally
            {
                SOCKET_POOL.putBackIntoPool(socket);
            }
        }
        SocketMessageTransportData data = new SocketMessageTransportData(url, proxyId, this.username, this.token, this.encrypt);
        return new MtProxyInvocationHandler(data, this, api, timeoutMillis);
    }

    private void rethrow(Throwable t) throws IOException
    {
        if (t instanceof IOException)
        {
            throw (IOException) t;
        }
        if (t instanceof RuntimeException)
        {
            throw (RuntimeException) t;
        }
        throw new JrpipRuntimeException("Unexpected exception", t);
    }

    private void rethrowCnf(Throwable t) throws IOException, ClassNotFoundException
    {
        if (t instanceof IOException)
        {
            throw (IOException) t;
        }
        if (t instanceof RuntimeException)
        {
            throw (RuntimeException) t;
        }
        if (t instanceof ClassNotFoundException)
        {
            throw (ClassNotFoundException) t;
        }
        throw new JrpipRuntimeException("Unexpected exception", t);
    }

    private void forceCloseSocket(JrpipClientSocket socket)
    {
        if (socket != null)
        {
            socket.forceClose();
        }
    }

    @Override
    public boolean fastFailPing(String url, int timeoutMillis) throws IOException
    {
        JrpipClientSocket socket = null;
        try
        {
            socket = borrowSocket(url);
            return socket.fastFailPing(timeoutMillis) == 200;
        }
        catch(Throwable t)
        {
            forceCloseSocket(socket);
            socket = null;
            rethrow(t);
        }
        finally
        {
            SOCKET_POOL.putBackIntoPool(socket);
        }
        return false; // can never get here!
    }

    private JrpipClientSocket borrowSocket(String url) throws IOException
    {
        SocketMessageTransportData data = new SocketMessageTransportData(url, -1, this.username, this.token, this.encrypt);
        return borrowSocket(data);
    }

    private JrpipClientSocket borrowSocket(SocketMessageTransportData data) throws IOException
    {
        return SOCKET_POOL.borrow(data);
    }

    @Override
    public void waitForServer(long deadline, MessageTransportData d)
    {
        SocketMessageTransportData data = (SocketMessageTransportData) d;
        boolean noValidResponse = true;
        while (System.currentTimeMillis() < deadline && noValidResponse)
        {
            boolean wait = true;
            JrpipClientSocket socket = null;
            try
            {
                socket = borrowSocket(data);
                int timeout = (int) Math.min(MessageTransport.PING_TIMEOUT, deadline - System.currentTimeMillis());
                if (timeout == 0)
                {
                    timeout = 1;
                }
                int code = socket.fastFailPing(timeout);

                if (code == 200)
                {
                    noValidResponse = false;
                }
                else
                {
                    LOGGER.warn("Ping request to {} resulted in {}", data.getUrl(), code);
                }
            }
            catch (SocketException se)
            {
                forceCloseSocket(socket);
                socket = null;
                if (se.getMessage().contains("reset") || se.getMessage().contains("peer") || se.getMessage().contains("abort"))
                {
                    wait = false;
                }
            }
            catch (Throwable t)
            {
                LOGGER.warn("could not ping server at {}", data.getUrl(), t);
                forceCloseSocket(socket);
                socket = null;
            }
            finally
            {
                SOCKET_POOL.putBackIntoPool(socket);
            }
            try
            {
                long now = System.currentTimeMillis();
                if (noValidResponse && now < deadline && wait)
                {
                    int timeout = (int) Math.min(MessageTransport.PING_INTERVAL, deadline - now);
                    Thread.sleep(timeout);
                }
            }
            catch (InterruptedException e)
            {
                // ok, just ignore it
            }
        }
        if (noValidResponse)
        {
            throw new JrpipRuntimeException("Could not reach server at " + data.getUrl());
        }

    }

    @Override
    public ResponseMessage sendParameters(MessageTransportData d, RequestId requestId, int timeout,
            String serviceClass, String mangledMethodName, Object[] args, Method method, boolean compress)
            throws ClassNotFoundException, IOException
    {
        SocketMessageTransportData data = (SocketMessageTransportData) d;
        JrpipClientSocket socket = null;
        try
        {
            socket = borrowSocket(data);
            return socket.sendParameters(requestId, timeout, serviceClass, mangledMethodName, args, compress);
        }
        catch (Throwable t)
        {
            forceCloseSocket(socket);
            socket = null;
            rethrowCnf(t);
        }
        finally
        {
            SOCKET_POOL.putBackIntoPool(socket);
        }
        return null; //will never get here!
    }

    @Override
    public ResponseMessage requestResend(MessageTransportData d, RequestId requestId, int timeout,
            Object[] args, Method method, boolean compress)
            throws ClassNotFoundException, IOException
    {
        SocketMessageTransportData data = (SocketMessageTransportData) d;
        JrpipClientSocket socket = null;
        try
        {
            socket = borrowSocket(data);
            return socket.requestResend(requestId, timeout, compress);
        }
        catch (Throwable t)
        {
            forceCloseSocket(socket);
            socket = null;
            rethrowCnf(t);
        }
        finally
        {
            SOCKET_POOL.putBackIntoPool(socket);
        }
        return null; // will never get here!
    }

    @Override
    public boolean sendThanks(Object key, List<ThankYouWriter.ThankYouRequest> requestList) throws IOException
    {
        SocketMessageTransportData data = (SocketMessageTransportData) key;
        JrpipClientSocket socket = null;
        try
        {
            socket = borrowSocket(data);
            return socket.sendThanks(requestList);
        }
        catch (Throwable t)
        {
            forceCloseSocket(socket);
            socket = null;
            rethrow(t);
        }
        finally
        {
            SOCKET_POOL.putBackIntoPool(socket);
        }
        return false;
    }

    @Override
    public void initAndRegisterLocalServices(String url, boolean disconnectedMode, int timeout) throws MalformedURLException
    {
        if (!serverInitialized.containsKey(url))
        {
            JrpipClientSocket socket = null;
            try
            {
                socket = borrowSocket(url);
                socket.initRequest(timeout);
                serverInitialized.put(url, socket.serverShutdownTime);
            }
            catch(MalformedURLException mue)
            {
                throw mue;
            }
            catch (Throwable t)
            {
                forceCloseSocket(socket);
                socket = null;
                //ignore
            }
            finally
            {
                SOCKET_POOL.putBackIntoPool(socket);
            }
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

    private static long generateRandomProxyId()
    {
        return (long) (Math.random() * 2000000000000L);
    }

    private static class JrpipClientSocket
    {
        private Socket socket;
        private SocketMessageTransportData data;
        private AuthGenerator authGenerator;
        private volatile long lastUsed;
        private BlockOutputStream out;
        private BlockInputStream in;
        private boolean initialized = false;
        private boolean authenticated = false;
        private long proxyId = -1;
        private int serverShutdownTime;
        private CipherOutputStream128 cos;
        private CipherInputStream128 cis;

        public JrpipClientSocket(SocketMessageTransportData data, Integer serverShutdownTime) throws IOException
        {
            this.data = data;
            this.socket = new Socket(data.getHost(), data.getPort());
            this.socket.setSoTimeout(0);
            this.socket.setKeepAlive(true);
            this.socket.setTcpNoDelay(true);
            this.socket.setSoLinger(true, 2);
            in = new BlockInputStream(this.socket.getInputStream());
            out = new BlockOutputStream(this.socket.getOutputStream());
            lastUsed = System.currentTimeMillis();
            this.serverShutdownTime = serverShutdownTime == null ? 0 : serverShutdownTime;
            this.authGenerator = this.data.createAuthGenerator();
        }

        public SocketMessageTransportData getData()
        {
            return data;
        }

        public boolean isClosed()
        {
            closeIfTimedOut();
            return this.socket == null;
        }

        public void closeIfTimedOut()
        {
            if (socket == null)
            {
                return;
            }
            if (System.currentTimeMillis() > lastUsed + this.serverShutdownTime - 250)
            {
                quietlyClose(socket);
                socket = null;
            }
        }

        public int fastFailPing(int timeout) throws IOException
        {
            this.socket.setSoTimeout(timeout);
            this.out.beginConversation();
            this.out.write(StreamBasedInvocator.PING_REQUEST);
            this.out.endConversation();
            this.in.beginConversation();
            byte ping = this.in.readByte();
            this.in.endConversation();
            if (ping != StreamBasedInvocator.PING_REQUEST)
            {
                return 400;
            }
            this.lastUsed = System.currentTimeMillis();
            return 200;
        }

        public long initRequest(int timeout) throws IOException
        {
            if (this.initialized)
            {
                return this.proxyId;
            }
            this.socket.setSoTimeout(timeout);
            this.out.beginConversation();
            byte type = StreamBasedInvocator.INIT_REQUEST;
            if (this.data.requiresAuth())
            {
                type = StreamBasedInvocator.withAuth(type);
            }
            if (this.data.requiresEncryption())
            {
                type = StreamBasedInvocator.withEncryption(type);
            }
            this.out.write(type);
            DataOutputStream dos = new DataOutputStream(this.out);
            dos.writeUTF(this.data.getUrl());
            if (this.data.requiresAuth())
            {
                writeAuthHeader(dos);
            }
            dos.flush();
            out.endConversation();
            in.beginConversation();
            DataInputStream dis = new DataInputStream(this.in);
            byte status = dis.readByte();
            if (this.data.requiresAuth())
            {
                if (status != StreamBasedInvocator.INIT_REQUEST)
                {
                    in.endConversation();
                    throw new JrpipRuntimeException("Authorization failed!");
                }
            }
            if (status == StreamBasedInvocator.AUTH_FAILED)
            {
                throw new JrpipRuntimeException("Authorization failed!");
            }

            long proxyId = dis.readLong();
            this.serverShutdownTime = dis.readInt();
            in.endConversation();
            this.initialized = true;
            if (this.data.requiresAuth())
            {
                this.authenticated = true;
            }
            this.proxyId = proxyId;
            this.lastUsed = System.currentTimeMillis();
            return proxyId;
        }

        private void writeAuthHeader(DataOutputStream dos) throws IOException
        {
            dos.writeUTF(this.data.getUsername());
            long challenge = AuthGenerator.createChallenge();
            dos.writeLong(challenge);
            dos.writeInt(this.authGenerator.authCode(challenge));
            if (this.data.requiresEncryption())
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

        public ResponseMessage sendParameters(RequestId requestId, int timeout, String serviceClass, String mangledMethodName,
                Object[] args, boolean compress) throws IOException, ClassNotFoundException
        {
            this.socket.setSoTimeout(timeout);
            boolean needAuth = this.data.requiresAuth() && !this.authenticated;
            byte type = StreamBasedInvocator.INVOKE_REQUEST;
            type = compress ? StreamBasedInvocator.withCompression(type) : type;
            if (this.data.requiresEncryption())
            {
                type = StreamBasedInvocator.withEncryption(type);
            }
            if (needAuth)
            {
                type = StreamBasedInvocator.withAuth(type);
            }
            this.out.beginConversation();
            this.out.write(type);
            if (needAuth)
            {
                writeAuthHeader(new DataOutputStream(this.out));
            }
            OutputStream out = this.out;
            FixedDeflaterOutputStream zipped = null;
            CipherOutputStream128 cos = null;
            try
            {
                if (this.data.requiresEncryption())
                {
                    cos = this.cos;
                    cos.reset(out);
                    out = cos;
                }
                if (compress)
                {
                    zipped = new FixedDeflaterOutputStream(out);
                    out = zipped;
                }
                ObjectOutput objectOut = new ObjectOutputStream(out);
                objectOut.writeObject(requestId);
                objectOut.writeObject(serviceClass);
                objectOut.writeObject(mangledMethodName);
                if (args != null)
                {
                    for (Object o : args)
                    {
                        objectOut.writeObject(o);
                    }
                }
                objectOut.flush();
            }
            finally
            {
                if (zipped != null)
                {
                    zipped.finish();
                }
                if (cos != null)
                {
                    cos.finish();
                }
            }
            this.out.endConversation();
            this.in.beginConversation();
            byte status = this.in.readByte();
            Object returned = null;
            if (status == StreamBasedInvocator.AUTH_FAILED)
            {
                return ResponseMessage.forTransportErrorCode(401, "Bad or missing credentials");
            }
            else if (status != StreamBasedInvocator.REQUEST_NEVER_ARRVIED_STATUS)
            {
                returned = this.getResult(this.in, compress);
            }
            if (needAuth)
            {
                this.authenticated = true;
            }
            in.endConversation();
            this.lastUsed = System.currentTimeMillis();
            return ResponseMessage.forSuccess(status, returned);
        }

        private Object getResult(InputStream in, boolean compress)
                throws IOException, ClassNotFoundException
        {
            if (this.data.requiresEncryption())
            {
                this.cis.reset(in);
                in = this.cis;
            }

            FixedInflaterInputStream zipped = null;
            try
            {
                if (compress)
                {
                    zipped = new FixedInflaterInputStream(in);
                    in = zipped;
                }
                ObjectInput objectInput = new ObjectInputStream(in);
                return objectInput.readObject();
            }
            finally
            {
                if (zipped != null)
                {
                    zipped.finish();
                }
            }
        }

        public ResponseMessage requestResend(RequestId requestId, int timeout, boolean compress)
                throws IOException, ClassNotFoundException
        {
            this.socket.setSoTimeout(timeout);
            boolean needAuth = this.data.requiresAuth() && !this.initialized;
            byte type = StreamBasedInvocator.RESEND_REQUEST;
            if (needAuth)
            {
                type = StreamBasedInvocator.withAuth(type);
            }
            this.out.beginConversation();
            this.out.write(type);
            if (needAuth)
            {
                writeAuthHeader(new DataOutputStream(this.out));
            }
            ObjectOutput objectOut = new ObjectOutputStream(out);
            objectOut.writeObject(requestId);
            objectOut.flush();
            this.out.endConversation();
            this.in.beginConversation();
            byte status = this.in.readByte();
            Object returned = null;
            if (status == StreamBasedInvocator.AUTH_FAILED)
            {
                return ResponseMessage.forTransportErrorCode(401, "Bad or missing credentials");
            }
            else if (status != StreamBasedInvocator.REQUEST_NEVER_ARRVIED_STATUS)
            {
                returned = this.getResult(this.in, compress);
            }
            this.in.endConversation();
            this.lastUsed = System.currentTimeMillis();
            return ResponseMessage.forSuccess(status, returned);
        }

        public boolean sendThanks(List<ThankYouWriter.ThankYouRequest> requestList)
                throws IOException
        {
            this.socket.setSoTimeout(0);
            this.out.beginConversation();
            this.out.write(StreamBasedInvocator.THANK_YOU_REQUEST);
            OutputStream out = this.out;
            FixedDeflaterOutputStream zipped = null;
            try
            {
                zipped = new FixedDeflaterOutputStream(out);
                out = zipped;
                ObjectOutput objectOut = new ObjectOutputStream(out);
                objectOut.writeInt(requestList.size());
                for (ThankYouWriter.ThankYouRequest request : requestList)
                {
                    //if (CAUSE_RANDOM_ERROR) if (Math.random() > ERROR_RATE) throw new IOException("Random error, for testing only!");
                    objectOut.writeObject(request.getRequestId());
                }
                objectOut.flush();
            }
            finally
            {
                if (zipped != null)
                {
                    zipped.finish();
                }
            }
            this.out.endConversation();
            this.in.beginConversation();
            byte status = this.in.readByte();
            if (status != StreamBasedInvocator.THANK_YOU_REQUEST)
            {
                throw new IOException("Incorrect return type "+status);
            }
            this.in.endConversation();
            this.lastUsed = System.currentTimeMillis();
            return true;
        }

        public void forceClose()
        {
            if (this.socket != null)
            {
                quietlyClose(socket);
                this.socket = null;
            }
        }
    }

    private static class SocketPool
    {
        private ConcurrentHashMap<SocketMessageTransportData, List<JrpipClientSocket>> poolByUrl = new ConcurrentHashMap<>();

        public SocketPool()
        {
            IdleConnectionCloser idleConnectionCloser = new IdleConnectionCloser(this);
            idleConnectionCloser.start();
        }

        public JrpipClientSocket borrow(SocketMessageTransportData data) throws IOException
        {
            List<JrpipClientSocket> sockets = poolByUrl.computeIfAbsent(data, (x) -> new ArrayList<>());
            JrpipClientSocket result = null;
            while(result == null)
            {
                synchronized (sockets)
                {
                    if (!sockets.isEmpty())
                    {
                        result = sockets.remove(sockets.size() - 1);
                    }
                }
                if (result == null)
                {
                    result = new JrpipClientSocket(data, serverInitialized.get(data.getUrl()));
                }
                else
                {
                    if (result.isClosed())
                    {
                        result = null;
                    }
                }
            }
            return result;
        }

        public void putBackIntoPool(JrpipClientSocket socket)
        {
            if (socket == null || socket.isClosed())
            {
                return;
            }
            List<JrpipClientSocket> sockets = poolByUrl.get(socket.getData());
            synchronized (sockets)
            {
                sockets.add(socket);
            }
        }

        public void closeOldSockets()
        {
            ArrayList<SocketMessageTransportData> keys = new ArrayList<>(poolByUrl.keySet());
            for(SocketMessageTransportData key: keys)
            {
                List<JrpipClientSocket> sockets = poolByUrl.get(key);
                synchronized (sockets)
                {
                    for(int i=sockets.size() -1;i>=0;i--)
                    {
                        JrpipClientSocket socket = sockets.get(i);
                        socket.closeIfTimedOut();
                        if (socket.isClosed() && i == sockets.size() - 1)
                        {
                            sockets.remove(i);
                        }
                    }
                }
            }
        }
    }

    private static class IdleConnectionCloser extends Thread
    {
        private SocketPool pool;

        public IdleConnectionCloser(SocketPool pool)
        {
            super("FstClientSocketCloser");
            this.setDaemon(true);
            this.pool = pool;
        }

        @Override
        public void run()
        {
            while(true)
            {
                try
                {
                    Thread.sleep(IDLE_CLOSER_PERIOD);
                }
                catch (InterruptedException e)
                {
                    //ignore
                }
                this.pool.closeOldSockets();
            }
        }
    }

    /**
     * Useful for tests. Do not call in production.
     */
    public static void clearServerStatus()
    {
        serverInitialized.clear();
    }
}
