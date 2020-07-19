# bk-repo 蓝鲸制品库

#### 系统架构图
制品库架构图![制品库架构图](/uploads/0FCF5A4590AD4CDE8BDC78DBA397E4D4/bkrepo.png)

- 应用场景层
- oss(运营支撑)层
- 接入层
- 协议层
- 数据与调度层

#### 设计目标
蓝鲸制品库平台是一个基于微服务架构的平台，采用spring boot+ spring cloud的技术栈，kotlin作为主要的开发语言，微服务网关采用的是openresty

#### 系统目录划分
 - docs 系统文档
 - src 
    - gateway 服务网关代码
    - frontend 前端目录
    - backend  后端目录
    - gradle 构建工具
- support-files 系统初始化与交互代码

#### 微服务模块
- generic
- repository
- docker
- helm
- npm
- pypi
- rpm
- maven
- composer
- opdata
- monitor
- replication


