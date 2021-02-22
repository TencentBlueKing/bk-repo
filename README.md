# bk-repo 蓝鲸制品库

## Overview

蓝鲸制品库平台是一个基于微服务架构的平台，采用spring boot+ spring cloud的技术栈，kotlin作为主要的开发语言，微服务网关采用的是openresty

制品库架构图![制品库架构图](/uploads/0FCF5A4590AD4CDE8BDC78DBA397E4D4/bkrepo.png)

- 应用场景层
- oss(运营支撑)层
- 接入层
- 协议层
- 数据与调度层



## Features
- auth 微服务，实现基于rbac的权限体系
- generic 通用制品库微服务
- repository 仓库微服务
- docker docker镜像仓库微服务
- helm helm chart仓库微服务
- npm npm依赖源微服务
- pypi pypi依赖源微服务
- rpm rpm依赖源微服务
- maven maven依赖源微服务
- composer composer依赖源微服务
- opdata 运营统计微服务
- monitor 监控微服务
- replication 备份复制微服务

## Getting started
* [下载与编译](docs/overview/source_compile.md)
* [安装部署](docs/overview/installation.md)
* [API使用说明见这里](docs/apidoc/)
* [使用Helm部署BKREPO到K8S环境](docs/install/README.md)


