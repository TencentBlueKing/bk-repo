## 使用指引

#### 创建仓库 -- develop 分支：

```bash
curl -X POST http://{ip}/repository/repo/ \
-H 'Content-Type: application/json' \
-d '{ 
  "projectId": "{project}",
  "name": "{repo}",
  "type": "RPM",
  "category": "LOCAL",
  "public": true,
  "configuration": {
    "type": "rpm-local",  
    "repodataDepth": 1,  
    "enabledFileLists": false,
    "groupXmlSet": [
      "groups.xml",
      "bkrepos.xml"
    ]
  },
  "description": "for bkdevops test"
}'
```



#### 创建仓库 -- develop_new 分支：

```bash
curl -X POST http://{ip}/repository/repo/create \
-H 'Content-Type: application/json' \
-d '{ 
  "projectId": "{project}",
  "name": "{repo}",
  "type": "RPM",
  "category": "COMPOSITE",
  "public": true,
  "configuration": {
    "type": "composite",  
    "settings": {
            "enabledFileLists": false,
            "repodataDepth": 1,
            "groupXmlSet": [
            	"groups.xml",
      				"bkrepos.xml"
            ]
        }
  },
  "description": "for bkdevops test"
}'
```



```bash
#新增仓库配置分组文件名
curl -X PUT http://{ip}/rpm/configuration/{project}/{repo}/ \
-H 'Content-Type: application/json' \
-d '[
      "abc.xml",
      "bkrepo.xml"
    ]'

#移除仓库配置分组文件名
curl -X DELETE http://{ip}/rpm/configuration/{project}/{repo}/ \
-H 'Content-Type: application/json' \
-d '[
      "abc.xml",
      "bkrepos.xml"
    ]'
```





仓库地址配置：

配置文件目录：/etc/yum.repos.d/

全局默认配置文件：CentOS-Base.repo

或者自定义：{name}.repo

参考数据格式：

```txt
[bkrepo]        
name=bkrepo     //仓库名
baseurl=http://admin:password@{repositoryUrl}/$releasever/os/$basearch //仓库地址，如果有开启认证，需要在请求前添加 用户名：密码
keepcache=0  //是否开启缓存，测试阶段推荐开启，否则上传后，yum install 时会优先去本地缓存找
enabled=1    //地址授信，如果非 https 环境必须设为1
gpgcheck=0   //设为0，目前还不支持gpg签名
metadata_expire=1m  //本地元数据过期时间 ，测试阶段数据量不大的话，时间越短测试越方便
```

```shell
#发布
curl -uadmin:password -XPUT {repositoryUrl} -T {文件路径}
#下载
yum install -y {package}
# 添加源后快速使源生效
yum check-update
#从指定源下载
yum install nginx --enablerepo={源名称}
#删除包
yum erase -y {package}
#清除缓存的包
yum clean packages --enablerepo={源名称}
#清除对应仓库的元数据
yum clean metadata --enablerepo={源名称}

#上传组文件,必须上传至仓库repodata 目录
curl -uadmin:password -XPUT {repositoryUrl}/repodata/ -T {文件路径}

#group 列表
yum grouplist
#下载组包含的包
yum groupinstall {group}
#在系统中移除组，不会移除安装的包
yum groups mark remove {group}
#移除通过组安装的包，在系统中组依然是被安装
yum groupremove {group}
```

<hr/>

#### repodataDepth 解释：

repodataDepth： 代表索引生成的目录的位置（对应请求的层级）,当rpm构件`deploy`请求路径参数小于repodataDepth大小时（project, repo 不包含在请求路径中）,不会计算构件索引。

例如：repodataDepth：2 ，项目名：bkrepo ，仓库名：rpm-local。

请求1：`curl -uadmin:password -XPUT http://{host}:{port}/bkrepo/rpm-local/a/b/ -T {filePath}`

请求2：`curl -uadmin:password -XPUT http://{host}:{port}/bkrepo/rpm-local/a/ -T {filePath}`

请求3：`curl -uadmin:password -XPUT http://{host}:{port}/bkrepo/rpm-local/a/b/c/ -T {filePath}`

-- 请求1的构件将会计算索引，`yum install` 可以下载到，索引目录：`/a/b/repodata`

-- 请求2的构件不会计算索引，`yum install` 下载不到，但是包会保存在服务器上。

-- 请求3的构件将会计算索引，`yum install` 可以下载到，索引目录：`/a/b/repodata`