# proxy部署
1.上传proxy到制品库任意generic仓库，例如上传到bkrepo项目、proxy仓库、完整路径为/proxy.jar

2.添加generic服务配置，项目、仓库、完整路径和上传的项目、仓库、完整路径保持一致
```yaml
generic:
  config:
    proxy:
      projectId: bkrepo
      repoName: proxy
      fullPath: /proxy.jar
```
3.创建proxy
使用管理员账号通过[接口](../apidoc/proxy/proxy.md#%20创建proxy)创建proxy

4.下载proxy
使用管理员账号通过[下载接口](../apidoc/proxy/proxy.md#%20下载proxy)下载proxy.jar

5.运行proxy
**运行依赖JDK版本 17**
配置项storage.filesystem.path为文件存储目录，需要确保此目录已创建

- Windows
```bashs
# 示例
javaw -D'storage.filesystem.path=Z:\data\store' -jar proxy.jar
```
启动成功后可以在当前工作目录下看到logs、runtime两个目录，logs目录中为proxy日志，runtime目录中proxy.pid文件保存了proxy的进程id

- Linux
添加/etc/systemd/system/service-proxy.service

/usr/bin/java为java路径  

/data/store为文件存储目录  

/data/work/proxy.jar为proxy.jar路径

根据实际部署环境调整
```
[Unit]
Description=service-proxy
Requires=network-online.target

[Service]
EnvironmentFile=-/usr/bin/java
Environment=GOMAXPROCS=2
Restart=on-failure
ExecStart=/usr/bin/java -server -Dsun.jnu.encoding=UTF-8 -Dfile.encoding=UTF-8 -D'storage.filesystem.path=/data/store' -jar /data/work/proxy.jar
ExecReload=/bin/kill -HUP $MAINPID
KillSignal=SIGTERM

[Install]
WantedBy=multi-user.target
```
启动proxy
```bash
systemctl daemon-reload
systemctl start service-proxy
```

6.配置router
使用管理员账号通过[接口](../apidoc/proxy/router.md#增加路由策略)配置路由策略
```bash
curl -X POST --header 'Content-Type: application/json' 'bkrepo.example.com/opdata/api/router/admin/policy' --data '{
  "destRouterNodeId": "{proxy_name}",
  "projectIds": [
    "{projectId}"
  ],
  "users": []
}'
```
