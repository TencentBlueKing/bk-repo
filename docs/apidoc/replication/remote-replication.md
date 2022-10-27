# 分发接口

[toc]

## 创建远端集群分发配置

- API: POST  /replication/api/remote/distribution/create/{projectId}/{repoName}
- API 名称: remote_distribution_create
- 功能说明：
	- 中文：创建远端集群分发配置
	- English：remote distribution create
- 请求体:
  ```json
  新建异构分发任务
  {
    "configs":[
    {
    "name": "mirrors",
    "registry":"{registry-host}/{repository}",
    "username":"***",
    "password":"****",
    "packageConstraints": [],
    "pathConstraints": [],
    "replicaType": "REAL_TIME",
    "setting": {
      "rateLimit": 0,
      "includeMetadata": true,
      "conflictStrategy": "SKIP",
      "errorStrategy": "CONTINUE",
      "executionStrategy": "IMMEDIATELY",
      "executionPlan": {
        "executeImmediately": true
      }
    },
    "enable": true,
    "description": "test replica task"
   }
  ]
  }
  ```
  ```json
  已有同构分发集群
  {
    "configs":[
    {
    "name": "mirrors",
    "clusterId":"xxxx",
    "remoteProjectId":"xxxx",
    "remoteRepoName":"xxxx",
    "packageConstraints": [],
    "pathConstraints": [],
    "replicaType": "REAL_TIME",
    "setting": {
      "rateLimit": 0,
      "includeMetadata": true,
      "conflictStrategy": "SKIP",
      "errorStrategy": "CONTINUE",
      "executionStrategy": "IMMEDIATELY",
      "executionPlan": {
        "executeImmediately": true
      }
    },
    "enable": true,
    "description": "test replica task"
   }
  ]
  }
  ```

- 请求字段说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |projectId|string|是|无|项目id|the project Id|
  |repoName|string|是|无|仓库名称|the repoName|
  |name|string|是|无|远端源名称| name|
  |clusterId|string|否|无|集群id，与registry互斥，必须存在一个| clusterId|
  |registry|string|否|无|远端源地址，与clusterId互斥，必须存在一个| registry|
  |username|string|否|无|用户名, 当registry不为空时配置，可为空| username|
  |password|string|否|无|密码，当registry不为空时配置，可为空| password|
  |certificate|string|否|无|证书，当registry不为空时配置，可为空| certificate|
  |remoteProjectId|string|否|无|远端项目id，当clusterId不为空时必填|remote ProjectId|
  |remoteRepoName|string|否|无|远端仓库id，当clusterId不为空时必填|remote RepoName|
  |packageConstraints|list|否|无|包限制|package constraints|
  |pathConstraints|list|否|无|路径限制|path constraints|
  |replicaType|enum|是|REAL_TIME|[SCHEDULED,REAL_TIME]|replication type|
  |setting|object|是|无|计划相关设置|task setting|
  |enable|bool|是|true|计划是否启动|do task enable|
  |description|sting|否|无|描述|description|
  
- setting对象说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |rateLimit|long|是|0|分发限速|rate limit|
  |includeMetadata|bool|是|true|是否同步元数据|do include metadata|
  |conflictStrategy|enum|是|SKIP|[SKIP,OVERWRITE,FAST_FAIL]|conflict strategy|
  |errorStrategy|enum|是|CONTINUE|[CONTINUE,FAST_FAIL]|error strategy|
  |executionStrategy|enum|是|IMMEDIATELY|[IMMEDIATELY,SPECIFIED_TIME,CRON_EXPRESSION]|execution strategy|
  |executionPlan|object|是|无|调度策略|execution plan|

- executionPlan对象说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |executeImmediately|bool|是|true|立即执行|execute immediately|
  |executeTime|time|否|无|执行时间执行|execute time|
  |cronExpression|string|否|无|cron表达式执行|cron expression|


- 响应体

  ```json
  {
    "code": 0,
    "message": null,
    "data": null,
    "traceId": ""
  }
  ```


## 更新远端集群分发配置

- API: POST  /replication/api/remote/distribution/update/{projectId}/{repoName}/{name}
- API 名称: remote_distribution_update
- 功能说明：
	- 中文：更新远端集群分发配置
	- English：remote distribution update
- 请求体:

  ```json
  更新异构分发集群
    {
    "registry":"{registry-host}/{repository}",
    "username":"***",
    "password":"****",
    "packageConstraints": [],
    "pathConstraints": [],
    "replicaType": "REAL_TIME",
    "setting": {
      "rateLimit": 0,
      "includeMetadata": true,
      "conflictStrategy": "SKIP",
      "errorStrategy": "CONTINUE",
      "executionStrategy": "IMMEDIATELY",
      "executionPlan": {
        "executeImmediately": true
      }
    },
    "enable": true,
    "description": "test replica task"
   }
  ```
   ```json
  更新同构分发集群
    {
    "clusterId":"xxxx",
    "remoteProjectId":"xxxx",
    "remoteRepoName":"xxxx",
    "packageConstraints": [],
    "pathConstraints": [],
    "replicaType": "REAL_TIME",
    "setting": {
      "rateLimit": 0,
      "includeMetadata": true,
      "conflictStrategy": "SKIP",
      "errorStrategy": "CONTINUE",
      "executionStrategy": "IMMEDIATELY",
      "executionPlan": {
        "executeImmediately": true
      }
    },
    "enable": true,
    "description": "test replica task"
   }
  ```

