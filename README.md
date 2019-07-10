# JrPip - Java Remote Proxy Invocation Provider

JrPip provides remote method invocation using the Java binary serialization protocol. 
The payload is streamed as the objects are being serialized, which significantly improves
speed and reduces memory consumption for large payloads. It can easily handle multi-GB 
payloads without multi-GB memory buffers.

## Features
* Streamed serialized payloads. Constant memory usage for all payloads.
* Execute once semantics: temporary network issues are handled transparently without re-executing the remote code.
* No RemoteException. A runtime exception is thrown if the destination becomes completely unreachable during a call.
* Implemented over HTTP in any servlet container.
	* Can be encrypted using HTTPS.
	* Can use HTTP authentication (cookie or header).
	* Proxy/loadbalancing supportable.
* Has a built in socket transport in addition to HTTP
* JrPip is safer than RMI because it never creates classes from the binary payload.
* JrPip does not in any way interfere with garbage collection.
* Binary logging.
* Ability to route to a local implementation (when configured).
* Server side method interceptor.

### Timeout specification
The default timeout is zero (infinite), which is appropriate for heavy workloads
within closely related endpoints (e.g. same application).

The timeout can be specified in 4 ways, with the following precedence:
- Highest precedence: `System.properties`. A timeout can be specified with the full class
name and method signature. Example:
```java
    System.setProperty("jrpip.timeout.com.gs.jrpip.Echo.echoAndSleep_java.lang.String_long", "1000");
```
- `@Timeout` annotation on the method in the interface. Example:
```java
    @Timeout(timeoutMillis = 10000)
    public int someMethod(String val);
```
- `@Timeout` annotation on the interface class. Example:
```java
@Timeout(timeoutMillis = 5000)
public interface ExampleService
```
- Timeout value used in the call to `factory.create`

### Binary Logging:
The following System properties can be used to configure binary logging.

* `jrpip.enableBinaryLogs`: boolean. Default: `false`. Enables binary logging.
* `jrpip.binaryLogsDirectory`: string. Default: `jrpipBinaryLogs`. Directory where binary logs are stored.

The produced log file can be inspected with the utility ListJrpipRequest. Run that command to see the options.

## Usage with a servlet container
1. Create an interface for the service.
2. Create an implementation of that interface. All objects in the implementation method signatures must be serializable and present
on the classpaths of both the client and server.
3. Deploy JrpipServlet in a servlet container with a configuration that binds the interface to the implementation.
4. On the client, get an instance of the interface from the FastServletProxyFactory and call methods on it.

### Example configuration:
One or more interface/implementation pairs can be configured for a given servlet.

#### Typical xml based configuration:
```xml
    <servlet>
        <servlet-name>JrpipServlet</servlet-name>
        <servlet-class>com.gs.jrpip.server.JrpipServlet</servlet-class>
        <init-param>
            <param-name>serviceInterface.Example</param-name>
            <param-value>com.example.ExampleService</param-value> <!-- this is the interface -->
        </init-param>
        <init-param>
            <param-name>serviceClass.Example</param-name>
            <param-value>com.example.ExampleServiceImpl</param-value> <!-- this is the implementation -->
        </init-param>
        <init-param>
            <param-name>serviceInterface.FooService</param-name>
            <param-value>com.example.foo.FooService</param-value>
        </init-param>
        <init-param>
            <param-name>serviceClass.FooService</param-name>
            <param-value>com.example.foo.FooServiceImpl</param-value>
        </init-param>
    </servlet>
```

#### Typical Jetty configuration:
```java
        Server server = new Server(9001);

        ServletHandler handler = new ServletHandler();
        server.setHandler(handler);

        ServletHolder holder = handler.addServletWithMapping(JrpipServlet.class, "/JrpipServlet");
        holder.setInitParameter("serviceInterface.Echo", "com.gs.jrpip.Echo"); // this is the interface
        holder.setInitParameter("serviceClass.Echo", "com.gs.jrpip.EchoImpl"); // this is the implementation

        server.start();
```

And older versions of Jetty:
```java
        HttpServer server = new HttpServer();

        HttpContext context = new HttpContext();
        context.setContextPath("/");

        ServletHandler servletHandler = new ServletHandler();
        context.addHandler(servletHandler);

        ServletHolder holder = servletHandler.addServlet("JrpipServlet", "/JrpipServlet", "com.gs.jrpip.server.JrpipServlet");
        holder.put("serviceInterface.Echo", "com.gs.jrpip.Echo"); // this is the interface
        holder.put("serviceClass.Echo", "com.gs.jrpip.EchoImpl"); // this is the implementation

        server.addContext(context);
        server.start();
```

