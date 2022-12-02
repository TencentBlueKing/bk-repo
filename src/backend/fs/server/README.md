## 制品库文件系统服务器
使用Spring WebFlux和Kotlin coroutines实现的Reactive的服务器。NIO服务器我们选择的是Netty，因为其使用广泛。

## roadmap

文件系统相关

- [x] 文件下载
- [x] 文件范围下载
- [ ] 分片读
- [ ] 分片写

web相关

- [x] 账号/权限认证
- [x] 全局异常处理
- [x] 微服务间认证
- [x] Reactive Feign
- [x] Spring Actuator支持
- [x] Server Metrics
- [x] Zero Copy
- [ ] 消息国际化
- [ ] Prometheus支持