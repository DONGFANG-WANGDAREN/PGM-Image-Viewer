# Debug Session: https-login-failure
- **Status**: [OPEN]
- **Issue**: 局域网 `http` 可正常访问，但 `https` 无法访问或连接被关闭。
- **Debug Server**: http://192.168.1.68:7777/event
- **Log File**: .dbg/trae-debug-log-https-login-failure.ndjson

## Reproduction Steps
1. 启动应用内 WebSocket / HTTP 服务。
2. 记录界面显示的 HTTP / HTTPS 地址与端口。
3. 使用浏览器访问 HTTP 地址，确认正常。
4. 使用浏览器访问 HTTPS 地址，观察失败现象与浏览器错误。

## Hypotheses & Verification
| ID | Hypothesis | Likelihood | Effort | Evidence |
|----|------------|------------|--------|----------|
| A | HTTPS 服务根本没有成功启动，界面展示了地址但后台 TLS 监听失败 | High | Low | 已在 `WebSocketService.startHttpServer()` 埋点，观察 HTTP/HTTPS `isAlive` 与 `getListeningPort` |
| B | HTTPS 端口已启动，但 AndroidKeyStore 证书/SSLContext 初始化失败，握手阶段被直接关闭 | High | Low | 已在 `LocalHttpsHelper` 埋点，观察 keystore / cert / key manager / SSLContext 各阶段 |
| C | HTTPS 端口实际上是明文 HTTP 或 NanoHTTPD TLS 包装异常，导致浏览器握手后立即断开 | Medium | Medium | 结合 A/B 的成功日志与浏览器现象判断，若启动成功但仍断开则重点看 C |
| D | 界面或扫码页拿到的 HTTPS 端口与真实监听端口不一致 | Medium | Low | 已在 `WebSocketService.startHttpServer()` 埋点，比较 `activeHttpsPort` 与 `httpsListeningPort` |
| E | 浏览器不信任自签证书之外，还有协议版本/密码套件兼容问题 | Low | Medium | 若 A/B/D 都正常而浏览器仍断，则剩余指向 E |

## Log Evidence
- Pending: waiting for `pre-fix` reproduction logs after instrumentation

## Verification Conclusion
Pending
