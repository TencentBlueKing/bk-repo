# 安装部署指南

本文主要着重于二进制部署，基于项目原先的部署文档进行补充和完善。

## 1. 前期准备说明

### 1.1 拉取代码以及编译

请参照仓库上的文档进行操作：[代码拉取及编译](../compile.md)

### 1.2 部署目录说明

部署的目录遵循蓝鲸运营规范，这里举例以 `/data/bkee` 作为主目录，用户可以自行更换，比如叫 `/a/b` 都可以。目录层次多，需要仔细看，具体如下：

```shell
|- /data/bkee  # 蓝鲸根目录
  |- ci      # ci部署程序目录
  |- bkrepo  # bkrepo部署目录
```

具体说明如下章节。

### 1.3 bkrepo部署目录

```shell
|- /data/bkee/bkrepo       # 程序主目录
  |- src
    |- backend             # 后端程序目录
    |- frontend            # 存放前端发布的静态资源目录
    |- gateway             # 网关配置文件及lua脚本
  |- scripts               # 部署脚本
  |- support-files         # 配置文件目录
```

### 1.4 系统要求

- CentOS 7.x +
- JDK 1.8
- MongoDB 3.6 +
- Consul 1.0 +

### 1.5 设置部署环境变量

编辑系统环境变量文件：

```bash
sudo vim /etc/profile
```

添加以下内容：

```bash
export WORK_DIR=/data/bkee
export BK_REPO_MONGODB_USER=<mongodb用户名>
export BK_REPO_MONGODB_PASSWORD=<mongodb密码>
export BK_REPO_MONGODB_ADDR=<mongodb地址>
export BK_REPO_MONGODB_DB_NAME=<mongodb数据库名>
export BK_REPO_SERVICE_PREFIX=bkrepo-
export BK_REPO_CONSUL_SERVER_HOST=<consul服务IP>
export BK_REPO_CONSUL_SERVER_PORT=<consul服务端口>
```

### 1.6 MongoDB初始化

按文件序号顺序执行 `support-files/sql` 目录下的脚本：

```bash
mongo -u $BK_REPO_MONGODB_USER -p $BK_REPO_MONGODB_PASSWORD $BK_REPO_MONGODB_ADDR/$BK_REPO_MONGODB_DB_NAME init-data.js
```

### 1.7 repo.env文件完善

配置 `$WORK_DIR/bkrepo/scripts/repo.env` 相关参数，按照说明进行补充完善空白，例如Redis的配置信息等。[env说明文档](../env.md)

注意：`BK_REPO_DEPLOY_MODE` 应设置为 `standalone` 而非 `ci`。

## 2. 安装Consul

### 2.1 安装及启动

参照官方文档进行安装：[安装consul](consul.md)

推荐采用服务端和客户端的方式启动。

示例：

```bash
consul agent -data-dir=/usr/local/var/consul -datacenter=bk-repo -domain=bk-repo -bind=<本地IP> -retry-join=<consul服务端IP> -http-port=8081 -ui （本地执行）
consul agent -server -data-dir=/data/consul -ui -client=0.0.0.0 -bind=<consul服务端IP> -http-port=8500 -datacenter=bk-repo -domain=bk-repo -bootstrap （工作机执行）
```

### 2.2 Consul配置初始化及后端配置文件渲染

涉及到配置文件里面有双"_"下划线定义的变量需要做占位符号替换，已经抽离到 `scripts/repo.env` 文件里:

- `scripts/repo.env` 中有对应的配置项，需要进行修改，如果遇到配置项涉及到蓝鲸的或者不会用到的，则可以保持默认配置不修改即可，修改后保存退出。

  - 修改 `INSTALL_PATH`，这个为安装主目录，默认是 `/data/bkee`
  - 修改 `MODULE` 变量建议不要修改，默认为 `bkrepo`

