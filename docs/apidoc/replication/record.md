

# Replication仓库同步执行日志接口

[toc]

## 根据recordId查询任务执行日志

- API: GET /replication/api/task/record/{recordId}
- API 名称: get_task_record
- 功能说明：
  - 中文：查询任务信息
  - English：get task record
- 请求体
  此接口无请求体
- 请求字段说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |recordId|string|是|无|记录唯一key|record id|

- 响应体

  ```json
  {
    "code": 0,
    "message": null,
    "data": {
      "replicaObjectType": "REPOSITORY",    
      "record": {
        "id": "609b573d53ccce752bf9b860",
        "taskKey": "651095dfe0524ce9b3ab53d13532361c",
        "status": "SUCCESS",
        "startTime": "2021-05-12T12:19:08.813",
        "endTime": "2021-05-12T12:19:37.967",
        "errorReason": null
      }
    },
    "traceId": null
  }
  ```

- data字段说明

  |字段|类型|说明|Description|
  |---|---|---|---|
  |replicaObjectType|enum|[REPOSITORY,PACKAGE,PATH]|replica object type|
  |id|string|执行日志唯一id|record id|
  |taskKey|string|任务唯一key|task key|
  |status|enum|[RUNNING,SUCCESS,FAILED]|task status|
  |startTime|date|任务开始执行时间|task execute start time|
  |endTime|date|任务结束执行时间|task execute end time|
  |errorReason|string|错误原因，未执行或执行成功则为null|task failed error reason|

## 根据key查询任务执行日志列表

- API: GET /replication/api/task/record/list/{key}
- API 名称: list_task_record
- 功能说明：
  - 中文：查询任务信息列表
  - English：list task record
- 请求体
  此接口无请求体
- 请求字段说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |key|string|是|无|任务唯一key|task key|

- 响应体

  ```json
  {
    "code": 0,
    "message": null,
    "data": [
      {
        "id": "609b573d53ccce752bf9b860",
        "taskKey": "651095dfe0524ce9b3ab53d13532361c",
        "status": "SUCCESS",
        "startTime": "2021-05-12T12:19:08.813",
        "endTime": "2021-05-12T12:19:37.967",
        "errorReason": null
      }
    ],
    "traceId": null
  }
  ```

- data字段说明

  |字段|类型|说明|Description|
  |---|---|---|---|
  |id|string|执行日志唯一id|record id|
  |taskKey|string|任务唯一key|task key|
  |status|enum|[RUNNING,SUCCESS,FAILED]|task status|
  |startTime|date|任务开始执行时间|task execute start time|
  |endTime|date|任务结束执行时间|task execute end time|
  |errorReason|string|错误原因，未执行或执行成功则为null|task failed error reason|

## 根据key分页查询任务执行日志列表

- API: GET /replication/api/task/record/page/{key}
- API 名称: list_task_record_page
- 功能说明：
  - 中文：分页查询任务日志列表
  - English：list task record page
- 请求体
  此接口无请求体
- 请求字段说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |key|string|是|无|任务唯一id|task id|
  |pageNumber|int|是|无|当前页|page number|
  |pageSize|int|是|无|分页数量|page size|
  |status|enum|否|无|执行状态|execute status|

- 响应体

  ```json
  {
    "code": 0,
    "message": null,
    "data": {
      "pageNumber": 0,
      "pageSize": 1,
      "totalRecords": 8,
      "totalPages": 2,
      "records": [
        {
          "id": "609b573d53ccce752bf9b860",
          "taskKey": "651095dfe0524ce9b3ab53d13532361c",
          "status": "SUCCESS",
          "startTime": "2021-05-12T12:19:08.813",
          "endTime": "2021-05-12T12:19:37.967",
          "errorReason": null
        }
      ]
    },
    "traceId": null
  }
  ```

