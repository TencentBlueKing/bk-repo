# BK-REPO

此Chart用于在Kubernetes集群中通过helm部署bkrepo

## 环境要求
- Kubernetes 1.12+
- Helm 3+
- PV provisioner

## 安装Chart
使用以下命令安装名称为`bkrepo`的release, 其中`<bkrepo helm repo url>`代表helm仓库地址:

```shell
$ helm repo add bkee <bkrepo helm repo url>
$ helm install bkrepo bkee/bkrepo
```

上述命令将使用默认配置在Kubernetes集群中部署bkrepo, 并输出访问指引。

## 卸载Chart
使用以下命令卸载`bkrepo`:

```shell
$ helm uninstall bkrepo
```

上述命令经移除所有和bkrepo相关的Kubernetes组件，并删除release。

## Chart依赖
- [bitnami/nginx-ingress-controller](https://github.com/bitnami/charts/tree/master/bitnami/nginx-ingress-controller)
- [bitnami/mongodb](https://github.com/bitnami/charts/blob/master/bitnami/mongodb)

## 配置说明
下面展示了可配置的参数列表以及默认值

### Charts 镜像通用配置
|参数|描述|默认值 |
|---|---|---|
| `image.repository` | bkrepo镜像仓库地址 | `mirrors.tencent.com` |
| `image.pullPolicy` | bkrepo镜像拉取策略 | `IfNotPresent` |
| `image.tag` | bkrepo镜像tag，不设置则使用`appVersion` | `""` |
| `imagePullSecrets` | bkrepo镜像拉取secret name列表 | `[]` |
| `nameOverride` | 覆盖Release名称 | `""` |
| `fullnameOverride` | 覆盖Release完整名称 | `""` |
| `podAnnotations` | Pod 公共标注 | `""` |
| `podSecurityContext` | Pod Security Context | `{}` |
| `securityContext` | 容器 Security Context | `{}` |

### RBAC 配置
|参数|描述|默认值 |
|---|---|---|
| `serviceAccount.create` | 是否为Pod创建serviceAccount | `true` |
| `serviceAccount.annotations` | serviceAccount注解 | `{}` |
| `serviceAccount.name` | serviceAccount名称，不设置则使用`fullname template` | `""` |
| `rbac.create` | 是否创建RBAC资源 | `true` |

### ingress 配置
|参数|描述|默认值 |
|---|---|---|
| `ingress.enabled` | 是否创建ingress | `true` |
| `annotations` | ingress标注 | Check `values.yaml` |

### nginx-ingress 配置
默认将部署nginx-ingress-controller，如果不需要可以关闭。
相关配置请参考[bitnami/nginx-ingress-controller](https://github.com/bitnami/charts/tree/master/bitnami/)

|参数|描述|默认值 |
|---|---|---|
| `nginx-ingress-controller.enabled` | 是否部署nginx ingress controller | `true` |
| `nginx-ingress-controller.image.registry` | nginx ingress controller镜像地址 | `docker.io` |

### mongodb 配置
默认将部署mongodb，如果不需要可以关闭。
相关配置请参考[bitnami/mongodb](https://github.com/bitnami/charts/blob/master/bitnami/mongodb)

|参数|描述|默认值 |
|---|---|---|
| `mongodb.enabled` | 是否部署mognodb | `true` |
| `mongodb.image.registry` | mongodb镜像地址 | `docker.io` |
| `mongodb.fullnameOverride` | mongodb release 完整名称 | `bkrepo-mongodb` |
| `mongodb.architecture` | 部署模式 | `standalone` |
| `mongodb.auth.enabled` | 是否开启认证 | `true` |
| `mongodb.auth.database` | mongodb数据库名称 | `bkrepo` |
| `mongodb.auth.username` | mongodb认证用户名 | `bkrepo` |
| `mongodb.auth.password` | mongodb密码 | `bkrepo` |

### 数据持久化配置

数据持久化配置, 当使用filesystem方式存储时需要配置。

|参数|描述|默认值 |
|---|---|---|
| `persistence.enabled` | 是否开启数据持久化，false则使用emptyDir类型volume, pod结束后数据将被清空，无法持久化 | `true` |
| `persistence.accessModes` | PVC Access Mode for bkrepo data volume | `ReadWriteOnce` |
| `persistence.size` | PVC Storage Request for bkrepo data volume | `100Gi` |
| `persistence.storageClass` | 指定storageClass。如果设置为"-", 则禁用动态卷供应; 如果不设置, 将使用默认的storageClass(minikube上是hostPath) | `100Gi` |
| `persistence.existingClaim` | 如果开启持久化并且定义了该项，则绑定k8s集群中已存在的pvc | `nil` |

> 如果开启数据持久化，并且没有配置`existingClaim`，将使用[动态卷供应](https://kubernetes.io/docs/concepts/storage/dynamic-provisioning/)提供存储，使用`storageClass`定义的存储类。在删除该声明后，这个卷也会被销毁。

### 数据初始化job配置
|参数|描述|默认值 |
|---|---|---|
| `jobs.initMongodb` | 是否初始化mongodb数据，支持幂等执行 | `true` |

### bkrepo公共配置
|参数|描述|默认值 |
|---|---|---|
| `common.jvmOption` | jvm启动选项, 如-Xms1024M -Xmx1024M | `""` |
| `common.springProfile` | SpringBoot active profile | `dev` |
| `common.username` | bkrepo初始用户名 | `admin` |
| `common.password` | bkrepo初始密码 | `blueking` |
| `common.mongodb.uri` | mongodb 标准连接字符串 | `mongodb://bkrepo:bkrepo@bkrepo-mongodb:27017/bkrepo` (通过依赖的mongodb Charts部署的mongodb地址) |
| `common.storage.type` | 存储类型，支持filesystem/cos/s3/hdfs | `filesystem` |
| `common.storage.filesystem.path` | filesystem存储方式配置，存储路径 | `/data/storage` |
| `common.storage.cos` | cos存储方式配置 | `nil` |
| `common.storage.s3` | s3存储方式配置 | `nil` |
| `common.storage.hdfs` | hdfs存储方式配置 | `nil` |

### 网关配置
|参数|描述|默认值 |
|---|---|---|
| `gateway.service.type` | 服务类型 | `ClusterIP` |
| `gateway.service.port` | 服务类型为`ClusterIP`时端口设置 | `80` |
| `gateway.service.nodePort` | 服务类型为`NodePort`时端口设置 | `80` |
| `gateway.replicaCount` | Kubernetes replicaCount 配置 | `1` |
| `gateway.resources` | Kubernetes resources 配置 | `{}` |
| `gateway.tolerations` | Kubernetes tolerations 配置 | `[]` |
| `gateway.affinity` | Kubernetes affinity 配置 | `[]` |
| `gateway.host` | bkrepo 地址 | `bkrepo.com` |
| `gateway.dnsServer` | dns服务器地址，用于配置nginx resolver | `local=on`(openrestry语法，取本机`/etc/resolv.conf`配置) |
| `gateway.authorization` | 网关访问微服务认证信息 | `"Platform MThiNjFjOWMtOTAxYi00ZWEzLTg5YzMtMWY3NGJlOTQ0YjY2OlVzOFpHRFhQcWs4NmN3TXVrWUFCUXFDWkxBa00zSw=="` |
| `gateway.deployMode` | 部署模式，standalone: 独立模式，ci: 与ci搭配模式 | `standalone` |

### repository服务配置
|参数|描述|默认值 |
|---|---|---|
| `repository.replicaCount` | Kubernetes replicaCount 配置 | `1` |
| `repository.resources` | Kubernetes resources 配置 | `{}` |
| `repository.tolerations` | Kubernetes tolerations 配置 | `[]` |
| `repository.affinity` | Kubernetes affinity 配置 | `[]` |
| `repository.config.deletedNodeReserveDays` | 节点被删除后多久清理数据 | `15` |

### auth服务配置
|参数|描述|默认值 |
|---|---|---|
| `auth.replicaCount` | Kubernetes replicaCount 配置 | `1` |
| `auth.resources` | Kubernetes resources 配置 | `{}` |
| `auth.tolerations` | Kubernetes tolerations 配置 | `[]` |
| `auth.affinity` | Kubernetes affinity 配置 | `[]` |
| `auth.config.realm` | 认证realm类型，支持local/devops | `local` |

### generic服务配置
|参数|描述|默认值 |
|---|---|---|
| `generic.enabled` | 是否部署generic | `true` |
| `generic.replicaCount` | Kubernetes replicaCount 配置 | `1` |
| `generic.resources` | Kubernetes resources 配置 | `{}` |
| `generic.tolerations` | Kubernetes tolerations 配置 | `[]` |
| `generic.affinity` | Kubernetes affinity 配置 | `[]` |

### docker registry服务配置
|参数|描述|默认值 |
|---|---|---|
| `docker.enabled` | 是否部署docker | `false` |
| `docker.replicaCount` | Kubernetes replicaCount 配置 | `1` |
| `docker.resources` | Kubernetes resources 配置 | `{}` |
| `docker.tolerations` | Kubernetes tolerations 配置 | `[]` |
| `docker.affinity` | Kubernetes affinity 配置 | `[]` |

### npm registry服务配置
|参数|描述|默认值 |
|---|---|---|
| `npm.enabled` | 是否部署npm | `false` |
| `npm.replicaCount` | Kubernetes replicaCount 配置 | `1` |
| `npm.resources` | Kubernetes resources 配置 | `{}` |
| `npm.tolerations` | Kubernetes tolerations 配置 | `[]` |
| `npm.affinity` | Kubernetes affinity 配置 | `[]` |

### pypi registry服务配置
|参数|描述|默认值 |
|---|---|---|
| `pypi.enabled` | 是否部署pypi | `false` |
| `pypi.replicaCount` | Kubernetes replicaCount 配置 | `1` |
| `pypi.resources` | Kubernetes resources 配置 | `{}` |
| `pypi.tolerations` | Kubernetes tolerations 配置 | `[]` |
| `pypi.affinity` | Kubernetes affinity 配置 | `[]` |

### helm registry服务配置
|参数|描述|默认值 |
|---|---|---|
| `helm.enabled` | 是否部署helm | `false` |
| `helm.replicaCount` | Kubernetes replicaCount 配置 | `1` |
| `helm.resources` | Kubernetes resources 配置 | `{}` |
| `helm.tolerations` | Kubernetes tolerations 配置 | `[]` |
| `helm.affinity` | Kubernetes affinity 配置 | `[]` |


可以通过`--set key=value[,key=value]`来指定参数进行安装。例如，

```shell
$ helm install bkrepo bkee/bkrepo --set image.pullPolicy=Always
  
```

上述命令将设置`image.pullPolicy`为`Always`。

另外，也可以通过指定`YAML`文件的方式来提供参数。例如，

```shell
$ helm install bkrepo bkee/bkrepo -f values
```

可以使用`helm show values`来获取默认配置，

```shell
# 查看默认配置
$ helm show values bkee/bkrepo

# 保存values.yaml
$ helm show values bkee/bkrepo > values.yaml
```

## 配置案例

### 1. 使用已有的mongodb
```
# 关闭mongodb部署
mongodb.enabled=false
# 设置已有的mongod连接字符串
common.mongodb.uri=mongodb://user:pass@mongodb-server:27017/bkrepo
```

### 2. 使用已有的ingress-controller
```
# 关闭nginx-ingress-controller部署
nginx-ingress-controller.enabled=false

# 根据需要配置ingress annotations
# ingress.annotations.key=value
```

### 3. 使用已有的pvc
```
# 开启数据持久化
persistence.enabled=true
persistence.existingClaim=my-persistent-volume-claim
persistence.accessModes=访问模式列表
persistence.size=pvc大小
```

### 4. 内网环境下，使用代理镜像仓库
```
# 修改mongodb镜像仓库
mongodb.image.registry=xxx
# 修改nginx-ingress-controller镜像仓库
nginx-ingress-controller.image.registry=xxx
# 修改bkrepo镜像仓库
image.registry=xxx
```

### 5. 自定义host
```
gateway.host=bkrepo.xxx.com
```

### 6. 不部署ingress，使用NodePort直接访问
```
ingress.enabled=false
nginx-ingress-controller.enabled=false
gateway.service.type=NodePort
gateway.service.nodePort=30000
```
部署成功后，即可通过host:nodePort访问

### 6. 不部署ingress，使用port-forward访问
```
ingress.enabled=false
nginx-ingress-controller.enabled=false
gateway.service.type=ClusterIP
```

部署成功后，通过`kubectl port-forward`将`repo-gateway`服务暴露出去，即可通过host:port访问
```shell
kubectl port-forward service/repo-gateway <port>:80
```

## 常见问题

**1. 首次启动失败，是bkrepo Chart有问题吗**
答: bkrepo的Chart依赖了`mongodb`和`nignx-ingress-controller`, 这两个依赖的Chart默认从docker hub拉镜像，如果网络不通或被docker hub限制，将导致镜像拉取失败，可以参考配置列表修改镜像地址。

**2. 首次启动时间过长，READY状态为`0/1`，是bkrepo Chart太垃圾了吗？**
答: 如果选择了部署`mongodb Chart`，需要等待`mongodb`部署完成，否则初始化`Job`以及其余`Pod`内的进程启动失败，这个期间k8s探针检测失败，所以容器状态为`Not Ready`，只需等待`mongodb`部署完成后，其余`Pod`被调度重启即可。

**3. 我卸载了Release立即重新部署，Pod状态一直为`Pending`，是bkrepo Chart太鸡肋了吗？**
答: 如果选择了默认方式使用动态卷供应，当使用`helm uninstall`卸载Release，随后创建的pvc也会被删除，如果在pvc被删除之前重新部署，新启动的`Pod`会进入`Pending`状态。

**4. 如何查看日志？**
答: 有两种方式可以查看日志: 1. kubectl logs pod 查看实时日志  2.日志保存在/data/workspace/logs目录下，可以进入容器内查看