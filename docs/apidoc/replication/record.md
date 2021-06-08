# Replication仓库同步执行日志接口

[toc]

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
    "data": {
      "id": "609b573d53ccce752bf9b860",
      "taskKey": "651095dfe0524ce9b3ab53d13532361c",
      "status": "SUCCESS",
      "startTime": "2021-05-12T12:19:08.813",
      "endTime": "2021-05-12T12:19:37.967",
      "errorReason": null
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

## 根据key分页查询任务执行日志列表

- API: GET /replication/api/task/record/detail/page/{key}
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
    "data": {
      "recordId": "609b573d53ccce752bf9b860",
      "localCluster": "651095dfe0524ce9b3ab53d13532361c",
      "remoteCluster": "SUCCESS",
      "status": "SUCCESS",
      "progress": {
        "blob": {
          "total": 10,
          "success": 10,
          "skip": 0,
          "failed": 0
        },
        "node": null,
        "version": null,
        "totalSize": 0
      },
      "startTime": "2021-05-12T12:19:08.813",
      "endTime": "2021-05-12T12:19:37.967",
      "errorReason": null
    },
    "traceId": null
  }
  ```

- data字段说明

  |字段|类型|说明|Description|
  |---|---|---|---|
  |recordId|string|记录唯一id|record id|
  |localCluster|string|本地集群名称|local cluster node name|
  |remoteCluster|string|远程集群名称|remote cluster node name|
  |status|enum|[RUNNING,SUCCESS,FAILED]|task execute status|
  |progress|object|同步进度|task execute progress|
  |startTime|date|任务开始执行时间|task execute start time|
  |endTime|date|任务结束执行时间|task execute end time|
  |errorReason|string|错误原因，未执行或执行成功则为null|task failed error reason|

- progress字段说明

  |字段|类型|说明|Description|
  |---|---|---|---|
  |blob|object|同步blob文件数量|task execute blob size|
  |node|object|同步节点数量|task execute node size|
  |version|object|同步包版本数量|task execute package version size|
  |totalSize|long|同步文件数据数量, 单位bytes|task execute file size|
  
- blob字段说明（node和version字段与其一样）

  |字段|类型|说明|Description|
  |---|---|---|---|
  |total|long|总量|total size|
  |success|long|成功数量|success size|
  |skip|long|跳过数量|skip size|
  |failed|long|失败数量|failed size|
  
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
  |repoName|string|否|无|仓库名称，支持前缀模糊匹配|repo name|
  |clusterName|string|否|无|远程节点名称，支持前缀模糊匹配|cluster node name|

- 响应体

  ```json
  {
    "code": 0,
    "message": null,
    "data": {
      "pageNumber": 0,
      "pageSize": 1,
      "totalRecords": 18,
      "totalPages": 2,
      "records": [
        {
          "recordId": "609b573d53ccce752bf9b860",
          "localCluster": "651095dfe0524ce9b3ab53d13532361c",
          "remoteCluster": "SUCCESS",
          "status": "SUCCESS",
          "progress": {
            "blob": {
              "total": 10,
              "success": 10,
              "skip": 0,
              "failed": 0
            },
            "node": null,
            "version": null,
            "totalSize": 0
          },
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
  |recordId|string|记录唯一id|record id|
  |localCluster|string|本地集群名称|local cluster node name|
  |remoteCluster|string|远程集群名称|remote cluster node name|
  |status|enum|[RUNNING,SUCCESS,FAILED]|task execute status|
  |progress|object|同步进度|task execute progress|
  |startTime|date|任务开始执行时间|task execute start time|
  |endTime|date|任务结束执行时间|task execute end time|
  |errorReason|string|错误原因，未执行或执行成功则为null|task failed error reason|

- progress字段说明

  |字段|类型|说明|Description|
  |---|---|---|---|
  |blob|object|同步blob文件数量|task execute blob size|
  |node|object|同步节点数量|task execute node size|
  |version|object|同步包版本数量|task execute package version size|
  |totalSize|long|同步文件数据数量, 单位bytes|task execute file size|
  
- blob字段说明（node和version字段与其一样）

  |字段|类型|说明|Description|
  |---|---|---|---|
  |total|long|总量|total size|
  |success|long|成功数量|success size|
  |skip|long|跳过数量|skip size|
  |failed|long|失败数量|failed size|
