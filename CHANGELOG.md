# Change Log
## 5.1.3 2022-07-24
- for socket server, add a method to terminate ongoing connections (usually after stop)

## 5.1.2 2019-11-05
- for socket server, fix url to service local map
- for socket server, fix socket initial connection timeout

# Change Log
## 5.1.1 2019-08-09
- Added stateful service configuration to socket transport.

## 5.1.0 2019-07-09
- Added optional AES encryption to socket transport.

## 5.0.0 - 2019-07-05
- Added `@Timeout` annotation to specify timeout on a per class or method level
- Tighter timeout monitoring
- New socket based transport in addition to HTTP
    - The new transport has lower overhead (latency)
    - Only one dependency: slf4j
    - `@Compression` annotation to turn off compression. Not supported in HTTP
- Minimum JDK: 1.8

## 4.0.0 - 2017-05-04
Initial open source release.

