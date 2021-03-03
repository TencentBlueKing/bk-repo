# Kubernetes部署
## 概述
bk-repo k8s构建工具

## 目录结构
```              
├── charts                 # helm charts目录
│    ├── base              # 基础环境helm charts目录
│    └── bkrepo            # 业务相关helm charts目录
├── images                 # 业务镜像构建目录
│    ├── backend           # 微服务 Dockerfile
│    ├── gateway           # 网关 Dockerfile
│    ├── init              # 初始化job Dockerfile
│    └── build.sh          # 业务镜像构建脚本
├── build.env              # 构建环境变量
└── README.md 
```

## 环境准备
- k8s集群和helm环境
- mongodb服务器
- nfs服务器
    - 共享 /data/nfs目录 
    - mkdir /data/nfs/bkrepo/storage # 数据存储目录
- 所有宿主机需要安装: nfs-common , nfs-utils

## 使用方式

1. 添加helm仓库
    ```shell
    $ helm repo add bkee <bkrepo helm repo url>
    "bkee" has been added to your repositories
    ```

2. 确认访问helm仓库正常
    ```shell
    $ helm search repo bkee/bkrepo
    NAME            	CHART VERSION	APP VERSION	DESCRIPTION
    bkee/bkrepo     	1.0.0        	1.0.0      	BlueKing Repository
    bkee/bkrepo-base	1.0.0        	1.0.0      	BlueKing Repository Base Enviromement
    ```

3. 部署bkrepo-base(基础环境)

    *基础环境包含了consul、ingress-nignx和volume的配置，如果k8s环境已经部署过consul、ingress-nignx, 可以通过配置忽略consul、ingress-nignx*

    `config-base.yaml` 配置请参考[./charts/base/values.yaml](./charts/base/values.yaml)

    ```shell
    $ helm install bkrepo bkee/bkrepo-base --namespace=default -f config-base.yaml
    NAME: bkrepo-base
    ...
    ```

4. 部署bkrepo

    `config.yaml` 配置请参考[./charts/bkrepo/values.yaml](./charts/bkrepo/values.yaml)

    ```shell
    $ helm install bkrepo bkee/bkrepo --namespace=default -f config.yaml
    NAME: bkrepo
    ...
    ```

## 构建镜像和charts包指引

1. 构建docker镜像
    ```shell script
    ./images/build.sh
    ```

2. 部署基础镜像
    ```shell script
    cd charts/base
    # 根据需要修改values.yaml
    # helm2
    helm install -n bkrepo-base . --namespace=bkrepo
    # helm3
    helm install bkrepo-base . --namespace=bkrepo
    ```

3. 部署业务镜像
    ```shell script
    cd charts/bkrepo
    # 根据需要修改values.yaml
    # helm2
    helm install -n bkrepo . --namespace=bkrepo
    # helm3
    helm install bkrepo . --namespace=bkrepo
    ```

4. 构建helm charts包
    ```shell script
    # 基础环境charts包
    cd base-charts
    helm package .

    # 业务环境charts包
    cd bkrepo-charts
    helm package .
    ```