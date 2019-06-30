# Change Log
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