- data字段说明

  |字段|类型|说明|Description|
  |---|---|---|---|
  |id|string|执行日志唯一id|record id|
  |taskKey|string|任务唯一key|task key|
  |status|enum|[RUNNING,SUCCESS,FAILED]|task status|
  |startTime|date|任务开始执行时间|task execute start time|
  |endTime|date|任务结束执行时间|task execute end time|
  |errorReason|string|错误原因，未执行或执行成功则为null|task failed error reason|


## 根据recordId查询任务执行日志详情列表

- API: GET /replication/api/task/record/detail/list/{recordId}
- API 名称: list_task_record_detail
- 功能说明：
  - 中文：查询任务日志详情列表
  - English：list task record detail
- 请求体
  此接口无请求体
- 请求字段说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |recordId|string|是|无|任务执行日志唯一id|record id|

- 响应体

  ```json
  {
    "code": 0,
    "message": null,
    "data": [
      {
        "id": "979b573d53efcd752bf9b762",
        "recordId": "609b573d53ccce752bf9b860",
        "localCluster": "651095dfe0524ce9b3ab53d13532361c",
        "remoteCluster": "SUCCESS",
        "localRepoName": "npm-local",
        "repoType": "NPM",
        "packageConstraint": {
          "packageKey": "npm://helloworld",
          "versions": ["1.1.0","1.3.0"]
        },
        "pathConstraint": null,
        "status": "SUCCESS",
        "progress": {
          "success": 10,
          "skip": 0,
          "failed": 0,
          "totalSize": 100
        },
        "startTime": "2021-05-12T12:19:08.813",
        "endTime": "2021-05-12T12:19:37.967",
        "errorReason": null
      }
    ],
    "traceId": null
  }
  ```

- data字段说明

  |字段|类型|说明|Description|
  |---|---|---|---|
  |id|string|记录详情唯一id|record detail id|
  |recordId|string|记录唯一id|record id|
  |localCluster|string|本地集群名称|local cluster node name|
  |remoteCluster|string|远程集群名称|remote cluster node name|
  |localRepoName|string|本地仓库名称|local repository name|
  |repoType|enum|[DOCKER,NPM,RPM,...]|local repository type|
  |packageConstraints|object|否|无|包限制|package constraints|
  |pathConstraints|object|否|无|路径限制|path constraints|
  |status|enum|[RUNNING,SUCCESS,FAILED]|task execute status|
  |progress|object|同步进度|task execute progress|
  |startTime|date|任务开始执行时间|task execute start time|
  |endTime|date|任务结束执行时间|task execute end time|
  |errorReason|string|错误原因，未执行或执行成功则为null|task failed error reason|

- progress字段说明
  
  |字段|类型|说明|Description|
  |---|---|---|---|
  |success|long|成功数量|success size|
  |skip|long|跳过数量|skip size|
  |failed|long|失败数量|failed size|
  |totalSize|long|数据总量|total size|
  
## 根据recordId分页查询任务执行日志详情列表

- API: GET /replication/api/task/record/detail/page/{recordId}
- API 名称: list_task_record_detail_page
- 功能说明：
  - 中文：分页查询任务日志详情列表
  - English：list task record detail page
- 请求体
  此接口无请求体
- 请求字段说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |recordId|string|是|无|任务执行日志唯一id|record id|
  |pageNumber|int|是|无|当前页|page number|
  |pageSize|int|是|无|分页数量|page size|
  |packageName|string|否|无|包名称，支持前缀模糊匹配|package name|
  |repoName|string|否|无|仓库名称|repo name|
  |clusterName|string|否|无|远程节点名称|cluster node name|
  |path|string|否|无|路径名称，支持前缀模糊匹配|file path|
  |status|enum|否|无|[SUCCESS,RUNNING,FAILED]|execute status|
  |artifactName|string|否|无|制品名称（Generic为fullPath）|artifact name|
  |version|string|否|无|版本|artifact version|