- 请求字段说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |projectId|string|是|无|项目id|the project Id|
  |repoName|string|是|无|仓库名称|the repoName|
  |name|string|是|无|远端源名称| name|
  |clusterId|string|否|无|集群id，与registry互斥，必须存在一个| clusterId|
  |registry|string|否|无|远端源地址，与clusterId互斥，必须存在一个| registry|
  |username|string|否|无|用户名, 当registry不为空时配置，可为空| username|
  |password|string|否|无|密码，当registry不为空时配置，可为空| password|
  |certificate|string|否|无|证书，当registry不为空时配置，可为空| certificate|
  |remoteProjectId|string|否|无|远端项目id，当clusterId不为空时必填|remote ProjectId|
  |remoteRepoName|string|否|无|远端仓库id，当clusterId不为空时必填|remote RepoName|
  |packageConstraints|list|否|无|包限制|package constraints|
  |pathConstraints|list|否|无|路径限制|path constraints|
  |replicaType|enum|是|REAL_TIME|[SCHEDULED,REAL_TIME]|replication type|
  |setting|object|是|无|计划相关设置|task setting|
  |enable|bool|是|true|计划是否启动|do task enable|
  |description|sting|否|无|描述|description|
  
- setting对象说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |rateLimit|long|是|0|分发限速|rate limit|
  |includeMetadata|bool|是|true|是否同步元数据|do include metadata|
  |conflictStrategy|enum|是|SKIP|[SKIP,OVERWRITE,FAST_FAIL]|conflict strategy|
  |errorStrategy|enum|是|CONTINUE|[CONTINUE,FAST_FAIL]|error strategy|
  |executionStrategy|enum|是|IMMEDIATELY|[IMMEDIATELY,SPECIFIED_TIME,CRON_EXPRESSION]|execution strategy|
  |executionPlan|object|是|无|调度策略|execution plan|

- executionPlan对象说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |executeImmediately|bool|是|true|立即执行|execute immediately|
  |executeTime|time|否|无|执行时间执行|execute time|
  |cronExpression|string|否|无|cron表达式执行|cron expression|


- 响应体

  ```json
  {
    "code": 0,
    "message": null,
    "data": null,
    "traceId": ""
  }
  ```

## 查询远端集群分发配置

- API: GET  /replication/api/remote/distribution/info/{projectId}/{repoName}/{name}
          /replication/api/remote/distribution/info/{projectId}/{repoName}
- API 名称: remote_distribution_search
- 功能说明：
	- 中文：查询远端集群分发配置
	- English：remote distribution search
- 请求体:


- 请求字段说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |projectId|string|是|无|项目id|the project Id|
  |repoName|string|是|无|仓库名称|the repoName|
  |name|string|否|无|远端源名称| name|
  

- 响应体

  ```json
  {
    "code": 0,
    "message": null,
    "data": [
        {
            "projectId": "{projectId}",
            "repoName": "{repoName}",
            "name": "mirrors",
            "registry":"{registry-host}/{repository}",
            "certificate": null,
            "username": null,
            "password": null,
            "packageConstraints": null,
            "pathConstraints": null,
            "replicaType": "REAL_TIME",
            "setting": {
                "rateLimit": 0,
                "includeMetadata": true,
                "conflictStrategy": "SKIP",
                "errorStrategy": "FAST_FAIL",
                "executionStrategy": "IMMEDIATELY",
                "executionPlan": {
                    "executeImmediately": true,
                    "executeTime": null,
                    "cronExpression": null
                }
            },
            "description": null,
            "enable": true
        }
    ],
    "traceId": ""
  }
  ```

 ## 删除远端集群分发配置

- API: DELETE  /replication/api/remote/distribution/delete/{projectId}/{repoName}/{name}
- API 名称: remote_distribution_delete
- 功能说明：
	- 中文：删除远端集群分发配置
	- English：remote distribution delete
- 请求体:


- 请求字段说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |projectId|string|是|无|项目id|the project Id|
  |repoName|string|是|无|仓库名称|the repoName|
  |name|string|是|无|远端源名称| name|
  

- 响应体
```json
{
    "code": 0,
    "message": null,
    "data": null,
    "traceId": ""
}
```

## 禁用/启用远端集群分发配置

- API: POST  /replication/api/remote/distribution/toggle/status/{projectId}/{repoName}/{name}
- API 名称:toggle_remote_distribution_status
- 功能说明：
	- 中文：禁用/启用远端集群分发配置
	- English：toggle remote distribution status
- 请求体:


