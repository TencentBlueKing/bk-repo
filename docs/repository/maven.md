####启动配置：server.port: 8081

####添加仓库：
```bash
curl -X POST http://127.0.0.1:8080/api/repo \
-H 'Content-Type: application/json' \
-d '{
  "projectId": "projectName",
  "name": "repositoryName",
  "type": "MAVEN",
  "category": "LOCAL|REMOTE|VIRTUAL",
  "public": true,
  "configuration": {"type": "local|remote|virtual"}
}'
```

```bash

```

#### deploy  jar:

```bash
mvn deploy:deploy-file  
-Dfile={filePath} 
-DgroupId={group} 
-DartifactId={artifact} 
-Dversion={version} 
-Dpackaging={packageType} 
#[-DrepositoryId=file-http]  如果仓库有鉴权需带上该参数   
-Durl={repositoryUrl}

# Example
mvn deploy:deploy-file  
-Dfile=/abc/bcd/example-1.0.0.jar 
-DgroupId=com.xxx.yyy.zzz 
-DartifactId=example 
-Dversion=1.0.0 
-Dpackaging=jar 
-DrepositoryId=file-http 
-Durl=http://ip:port/projectId/repositoryId
```

mvn `conf/settings.xml` 对应账户密码设置：

```xml
<servers>
    <server>
      <id>file-http</id>  <!--与请求中repositoryId保持一致-->
      <username>admin</username>
      <password>password</password>
    </server>
  </servers>
```

