## 制品库文件系统服务器（BkRepo-Driver）
使用Spring WebFlux和Kotlin coroutines实现的Reactive的服务器。NIO服务器我们选择的是Netty，因为其使用广泛。

## roadmap

文件系统相关

- [x] 文件下载
- [x] 文件范围下载
- [x] 分片读
- [x] 分片写
- [x] 空洞文件
- [x] 并发读写
- [ ] 文件锁
- [ ] 文件系统与制品库系统的互通性

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
- [x] 全局TraceId

## 系统设计

bk-repo-fs是在bk-repo之上构建的一套文件系统，得益于bk-repo强大的存储架构，让我们无需担心具体的存储过程，
可以让我们更快的开始文件系统业务的开发。 但是bk-repo原先的存储模型的设计目标是面向单个文件的，在文件系统中，
如果以文件为粒度的读写，会造成延迟过大， 请求相应慢等问题，所以我们改善了bk-repo原有的存储模型，使其可以面向
块的存储，将写缩小，同时即写即可见。 主要原理是将block数据overlay到原先的节点数据。这样我们可以做到不用改动
原有的存储数据，同时还可以做到文件系统与制品库系统底层数据互通。

面向块存储的文件系统架构
![面向块存储的文件系统的读写示意图](../../../../docs/resource/fs-arch.png)