### Client side usage:
```java
        FastServletProxyFactory fspf = new FastServletProxyFactory();
        Echo echo = fspf.create(Echo.class, this.getJrpipUrl()); // HTTP url, e.g. http://example.com:8080/JrpipServlet
        echo.someMethod();
```
### Using other features:
* Overloaded `create` methods on `FastServletProxyFactory` can be used to specify timeouts. By default, the remote
method can run as long as it needs to.
* For HTTP basic AUTH, use `new FastServletProxyFactory(USER, PASSWORD)`.
* For HTTP cookie handling, use `new FastServletProxyFactory(tokenArray, path, domain)`.
* To register a local implementation, call `JrpipServiceRegistry.getInstance().addServiceForWebApp` or 
`JrpipServiceRegistry.getInstance().addServiceForUrl`. 

### Connection manager configuration:
The following System properties can be used to configure JrPip (from the java command line):

* `fastServletProxyFactory.maxConnectionsPerHost`: integer. Default: 10. Maximum number of HTTP connections per host.
* `fastServletProxyFactory.maxTotalConnections`: integer. Default `10 * maxConnectionsPerHost`. Maxium number of total connections.

The following static methods on `FastServletProxyFactory` can be used for similar configuration:
* `setMaxConnectionsPerHost`
* `setMaxTotalConnections`

For sticky sessions (typically used with cookies and a loadbalancer), use `SessionAwareFastServletProxyFactory`.

### Method interceptor:
To configure a method interceptor, add a parameter to the servlet configuration:
```xml
        <init-param>
            <param-name>methodInterceptor</param-name>
            <param-value>com.example.foo.FooMethodInterceptor</param-value>
        </init-param>
```
or for Jetty:
```java
	holder.put("methodInterceptor", TestMethodInterceptor.class.getName())
```

The method interceptor must implement `com.gs.jrpip.server.MethodInterceptor` and have a no-arg constructor.
See the javadoc in `com.gs.jrpip.server.MethodInterceptor` for call semantics.

### VM Bound configuration:
In some cases, usually when the service implementation is stateful in some way, it is desirable to 
disallow the client from connecting to a new instance of the server. To configure such a service, 
in the servlet configuration, replace "serviceClass" with "vmBoundServiceClass". 

Example:
```xml
    <servlet>
        <servlet-name>JrpipServlet</servlet-name>
        <servlet-class>com.gs.jrpip.server.JrpipServlet</servlet-class>
        <init-param>
            <param-name>serviceInterface.Example</param-name>
            <param-value>com.example.ExampleService</param-value>
        </init-param>
        <init-param>
            <param-name>vmBoundServiceClass.Example</param-name> <!-- this implementation is VM bound -->
            <param-value>com.example.ExampleServiceImpl</param-value>
        </init-param>
    </servlet>
```

## Usage with socket transport
1. Create an interface for the service.
2. Create an implementation of that interface. All objects in the implementation method signatures must be serializable and present
on the classpaths of both the client and server.
3. Deploy a `SocketServer` configured via `SocketServerConfig`.
4. On the client, get an instance of the interface from the `MtProxyFactory` and call methods on it.

### Example configuration:
One or more interface/implementation pairs can be configured for a given server.
See the javadoc for `SocketServerConfig` for more options.

```java
    SocketServerConfig config = new SocketServerConfig(9001);
    config.addServiceConfig(ExampleService.class, ExampleServiceImpl.class);
    config.addServiceConfig(AnotherService.class, AnotherServiceImpl.class);
    SocketServer server = new SocketServer(config);
    server.start();
```

### Client side usage:
```java
    SocketMessageTransport transport = new SocketMessageTransport();
    MtProxyFactory factory = new MtProxyFactory(transport);
    ExampleService example = factory.create(ExampleService.class,
            "jpfs://localhost:9001"); // URL for socket server is always jpfs://<server>:<port>
```

### Authentication
The socket server can be secured with a username/token pair (or pairs). The authentication
sends the username and a hashed challenge (nonce), so the token is never sent over the wire.
A socket is only authenticated once, right after connection.
On the server side:
```java
    config.addCredentials("fred", "lkjhhjas56786349873dliuonkje");
```
and on the client:
```java
    SocketMessageTransport transport = new SocketMessageTransport("fred", "lkjhhjas56786349873dliuonkje");
```

## Encryption
Encryption (AES-128/CBC) can be enabled when constructing the client side transport.
```java
    SocketMessageTransport transport = new SocketMessageTransport("fred", "lkjhhjas56786349873dliuonkje", true);
```
Encryption requires authentication, as it derives a per-session key from
the user token and per-session nonce.

### Method interceptor:
See the javadoc for `SocketServerConfig` and `MethodInterceptor`

### Event Listener
See the javadoc for `SocketServerConfig` and `JrpipEventListener`

### VM Bound configuration:
In some cases, usually when the service implementation is stateful in some way, it is desirable to
disallow the client from connecting to a new instance of the server. To configure such a service,
add a vmbound (boolean) to the service configuration call:

```java
    config.addServiceConfig(ExampleService.class, ExampleServiceImpl.class, true);
```

### Compression
The socket based transport can additionally turn off compression via annotations.
`@Compression(compress = false)` can be specified at the interface class level, or
at the method level, with the method level overriding the class level.
The LZ4 compression used in JrPip is very fast/light and generally there is no
benefit in changing it.
