# 支持多源分发特性接口

[toc]

## 创建第三方集群分发配置

- API: POST  /replication/api/thirdParty/create/{projectId}/{repoName}
- API 名称: create_third_party
- 功能说明：
	- 中文：创建第三方集群分发配置
	- English：create third party
- 请求体:

  ```json
  {
    "configs":[
    {
    "name": "chartmuseum",
    "registry":"http://localhost:8080/",
    "username":"***",
    "password":"****",
    "packageConstraints": [
          {
            "packageKey": "gav://com.alibaba:fastjson",
            "versions": ["1.2.47","1.2.48"]
          }
        ],
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
    "enabled": true,
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
  |name|string|是|无|第三方源名称| name|
  |registry|string|是|无|第三方源地址| registry|
  |username|string|否|无|用户名| username|
  |password|string|否|无|密码| password|
  |certificate|string|否|无|证书| certificate|
  |packageConstraints|list|否|无|包限制|package constraints|
  |pathConstraints|list|否|无|路径限制|path constraints|
  |replicaType|enum|是|REAL_TIME|[SCHEDULED,REAL_TIME]|replication type|
  |setting|object|是|无|计划相关设置|task setting|
  |enabled|bool|是|true|计划是否启动|do task enabled|
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


## 更新第三方集群分发配置

- API: POST  /replication/api/thirdParty/update/{projectId}/{repoName}/{name}
- API 名称: update_third_party
- 功能说明：
	- 中文：更新第三方集群分发配置
	- English：update third party
- 请求体:

  ```json
    {
    "registry":"http://localhost:8080/",
    "username":"***",
    "password":"****",
    "packageConstraints": [
          {
            "packageKey": "gav://com.alibaba:fastjson",
            "versions": ["1.2.47","1.2.48"]
          }
        ],
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
    "enabled": true,
    "description": "test replica task"
   }
  ```

- 请求字段说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |projectId|string|是|无|项目id|the project Id|
  |repoName|string|是|无|仓库名称|the repoName|
  |name|string|是|无|第三方源名称| name|
  |registry|string|是|无|第三方源地址| registry|
  |username|string|否|无|用户名| username|
  |password|string|否|无|密码| password|
  |certificate|string|否|无|证书| certificate|
  |packageConstraints|list|否|无|包限制|package constraints|
  |pathConstraints|list|否|无|路径限制|path constraints|
  |replicaType|enum|是|REAL_TIME|[SCHEDULED,REAL_TIME]|replication type|
  |setting|object|是|无|计划相关设置|task setting|
  |enabled|bool|是|true|计划是否启动|do task enabled|
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

## 查询第三方集群分发配置

- API: GET  /replication/api/thirdParty/info/{projectId}/{repoName}/{name}
- API 名称: search_third_party
- 功能说明：
	- 中文：查询第三方集群分发配置
	- English：search third party
- 请求体:


- 请求字段说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |projectId|string|是|无|项目id|the project Id|
  |repoName|string|是|无|仓库名称|the repoName|
  |name|string|否|无|第三方源名称| name|
  

- 响应体

  ```json
  {
    "code": 0,
    "message": null,
    "data": [
        {
            "projectId": "test",
            "repoName": "test",
            "name": "chartmuseum",
            "registry": "http://localhost:8080",
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

 ## 删除第三方集群分发配置

- API: DELETE  /replication/api/thirdParty/delete/{projectId}/{repoName}/{name}
- API 名称: delete_third_party
- 功能说明：
	- 中文：删除第三方集群分发配置
	- English：delete third party
- 请求体:


- 请求字段说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |projectId|string|是|无|项目id|the project Id|
  |repoName|string|是|无|仓库名称|the repoName|
  |name|string|是|无|第三方源名称| name|
  

- 响应体
```json
{
    "code": 0,
    "message": null,
    "data": null,
    "traceId": ""
}
```

## 禁用/启用第三方集群分发配置

- API: POST  /replication/api/thirdParty/toggle/status/{projectId}/{repoName}/{name}
- API 名称:toggle_status_third_party
- 功能说明：
	- 中文：禁用/启用第三方集群分发配置
	- English：toggle third party
- 请求体:


- 请求字段说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |projectId|string|是|无|项目id|the project Id|
  |repoName|string|是|无|仓库名称|the repoName|
  |name|string|是|无|第三方源名称| name|
  

- 响应体
```json
{
    "code": 0,
    "message": null,
    "data": null,
    "traceId": ""
}
```