
####添加仓库：
```bash
curl -X POST http://127.0.0.1:8080/api/repo \
-H 'Content-Type: application/json' \
-d '{
  "projectId": "projectName",
  "name": "repositoryName",
  "type": "PYPI",
  "category": "LOCAL|REMOTE|VIRTUAL|COMPOSITE",
  "public": true,
  "configuration": {"type": "local|remote|virtual|composite"}
}'

#添加remote 仓库
curl -X POST http://127.0.0.1:8080/api/repo \
-H 'Content-Type: application/json' \
-d '{
  "projectId": "projectName",
  "name": "repositoryName",
  "type": "PYPI",
  "category": "REMOTE",
  "public": true,
  "configuration":{
        "type":"remote",
        "url":"https://pypi.org/"  
  }
}'
```

<b>默认在Python3下运行  </b>

**全局依赖源配置：**
配置文件路径：`~/.pip/pip.conf`
```conf
[bkrepo]
index-url = http://ip:port/projectId/repositoryId/simple
username = admin
password = password
```


**依赖源地址需要加上`/simple`**



#### Upload:
配置仓库地址和认证信息:$HOME/.pypirc
=======
配置仓库地址和认证信息
```txt
[distutils]
index-servers = bkrepo
[bkrepo]
repository = http://ip:port/projectId/repositoryId/
username = admin
password = password
```

```bash
#使用twine作为上传工具
python3 -m twine upload -r {bkrepo} dist/*
```

#### install

替换默认依赖源地址

- MacOS/Liunx配置目录 :  $HOME/.pip/pip.conf
- Windows配置目录 :  %HOME%/pip/pip.ini
  ```txt
  [global]
  index-url = http://{admin}:{PASSWORD}@{repositoryUrl}/simple
  [install]
  trusted-host=http://{repositoryUrl}
  ```
- 执行下面命令：
  ```bash
  pip3 install {packageName}=={version}
  ```
  
指定依赖源下载
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



