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
* JrPip is safer than RMI because it never creates classes from the binary payload.
* JrPip does not in any way interfere with garbage collection.
* Binary logging.
* Ability to route to a local implementation (when configured).
* Server side method interceptor.

## Usage
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
        ServletHandler servletHandler = new ServletHandler();
        context.addHandler(servletHandler);

        ServletHolder holder = servletHandler.addServlet("JrpipServlet", "/JrpipServlet", "com.gs.jrpip.server.JrpipServlet");
        holder.put("serviceInterface.Echo", "com.gs.jrpip.Echo"); // this is the interface
        holder.put("serviceClass.Echo", "com.gs.jrpip.EchoImpl"); // this is the implementation
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

### Binary Logging:
The following System properties can be used to configure binary logging.

* `jrpip.enableBinaryLogs`: boolean. Default: `false`. Enables binary logging.
* `jrpip.binaryLogsDirectory`: string. Default: `jrpipBinaryLogs`. Directory where binary logs are stored.

The produced log file can be inspected with the utility ListJrpipRequest. Run that command to see the options.

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
