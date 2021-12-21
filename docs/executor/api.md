## 扫描器执行相关接口

### 扫描项目下仓库的文件

- API:POST  /executor/scan/repo
- API 名称: scan_repo
- 功能说明：
	- 中文：扫描整个仓库
	- English：scan the repository

- input body:

``` json
{
    "projectId":"data",
    "repoName":"test",
    "name":".apk",
    "rule":"SUFFIX"
}
```

- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|projectId|string|是|无|项目ID|projectId|
|repoName|string|是|无|仓库名称|repository name|
|name|string|否|无|文件名称|repository name|
|rule|string|否|EQ|匹配规则|file name match condition|

- output:

```
{
    "code": 0,
    "message": null,
    "data": "1636118059057",
    "traceId": ""
}

```

- output 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误 |0:success, other: failure|
|message|result message|错误消息 |the failure message |
|data | string | task |the task Id|
|traceId|string|请求跟踪id|the trace id|


### 扫描仓库下单个文件

- API:POST  /executor/scan/file
- API 名称: scan_file
- 功能说明：
	- 中文：扫描整个仓库
	- English：scan file in the repository

- input body:

``` json
{
    "projectId":"data",
    "repoName":"test",
    "fullPath":"/data/aa.apk",
}
```

- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|projectId|string|是|无|项目ID|projectId|
|repoName|string|是|无|仓库名称|repository name|
|fullPath|string|否|无|文件完成路径|repository name|

- output:

```
{
    "code": 0,
    "message": null,
    "data": "1636118059057",
    "traceId": ""
}

```

- output 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误 |0:success, other: failure|
|message|result message|错误消息 |the failure message |
|data | string | task |the task Id|
|traceId|string|请求跟踪id|the trace id|

### 分页获取任务执行状态

- API:GET  /executor/scan/status
- API 名称: scan_file
- 功能说明：
	- 中文：获取任务执行状态
	- English：get the task run status

- input body:

``` json

```

- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|projectId|string|是|无|项目ID|projectId|
|repoName|string|是|无|仓库名称|repository name|
|taskId|string|否|无|任务ID| 任务ID|
|pageNum|int|否|无|起始页数| the page number|
|pageSize|int|否|无|每页大小| the page size|

- output:

```
{
    "code": 0,
    "message": null,
    "data": {
        "totalRecords": 4,
        "records": [
            {
                "projectId": "keen",
                "repoName": "test",
                "fullPath": "/landun1.apk",
                "status": "FINISH"
            },
            {
                "projectId": "keen",
                "repoName": "test",
                "fullPath": "/landun2.apk",
                "status": "FINISH"
            },
            {
                "projectId": "keen",
                "repoName": "test",
                "fullPath": "/landun3.apk",
                "status": "FINISH"
            },
            {
                "projectId": "keen",
                "repoName": "test",
                "fullPath": "/landun4.apk",
                "status": "FINISH"
            }
        ]
    },
    "traceId": ""
}

```

- output 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误 |0:success, other: failure|
|message|result message|错误消息 |the failure message |
|data | result data | 返回数据 |the record|
|traceId|string|请求跟踪id|the trace id|


- data 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|totalRecords|int|总记录个数|the total record num|
|projectId|string|项目ID|the project Id|
|repoName|string|仓库名称|the repo name|
|fullPath|string|文件完整路径|the full path|


### 获取报告

- API:GET  /executor/scan/report
- API 名称: scan_file
- 功能说明：
	- 中文：获取任务执行状态
	- English：get the task run status

- input body:

``` json
{
    "taskId":"1636098724239",
    "projectId":"keen",
    "repoName":"test",
    "fullPath":"/landun1.apk"
}
```

- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|taskId|string|否|无|任务ID| 任务ID|
|projectId|string|是|无|项目ID|projectId|
|repoName|string|是|无|仓库名称|repository name|
|fullPath|string|是|无|文件全路径|the file pull path|

- output:

```
{
    "code": 0,
    "message": null,
    "data": [
    ],
    "traceId": ""
}

```

- output 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误 |0:success, other: failure|
|message|result message|错误消息 |the failure message |
|data | result data | 返回数据 |the record|
|traceId|string|请求跟踪id|the trace id|




