## 使用指引

<<<<<<< HEAD
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
=======
仓库地址配置：


>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4

配置文件目录：/etc/yum.repos.d/

全局默认配置文件：CentOS-Base.repo

或者自定义：{name}.repo

<<<<<<< HEAD
=======


>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
参考数据格式：

```txt
[bkrepo]        
name=bkrepo     //仓库名
<<<<<<< HEAD
baseurl=http://admin:password@{repositoryUrl}/$releasever/os/$basearch //仓库地址，如果有开启认证，需要在请求前添加 用户名：密码
=======
baseurl=http://admin:password@10.67.82.183:8084/bkrepo/rpm-local/$releasever/os/$basearch //仓库地址，如果有开启认证，需要在请求前添加 用户名：密码
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
keepcache=0  //是否开启缓存，测试阶段推荐开启，否则上传后，yum install 时会优先去本地缓存找
enabled=1    //地址授信，如果非 https 环境必须设为1
gpgcheck=0   //设为0，目前还不支持gpg签名
metadata_expire=1m  //本地元数据过期时间 ，测试阶段数据量不大的话，时间越短测试越方便
```

<<<<<<< HEAD
```shell
#发布
curl -uadmin:password -XPUT {repositoryUrl} -T {文件路径}
=======


<hr/>

```shell
#添加仓库 目前只支持本地仓库
curl -X POST http://127.0.0.1:8080/api/repo \
-H 'Content-Type: application/json' \
-d '{
  "projectId": "projectName",
  "name": "repositoryName",
  "type": "RPM
  "category": "LOCAL",
  "public": true,
  "configuration": {
    "type": "rpm-local",  
    "repodataDepth": 3, #代表索引生成的目录的深度
    "enabledFileLists": true, #是否启用单独的filelists.xml 索引
    "groupXmlSet": []     #仓库分组设置
  },
  "description": "for bkdevops test"
}'

#发布
curl -uadmin:password -XPUT http://{host}:{port}/{project}/{repo}/ -T {文件路径}
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
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

<<<<<<< HEAD
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
=======
#组相关操作：将分组文件上传到仓库下具体某个repodata路径，
curl -uadmin:password -XPUT http://{host}:{port}/{project}/{repo}/{仓库下具体repodata路径} -T {xxx.xml}
#安装group下所有包
yum groupinstall {groupName}
#卸载group下所有包：只是卸载包，该group依然会显示已被安装。
yum groupremove {groupName}
#本机移除分组：推荐卸载后执行该命令，
yum groups mark remove {groupName}
#升级group下所有包
yum groupupdate {groupName}

```



<hr/>

##  rpm仓库属性解释：

**repodataDepth**： 代表索引生成的目录的位置（对应请求的层级）,当rpm构件`deploy`请求路径参数小于repodataDepth大小时（project, repo 不包含在请求路径中）,不会计算构件索引。
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4

例如：repodataDepth：2 ，项目名：bkrepo ，仓库名：rpm-local。

请求1：`curl -uadmin:password -XPUT http://{host}:{port}/bkrepo/rpm-local/a/b/ -T {filePath}`

请求2：`curl -uadmin:password -XPUT http://{host}:{port}/bkrepo/rpm-local/a/ -T {filePath}`

请求3：`curl -uadmin:password -XPUT http://{host}:{port}/bkrepo/rpm-local/a/b/c/ -T {filePath}`

-- 请求1的构件将会计算索引，`yum install` 可以下载到，索引目录：`/a/b/repodata`

-- 请求2的构件不会计算索引，`yum install` 下载不到，但是包会保存在服务器上。

<<<<<<< HEAD
-- 请求3的构件将会计算索引，`yum install` 可以下载到，索引目录：`/a/b/repodata`
=======
-- 请求3的构件将会计算索引，`yum install` 可以下载到，索引目录：`/a/b/repodata`



**enabledFileLists**:  是否启用单独的filelists.xml 索引，开启后rpm包的filelists信息会单独保存在filelists.xml中，否则是保存在primary.xml中。



**groupXmlSet**： 仓库分组设置，添加仓库允许的分组文件列表，只有出现在列表中的分组文件才会被计算索引，客户端才可以下载到。



<hr/>

##rpm package

官方文档：[RPM Packaging Guide](https://rpm-packaging-guide.github.io/)

```shell
#安装打包工具
yum install gcc rpm-build rpm-devel rpmlint make python bash coreutils diffutils patch rpmdevtools

#在一个空白目录新建一个`.spec`文件
touch xxx.spec    
#打包
rpmdev-setuptree
rpmbuild -ba hello-world.spec
```



参考数据：

```txt
Name:       bkrepo-test
Version:    1.1
Release:    1
Summary:    Test same artifactUri but different content!
License:    FIXME

%description
This is my first RPM package, which does nothing.

%prep
# we have no source, so nothing here

%build
cat > bkrepo-test.sh <<EOF
#!/usr/bin/bash
echo bkrepo test
EOF

%install
mkdir -p %{buildroot}/usr/bin/
install -m 755 bkrepo-test.sh %{buildroot}/usr/bin/bkrepo-test.sh

%files
/usr/bin/bkrepo-test.sh

%changelog
# let's skip this for now
```



###  rpm group

```shell
#分组文件生成
yum-groups-manager \
-n "bkrepo" \  group name
--id=bkrepo \  group id
--save=bkrepo.xml \保存的文件名
--mandatory {包名} {包名} 
```

>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
