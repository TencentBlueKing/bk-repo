# Replication仓库同步接口

[toc]

## 说明

- 现有方案需要把`operate_log` collection设置为Capped Collection

```bash
db.operate_log.isCapped()
db.runCommand({"convertToCapped":"operate_log",size:10000})
```

## 测试集群连通状态

- API: GET /replication/api/cluster/tryConnect
- API 名称: test_connect
- 功能说明：
	- 中文：测试集群连通状态
	- English：test connect status

- 请求体:

  ``` json
  {
      "name":"shanghai"
  }
  ```

- 请求字段说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |name|string|是|无|节点名称|cluster node name|

- 响应体:

  ```
  {
    "code": 0,
    "message": null,
    "data": null,
    "traceId": null
  }
  ```

## 创建集群同步任务

- API: POST  /replication//api/task/cluster
- API 名称: create_replication_task
- 功能说明：
	- 中文：创建集群同步任务
	- English：create replication task
- 请求体:

  ``` json
  {
    "name":"计划",
    "localProjectId":"bkrepo",
    "replicaTaskObjects":[
      {
        "localRepoName":"maven-local",
        "remoteProjectId":"bkrepo",
        "remoteRepoName":"maven-local",
        "repoType":"MAVEN",
        "packageConstraints":[
          {
            "packageKey":"gav://com.alibaba:fastjson",
            "versions":["1.2.47","1.2.48"]
          }
        ],
        "pathConstraints":[]
      }
    ],
    "replicaType":"SCHEDULED",
    "setting":{
      "rateLimit":0,
      "includeMetadata":true,
      "conflictStrategy":"SKIP",
      "errorStrategy":"CONTINUE",
      "executionPlan":{
        "executeImmediately":true
      }
    },
    "remoteClusterIds":["651095dfe0524ce9b3ab53d13532361c","329fbcda45944fb9ae5c2573acd7bd2a"],
    "enabled":true,
    "description":"test replica task"
  }
  ```

- 请求字段说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |name|string|是|无|计划名称|replication name|
  |localProjectId|string|是|无|本地项目ID|the local project Id|
  |replicaTaskObjects|object|是|无|同步对象信息|replication object info|
  |replicaType|enum|是|SCHEDULED|[SCHEDULED,REAL_TIME]|replication type|
  |setting|object|是|无|计划相关设置|task setting|
  |remoteClusterIds|list|是|无|远程集成节点id|the remote cluster node ids|
  |enabled|bool|是|true|计划是否启动|do task enabled|
  |description|sting|否|无|描述|description|
  
- replicaTaskObjects对象说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |localRepoName|string|是|无|本地仓库名称|the local repoName|
  |remoteProjectId|string|是|无|远程项目id|the remote project Id|
  |remoteRepoName|string|是|无|远程仓库名称|the remote repoName|
  |repoType|enum|是|无|[DOCKER,MAVEN,NPM, ...]|repository type|
  |packageConstraints|list|否|无|包限制|package constraints|
  |pathConstraints|list|否|无|路径限制|path constraints|
  
- setting对象说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |rateLimit|long|是|0|分发限速|rate limit|
  |includeMetadata|bool|是|true|是否同步元数据|do include metadata|
  |conflictStrategy|enum|是|SKIP|[SKIP,OVERWRITE,FAST_FAIL]|conflict strategy|
  |errorStrategy|enum|是|CONTINUE|[CONTINUE,FAST_FAIL]|error strategy|
  |executionPlan|object|是|无|调度策略|execution plan|

- executionPlan对象说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |executeImmediately|bool|是|true|立即执行|execute immediately|
  |executeTime|time|否|无|执行时间执行|execute time|
  |cronExpression|string|否|无|cron表达式执行|cron expression|


- 响应体

  ```
  {
    "code": 0,
    "message": null,
    "data": null,
    "traceId": null
  }
  ```
