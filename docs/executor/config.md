# 配置项说明

#### 配置文件repo-executor

```bash
executor:
  # 存放报告的文件地址
  uri: mongodb://{user}:{password}@localhost:27017/bkrepo?maxPoolSize=50
  scan: 
    # 工作目录
    rootDir: /data/example
    # 执行模板文件存放地址
    configTemplateDir: /data/example/standalone.toml
    # 模板文件名臣
    configName: standalone.toml
    # 输出文件相对地址
    outputDir: /output/
    # 输入文件相对地址
    inputDir: /package/
    # 是否在进程启动时开启全量扫描，默认为false
    full: false 
    # 是否在任务执行完成之后清理工作空间
    clean: false
container:
  api: 
    # 此配置一般不用修改
    host: unix:///var/run/docker.sock
    # api版本，一般不用修改
    version: 1.23   
  run: 
    # 启动容器entrypoint的 args
    args: /xxx/standalone.toml
    # 执行任务的镜像名称
    imageName: scan
    # 容器内的执行目录
    dir: /data
spring:
  autoconfigure:
    exclude: org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,com.tencent.bkrepo.common.job.JobAutoConfiguration

```

#### 配置文件standalone.toml

```bash
[metadata]
standalonemode = true
# 任务ID，standalone模式可以忽略
taskID = #{[taskId]}
# 描述，standalone模式可以忽略
description = "This is a simple reference project configuration."
# 无需修改
analysistype = "Artifact"
# 无需修改
analysissubtype = "BinaryPackage"
  
[toparse]
[[toparse.binarypackage]]
# filename 输入文件路径，相对地址
filename = "./package/#{[sha256]}"

[output]
[output.json]
# json 输出目录，会自动创建目录
path = "./output"
# 过滤列表，不输出某些分析项目
blacklist = ["file_items"]

# 无需修改
[output.log]
loglevel = "INFO"
logfile = "./sysauditor.log"

# 无需修改
[auditor]
include_text = true
include_binary = true

[sysauditor.nvtools]
enabled = true
username = 
key = 
host = 
```
