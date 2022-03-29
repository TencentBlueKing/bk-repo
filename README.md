# bk-repo 蓝盾制品库

## Overview

蓝盾制品库是一个基于微服务架构设计的平台，使用spring boot+ spring cloud的技术栈，kotlin作为主要的开发语言，微服务网关采用的是openresty
提供制品协议存储、代理、分发、晋级、扫描、包管理等功能。
蓝盾制品库采用多级分层的策略去接收制品文件，使用mongodb去存储节点信息与元数据信息，使用对象存储去永久的存储制品文件。


制品库架构图![制品库架构图](docs/resource/bkrepo.png)

- 应用场景层
- oss(运营支撑)层
- 接入协议层
- 存储与调度层


## Features
- auth 统一账号、权限管理，对接bk-user、bk-iam等账号权限体系
- repository 项目、仓库、节点管理
- metadata 元数据管理
- generic 通用制品管理
- rpm rpm包管理
- docker image、helm chart、oci 云原生镜像仓库
- npm、composer、pypi、maven、nuget依赖源微服务
- opdata 制品库admin服务
- replication 制品分发微服务
- webhook服务 webhook的订阅与推送
- scanner scanner-executor 制品扫描

## Getting started
* [下载与编译](docs/install/compile.md)
* [安装部署](docs/install/binary/README.md)
* [API使用说明见这里](docs/apidoc/)
* [使用Helm部署BKREPO到K8S环境](support-files/kubernetes/README.md)


