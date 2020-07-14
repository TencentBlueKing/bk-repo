####启动配置：server.port: 8082

####添加仓库：
```bash
curl -X POST http://127.0.0.1:8080/service/repo \
-H 'Content-Type: application/json' \
-d '{
  "projectId": "projectName",
  "name": "repositoryName",
  "type": "PYPI",
  "category": "LOCAL|REMOTE|VIRTUAL",
  "public": true,
  "configuration": {"type": "local|remote|virtual"}
}'
```

<b>默认在Python3下运行  </b>

**全局依赖源配置：**
配置文件路径：`~/.pip/pip.conf`
```conf
[global]
index-url = http://ip:port/projectId/repositoryId/simple
username = admin
password = password
```

**Python 3.6.0开始不再支持`./pypirc`,所以upload指定仓库地址**

**依赖源地址需要加上`/simple`**



#### Upload：

```bash
#使用twine作为上传工具
python3 -m twine upload --repository-url {repositoryUrl} [-u user] [-p password] dist/*
#Example
python3 -m twine upload --repository-url http://ip:port/projectId/repositoryId -u admin -p password dist/*
```



#### install

```bash
pip3 install -i {repositoryUrl} {package}=={version}
#Example
pip3 install -i http://ip:port/projectId/repositoryId/simple installPackage==0.0.1
```



#### Search

```bash
#查看已安装的包
pip3 list
#删除安装包
pip3 uninstall package
#search
pip3 search -i {repositoryUrl} {package}|{summary}
#Example
pip3 search -i http://ip:port/projectId/repositoryId installPackage
```