- 请求字段说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |projectId|string|是|无|项目id|the project Id|
  |repoName|string|是|无|仓库名称|the repoName|
  |name|string|是|无|远端源名称| name|
  

- 响应体
```json
{
    "code": 0,
    "message": null,
    "data": null,
    "traceId": ""
}
```

## 创建一次性分发任务

- API: POST  /replication/api/remote/distribution/create/runOnceTask/{projectId}/{repoName}
- API 名称: runonce_remote_distribution_create
- 功能说明：
	- 中文：创建一次性分发配置
	- English：create runonce remote distribution 
- 请求体:
  ```json
  新建异构分发集群
  {
    "name": "xxxx",
    "registry":"{registry-host}/{repository}",
    "username":"***",
    "password":"****",
    "packageName":"nginx",
    "versions": ["1.1"],
    "targetVersions":["1.2","1.3"],
    "description":"xxxx"
  }
  ```
  ```json
  已有同构分发集群,同步package
  {
    "name": "xxxx",
    "clusterId":"xxxx",
    "remoteProjectId":"xxxx",
    "remoteRepoName":"xxxx",
    "packageName":"nginx",
    "versions": ["1.1"],
    "description":"xxxx"
  }
  ```
  ```json
  已有同构分发集群,同步path
  {
    "name": "xxxx",
    "clusterId":"xxxx",
    "remoteProjectId":"xxxx",
    "remoteRepoName":"xxxx",
    "pathConstraints":["/a.txt"],
    "description":"xxxx"
  }
  ```

- 请求字段说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |projectId|string|是|无|项目id|the project Id|
  |repoName|string|是|无|仓库名称|the repoName|
  |name|string|是|无|远端源名称| name|
  |clusterId|string|否|无|集群id，与registry互斥，必须存在一个| clusterId|
  |registry|string|否|无|远端源地址，与clusterId互斥，必须存在一个| registry|
  |username|string|否|无|用户名, 当registry不为空时配置，可为空| username|
  |password|string|否|无|密码，当registry不为空时配置，可为空| password|
  |certificate|string|否|无|证书，当registry不为空时配置，可为空| certificate|
  |remoteProjectId|string|否|无|远端项目id，当clusterId不为空时必填|remote ProjectId|
  |remoteRepoName|string|否|无|远端仓库id，当clusterId不为空时必填|remote RepoName|
  |packageName|string|否|无|包名，必须与versions一起使用|package name|
  |versions|list|否|无|包对应版本列表，必须与packageName一起使用|package versions|
  |targetVersions|list|否|无|推送目标版本,只有当versions数量为1时才可以设置，仅针对镜像类型|target versions|
  |pathConstraints|list|否|无|路径限制|path constraints|
  |description|sting|否|无|描述，在此填请求来源|description|



- 响应体

  ```json
  {
    "code": 0,
    "message": null,
    "data": null,
    "traceId": ""
  }
  ```


## 执行一次性任务

- API: POST  /replication/api/remote/distribution/execute/runOnceTask/{projectId}/{repoName}?name={name}
- API 名称:execute_runonce_task
- 功能说明：
	- 中文：执行一次性任务
	- English：execute task
- 请求体:


- 请求字段说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |name|string|是|无|任务名| name|
  

- 响应体
```json
{
    "code": 0,
    "message": null,
    "data": null,
    "traceId": ""
}
```



## 查询一次性任务的执行结果

- API: GET  /replication/api/remote/distribution/get/runOnceTaskStatus/{projectId}/{repoName}?name={name}
- API 名称:get_runonce_task_status
- 功能说明：
	- 中文：获取一次性任务执行结果
	- English：get task status
- 请求体:


- 请求字段说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |name|string|是|无|任务名| name|
  

- 响应体
```json
{
    "code": 0,
    "message": null,
    "data": {
        "id": "63043f3ff0cad86bddf5e215",
        "taskKey": "a39012fb57564d13b5c5c237d67f01dc",
        "status": "SUCCESS",
        "startTime": "2022-08-23T10:45:19.79",
        "endTime": "2022-10-20T17:14:37.229",
        "errorReason": null
    },
    "traceId": "8c9e8b15bd87b8fae86b72c4bf97c96a"
}
```

- 返回字段说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |taskKey|string|是|无|任务key|task key|
  |status|string|是|无|执行状态 [SUCCESS，RUNNING，FAILED]| status|
  |startTime|string|否|无|任务开始执行时间| startTime|
  |endTime|string|否|无|任务结束执行时间| endTime|
  |errorReason|string|否|无|错误原因| errorReason|



## 删除已经执行完成的一次性任务

- API: DELETE  /replication/api/remote/distribution/delete/runOnceTask/{projectId}/{repoName}?name={name}
- API 名称:delete_runonce_task
- 功能说明：
	- 中文：删除已经执行完成的一次性任务
	- English：delete runonce task
- 请求体:


- 请求字段说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |name|string|是|无|任务名| name|
  

- 响应体
```json
{
    "code": 0,
    "message": null,
    "data": null,
    "traceId": null
}
```