- 响应体

  ```json
  {
  "code" : 0,
  "message" : null,
  "data" : {
    "pageNumber" : 1,
    "pageSize" : 20,
    "totalRecords" : 2,
    "totalPages" : 1,
    "records" : [ {
      "id" : "65b07d80b69e4404f9ba4a50",
      "recordId" : "65b07d7eb69e4404f9ba4a4e",
      "localCluster" : "center",
      "remoteCluster" : "dev",
      "localRepoName" : "generic",
      "repoType" : "GENERIC",
      "packageConstraint" : null,
      "pathConstraint" : null,
      "artifactName" : "/bkrepo.op.conf.bak",
      "version" : null,
      "conflictStrategy" : "SKIP",
      "size" : 646,
      "sha256" : "bcdf4256783b6d9cf3cb7bcd666e59c6e0a5d7e58e45c9469bb29a60a69ffff4",
      "status" : "SUCCESS",
      "progress" : {
        "success" : 0,
        "skip" : 0,
        "failed" : 0,
        "totalSize" : 0,
        "conflict" : 0
      },
      "startTime" : "2024-01-24T11:01:20.534",
      "endTime" : "2024-01-24T11:01:20.556",
      "errorReason" : ""
    } ],
    "count" : 2,
    "page" : 1
  },
  "traceId" : "2f6803fcf2b5d1ab8cbbb97fa1d20b52"
  }
  ```

- data字段说明

  |字段|类型|说明|Description|
  |---|---|---|---|
  |id|string|记录详情唯一id|record detail id|
  |recordId|string|记录唯一id|record id|
  |localCluster|string|本地集群名称|local cluster node name|
  |remoteCluster|string|远程集群名称|remote cluster node name|
  |localRepoName|string|本地仓库名称|local repository name|
  |repoType|enum|[DOCKER,NPM,RPM,...]|local repository type|
  |packageConstraints|object|否|无|包限制|package constraints|
  |pathConstraints|object|否|无|路径限制|path constraints|
  |artifactName|string|制品名称（历史数据为null）|artifact name|
  |version|string|版本（Generic制品和历史数据为null）|artifact version|
  |conflictStrategy|string|冲突策略（没有冲突和历史数据为null）|conflict strategy|
  |size|long|制品大小|artifact size|
  |sha256|string|制品摘要（依赖源和历史数据为null）|artifact digest|
  |status|enum|[RUNNING,SUCCESS,FAILED]|task execute status|
  |progress|object|同步进度|task execute progress|
  |startTime|date|任务开始执行时间|task execute start time|
  |endTime|date|任务结束执行时间|task execute end time|
  |errorReason|string|错误原因，未执行或执行成功则为null|task failed error reason|

- progress字段说明(废弃)

  |字段|类型|说明|Description|
  |---|---|---|---|
  |success|long|成功数量|success size|
  |skip|long|跳过数量|skip size|
  |failed|long|失败数量|failed size|
  |totalSize|long|数据总量|total size|



## 根据recordId查询任务执行总览

- API: GET /replication/api/task/record/overview/{recordId}

- API 名称: get_task_record_overview

- 功能说明：

  - 中文：根据recordId查询任务执行总览
  - English：get task record overview

- 请求体
  此接口无请求体

- 请求字段说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |recordId|string|是|无|任务执行日志唯一id|record id|


- 响应体

  ```json
  {
      "code": 0,
      "message": null,
      "data": {
          "success": 0,
          "failed": 0,
          "conflict": 0
      },
      "traceId": "6c4376c1f002127e9444d9897d0ee7ce"
  }
  ```

- 历史数据响应体

  ```json
  {
  	"code": 0, 
      "message": null,
      "data": null,
      "traceId": "3df4fdf97a77684e352fd17973064ece"
  }
  ```

  

- data字段说明(历史数据为null)

  |字段|类型|说明|Description|
  |---|---|---|---|
  |success|long|成功数量|success size|
  |failed|long|失败数量|failed size|
  |conflict|long|冲突数量|conflict size|