```bash
cd $WORK_DIR/bkrepo/scripts
chmod +x render_tpl
./render_tpl -u -p $WORK_DIR -m bkrepo -e repo.env $WORK_DIR/bkrepo/support-files/templates/*.yaml

services=(auth repository dockerapi generic docker helm maven npm)
for var in ${services[@]}; do
    service=$BK_REPO_SERVICE_PREFIX$var
    echo $service
    curl -T $WORK_DIR/etc/bkrepo/$var.yaml http://$BK_REPO_CONSUL_SERVER_HOST:$BK_REPO_CONSUL_SERVER_PORT/v1/kv/bkrepo-config/$service/data
done

curl -T $WORK_DIR/etc/bkrepo/application.yaml http://$BK_REPO_CONSUL_SERVER_HOST:$BK_REPO_CONSUL_SERVER_PORT/v1/kv/bkrepo-config/application/data
echo "put config to consul kv success."
```

注意：生成的配置文件部分服务可能会有问题，例如 `auth` 模块，后续需要手动调整。auth报错是因为github拉下来的代码鉴权模式默认为bkiam，要更改为local。

## 3. 程序部署

### 3.1 网关部署

采用OpenResty作为网关服务器，部署主要分为OpenResty安装，gateway的lua和nginx配置代码部署两部分。

#### 3.1.1 安装OpenResty

本次安装的是OpenResty 1.15.8.3版本。

安装完毕后检查安装情况：

```bash
cd /usr/local/openresty/nginx && ./sbin/nginx -v
```

如果出现 `nginx version: openresty/1.15.8.3` 则代表安装成功。

#### 3.1.2 配置网关

确保前面的 `repo.env` 文件配置正确。

```bash
cd $WORK_DIR/bkrepo/scripts
chmod +x render_tpl
./render_tpl -u -p /data/bkee -m bkrepo -e repo.env $WORK_DIR/bkrepo/support-files/templates/gateway*
```

执行上述命令会在代码目录生成一个 `gateway` 模块，将其移动到代码目录 `src` 下的 `gateway` 包中。

然后将 `/src/gateway` 模块软链接到nginx的 `conf` 目录下：

```bash
rm -rf /usr/local/openresty/nginx/conf
ln -s $WORK_DIR/bkrepo/gateway /usr/local/openresty/nginx/conf
```

另外，`repo.env` 中的 `BK_REPO_HOST` 需要在本地和工作机上配置：

```bash
sudo vim /etc/hosts
```

例如：

```bash
<工作机IP> bkrepo.example.com  (访问域名自己定义)
```

#### 3.1.3 启动命令

```bash
mkdir -p /usr/local/openresty/nginx/run/ # 创建PID目录 
cd /usr/local/openresty/nginx # 进入nginx安装目录 
./sbin/nginx -t  # 验证nginx的配置是否正确 
./sbin/nginx     # 启动nginx 
./sbin/nginx -s reload # 重启nginx
```

### 3.2 前端部署

#### 3.2.1 前端编译

- [前端编译](frontend.md)，对编译有兴趣可以自行研究

#### 3.2.2 打包压缩

```bash
cd ${WORK_DIR}/src/frontend/frontend/
zip -r ui_raw.zip ui
mv ui_raw.zip ${WORK_DIR}
```

#### 3.2.3 渲染配置出包

```bash
rm -rf ${WORK_DIR}/web
chmod +x ${WORK_DIR}/scripts/render_tpl
cd $WORK_DIR
mkdir -p ${WORK_DIR}/web/frontend/ui/
cp -r ${WORK_DIR}/src/frontend/frontend/ui/* ${WORK_DIR}/web/frontend/ui/
${WORK_DIR}/scripts/render_tpl -u -p . -m web -e ${WORK_DIR}/scripts/repo.env -E BK_REPO_DEPLOY_MODE=standalone ${WORK_DIR}/src/frontend/frontend/ui/frontend*
```

重新打包：

```bash
cd ${WORK_DIR}/web/frontend/
zip -r ui.zip ui
mv ui.zip ${WORK_DIR}/frontend
```

解压：

```bash
cd ${WORK_DIR}/frontend
rm -rf ui
unzip ui.zip
```

检查 `src/gateway/lua/init.lua` 中的 `static_dir` 和解压的 `ui` 目录是否一致，例如：

```lua
static_dir = "/data/bkee/bkrepo/frontend"
```

### 3.3 后端微服务部署

- [后端服务部署](backend.md)


访问 `BK_REPO_HOST` 进行验证。
```
