# Kubernetes部署
## 概述
BK-Repo k8s构建工具

## 目录
```
./
├── README.md                  
├── base-charts                 # 基础环境 helm charts目录
├── base-image                  # 基础环境 镜像构建目录
│   ├── linux             # linux的一些工具
│   ├── dockerfile        # 基础镜像Dockerfile
│   ├── jdk               # jdk目录
│   └── build.sh          # 镜像构建脚本
├── bkrepo-charts               # bkrepo业务helm charts目录
├── bkrepo-image                # bkrepo业务镜像构建目录
│   ├── backend           # bkrepo 微服务 Dockerfile
│   ├── gateway           # bkrepo 网关 Dockerfile
│   ├── init              # bkrepo 初始化job Dockerfile
│   └── build.sh          # bkrepo 业务镜像构建脚本
└── build.env                   # 构建环境变量
```

## 环境准备
1. 搭建k8s集群
2. 准备mongodb服务器
3. 准备nfs服务器
    - 共享 /data/nfs目录 
    - mkdir /data/nfs/bkrepo/consul  # consul数据目录
    - mkdir /data/nfs/bkrepo/storage # 数据存储目录
4. 所有宿主机需要安装: nfs-common , nfs-utils
5. jdk/jre拷贝到base-image/jdk目录
6. consul拷贝到base-image/linux目录

## 打包基础镜像
```shell script
cd base-image
./build.sh
```

## 部署基础镜像
```shell script
cd base-charts
# 根据需要修改values.yaml
# helm2
helm install -n bkrepo-base . --namespace=bkrepo
# helm3
helm install bkrepo-base . --namespace=bkrepo
```

## 打包业务镜像
```shell script
cd bkrepo-image
./build.sh
```

## 部署业务镜像
```shell script
cd bkrepo-charts
# 根据需要修改values.yaml
# helm2
helm install -n bkrepo . --namespace=bkrepo
# helm3
helm install bkrepo . --namespace=bkrepo
```

## 构件charts包
```
# 基础环境charts包
cd base-charts
helm package .

# 业务环境charts包
cd bkrepo-charts
helm package .
```