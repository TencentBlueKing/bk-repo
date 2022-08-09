# 审计日志接口

[toc]

## 分页查询日志

- API: POST /repository/api/log/list
- API 名称: list_log
- 功能说明：
	- 中文：查询日志
	- English：list_log

- 请求体
```json
    {
        "pageNumber": 1,
        "pageSize": 20,
        "projectId": "test",
        "repoName": "generic-local",
        "resourceKey": "/release/1.0.0/boot-example.jar",
        "eventType": "NODE_DOWNLOADED",
        "sha256": "6671fe83b7a07c8932ee89164d1f2793b2318058eb8b98dc5c06ee0a5a3b0ec1",
        "pipelinId": "p-123456",
        "buildId": "b-123456",
        "userId": "admin",
        "startTime": "2022-08-01T15:00:00.000",
        "endTime": "2022-08-02T15:00:00.000",
        "direction": "DESC"
    }
```

- 请求字段说明

  |字段|类型|是否必须|默认值| 说明                                                                  |Description|
  |---|---|---|---------------------------------------------------------------------|---|---|
  |pageNumber|int|否|1| 页数                                                                  |page number|
  |pageSize|int|否|20| 页大小                                                                 |page size|
  |projectId|string|是|无| 项目ID                                                                |project id|
  |repoName|string|是|无| 仓库名称                                                                |repo name|
  |resourceKey|string|是|无| 事件资源key，支持通配符*，[详细说明](#resourcekey%E8%AF%B4%E6%98%8E) |resource key|
  |eventType|string|是|无| 事件类型，参考[公共事件](../common/event.md)                                   |event type|
  |sha256|string|否|无| 文件sha256。查询节点类型事件日志时选填                                              |sha256|
  |pipelinId|string|否|无| 文件元数据pipelineId。查询节点类型事件日志时选填                                       |pipeline id|
  |buildId|string|否|无| 文件元数据buildId。查询节点类型事件日志时选填                                          |buildId|
  |userId|string|否|无| 事件触发用户名                                                             |user id|
  |startTime|string|否|当前时间的前3个月| 开始时间                                                                |start time|
  |endTime|string|否|当前时间| 截至时间                                                                |end time|
  |direction|string|否|DESC| 按时间排序方向，默认降序。可选值ASC/DESC                                            |direction|


- 响应体

``` json
{
    "code": 0,
    "message": null,
    "data": {
        "pageNumber": 1,
        "pageSize": 20,
        "totalRecords": 1,
        "totalPages": 1,
        "records": [
            {
                "createdDate": "2022-08-02T15:20:47.235",
                "type": "NODE_DOWNLOADED",
                "projectId": "test",
                "repoName": "generic-local",
                "resourceKey": "/release/1.0.0/boot-example.jar",
                "userId": "admin",
                "clientAddress": "127.0.0.1",
                "description": {
                    "md5": "036208b4a1ab4a235d75c181e685e5a3",
                    "sha256": "6671fe83b7a07c8932ee89164d1f2793b2318058eb8b98dc5c06ee0a5a3b0ec1"
                }
            }
        ],
        "count": 1,
        "page": 1
    },
    "traceId": ""
}
```

## resourceKey说明

事件资源key，具有唯一性
1. 节点类型对应fullPath
2. 仓库类型对应仓库名称
3. 包类型对应包名称

例如：
- 节点下载事件，resourceKey即为下载节点的fullPath。节点的fullPath为/release/1.0.0/boot-example.jar,那么resourceKey为/release/1.0.0/boot-example.jar。
- 查询节点下载记录时，可以查询某个文件或目录的下载记录。查询/release/1.0.0/目录下文件的下载记录，resourceKey为/release/1.0.0/；查询/release/1.0.0/目录下jar类型文件的下载记录，resouceKey为/release/1.0.0/*.jar
    