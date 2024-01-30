# 扫描接口

[toc]

## 创建扫描任务

- API: POST /analyst/api/scan
- API 名称: scan
- 功能说明：
    - 中文：发起扫描
    - English：scan
- 请求体

```json
{
  "planId": "629f415a2511164b6e454145",
  "rule": {
    "relation": "AND",
    "rules": [
      {
        "field": "projectId",
        "value": "testProjectId",
        "operation": "EQ"
      },
      {
        "field": "repoName",
        "value": "maven-local",
        "operation": "EQ"
      },
      {
        "field": "fullPath",
        "value": "/",
        "operation": "PREFIX"
      }
    ]
  },
  "metadata": [
    {
      "key": "buildNumber",
      "value": "32"
    }
  ]
}
```

- 请求字段说明

| 字段       | 类型      | 是否必须 | 默认值   | 说明                                                         | Description        |
|----------|---------|------|-------|------------------------------------------------------------|--------------------|
| scanner  | string  | 否    | 无     | 要获取的报告使用的扫描器名称，扫描器名称在扫描器注册到制品库后确定，需要联系制品库管理员确认             | scanner name       |
| planId   | string  | 否    | 无     | 使用的扫描方案id,与scanner至少一个字段存在值                                | plan id            |
| force    | boolean | 否    | false | 是否强制扫描，为true时即使文件已扫描过也会再次执行扫描                              | force scan         |
| rule     | object  | 否    | 无     | 要扫描的文件匹配规则，参考[自定义搜索接口公共说明](../common/search.md?id=自定义搜索协议) | file match rule    |
| metadata | array   | 否    | 无     | 为扫描任务附加元数据，用于标识扫描任务                                        | scan task metadata |

- 响应体

```json
{
  "code": 0,
  "message": null,
  "data": {
    "name": "test",
    "taskId": "62a1ae99573ec051504778ee",
    "projectId": "testProjectId",
    "createdBy": "admin",
    "lastModifiedDateTime": "2022-06-09T16:26:01.437",
    "triggerDateTime": "2022-06-09T16:26:01.437",
    "startDateTime": null,
    "finishedDateTime": null,
    "triggerType": "MANUAL",
    "status": "PENDING",
    "scanPlan": {
      "id": "629f415a2511164b6e454145",
      "projectId": "testProjectId",
      "name": "DEFAULT_GENERIC",
      "type": "GENERIC",
      "scanner": "default",
      "description": "",
      "scanOnNewArtifact": false,
      "repoNames": [],
      "rule": {
        "rules": [
          {
            "field": "projectId",
            "value": "testProjectId",
            "operation": "EQ"
          }
        ],
        "relation": "AND"
      },
      "scanQuality": {},
      "createdBy": "system",
      "createdDate": "2022-06-07T20:15:22.886",
      "lastModifiedBy": "system",
      "lastModifiedDate": "2022-06-07T20:15:22.886"
    },
    "rule": {
      "rules": [
        {
          "field": "projectId",
          "value": "testProjectId",
          "operation": "EQ"
        },
        {
          "field": "repoName",
          "value": "maven-local",
          "operation": "EQ"
        },
        {
          "field": "fullPath",
          "value": "/jar/spring-core-5.3.15.jar",
          "operation": "PREFIX"
        }
      ],
      "relation": "AND"
    },
    "total": 0,
    "scanning": 0,
    "failed": 0,
    "scanned": 0,
    "passed": 0,
    "scanner": "default",
    "scannerType": "standard",
    "scannerVersion": "1::1",
    "scanResultOverview": {},
    "force": true,
    "metadata": [
      {
        "key": "buildNumber",
        "value": "32"
      }
    ]
  },
  "traceId": ""
}
```

- data字段说明

| 字段                 | 类型      | 说明                                                         | Description            |
|--------------------|---------|------------------------------------------------------------|------------------------|
| name               | string  | 任务名                                                        | task name              |
| taskId             | string  | 任务id                                                       | task id                |
| projectId          | string  | 所属项目id                                                     | project id             |
| createdBy          | string  | 任务创建者                                                      | task creator           |
| triggerDatetime    | string  | 触发任务的时间                                                    | task trigger time      |
| startDateTime      | string  | 任务开始执行时间                                                   | task started time      |
| finishedDateTime   | string  | 任务执行结束时间                                                   | task finished time     |
| triggerType        | string  | 触发类型,MANUAL,PIPELINE,ON_NEW_ARTIFACT                       | trigger type           |
| status             | string  | 任务状态                                                       | task status            |
| scanPlan           | object  | 使用的扫描方案                                                    | scan plan              |
| rule               | object  | 要扫描的文件匹配规则，参考[自定义搜索接口公共说明](../common/search.md?id=自定义搜索协议) | file match rule        |
| total              | number  | 总扫描文件数                                                     | total scan file count  |
| failed             | number  | 扫描失败文件数                                                    | scan failed file count |
| scanned            | number  | 已扫描文件数                                                     | scanned file count     |
| passed             | number  | 通过质量规则的文件数                                                 | passed file count      |
| scanner            | string  | 使用的扫描器明                                                    | scanner name           |
| scannerType        | string  | 扫描器类型                                                      | scanner type           |
| scannerVersion     | string  | 扫描器版本                                                      | scanner version        |
| scanResultOverview | array   | 扫描结果预览                                                     | scan result overview   |
| force              | boolean | 是否为强制扫描                                                    | force scan             |
| metadata           | array   | 扫描任务元数据                                                    | scan task metadata     |

扫描结果预览字段参考[获取扫描报告预览](./report.md?id=获取扫描报告预览)


## 通过流水线创建扫描任务

- API: POST /analyst/api/scan/pipeline
- API 名称: pipeline scan
- 功能说明：
  - 中文：通过流水线发起扫描
  - English：pipeline scan
- 请求体

```json
{
  "projectId": "testProjectId",
  "pid": "p-test",
  "bid": "b-test",
  "buildNo": "12",
  "pipelineName": "test",
  "pluginName": "test",
  "rule": {
    "relation": "AND",
    "rules": [
      {
        "field": "projectId",
        "value": "testProjectId",
        "operation": "EQ"
      },
      {
        "field": "repoName",
        "value": "pipeline",
        "operation": "EQ"
      },
      {
        "field": "fullPath",
        "value": "/p-test/b-test/spring-core-5.3.15.jar",
        "operation": "PREFIX"
      }
    ],
    "weworkBotUrl": "http://localhost",
    "chatIds": "chatId1|chatId2"
  }
}
```

- 请求字段说明

| 字段           | 类型     | 是否必须 | 默认值     | 说明                                                         | Description            |
|--------------|--------|------|---------|------------------------------------------------------------|------------------------|
| projectId    | string | 是    | 无       | 所属项目                                                       | project id             |
| pid          | string | 否    | 无       | 流水线id                                                      | pipeline id            |
| bid          | string | 否    | 无       | 构建id                                                       | build id               |
| buildNo      | string | 否    | 无       | 构建号                                                        | build number           |
| pipelineName | string | 否    | 无       | 流水线名                                                       | pipeline name          |
| pluginName   | string | 否    | 无       | 插件名                                                        | plugin name            |
| planId       | string | 否    | 无       | 使用的扫描方案id                                                  | plan id                |
| planType     | string | 否    | GENERIC | 扫描方案类型                                                     | plan type              |
| scanner      | string | 否    | 无       | 扫描方案使用的扫描器                                                 | scanner                |
| rule         | object | 是    | 无       | 要扫描的文件匹配规则，参考[自定义搜索接口公共说明](../common/search.md?id=自定义搜索协议) | file match rule        |
| weworkBotUrl | string | 否    | 无       | 企业微信机器人webhook地址                                           | wework bot webhook url |
| chatIds      | string | 否    | 无       | 企业微信机器人会话id，多个id用"&vert;"分隔                                | wework bot webhook url |

- 响应体

```json
{
  "code": 0,
  "message": null,
  "data": {},
  "traceId": ""
}
```

- data字段说明

响应体参考[创建扫描任务](./scan.md?id=创建扫描任务)响应体

## 停止扫描

- API: POST /analyst/api/scan/{projectId}/stop
- API 名称: stop_scan
- 功能说明：
  - 中文：停止扫描
  - English：stop scan
- 请求体 此接口请求体为空

- 请求字段说明

| 字段        | 类型     | 是否必须 | 默认值 | 说明             | Description          |
|-----------|--------|------|-----|----------------|----------------------|
| projectId | string | 是    | 无   | 项目id           | project id           |
| recordId  | string | 是    | 无   | 属于扫描方案的扫描子任务id | plan scan subtask id |

## 停止扫描任务

- API: POST /analyst/api/scan/{projectId}/tasks/{taskId}/stop
- API 名称: stop_scan_task
- 功能说明：
  - 中文：停止扫描任务
  - English：stop scan task
- 请求体 此接口请求体为空

- 请求字段说明

| 字段        | 类型     | 是否必须 | 默认值 | 说明   | Description  |
|-----------|--------|------|-----|------|--------------|
| projectId | string | 是    | 无   | 项目id | project id   |
| taskId    | string | 是    | 无   | 任务id | scan task id |

- 响应体

```json
{
  "code": 0,
  "message": null,
  "data": true,
  "traceId": ""
}
```


## 获取扫描任务

- API: GET /analyst/api/scan/tasks/{taskId}
- API 名称: get_task
- 功能说明：
    - 中文：获取扫描任务
    - English：get scan task
- 请求体 此接口请求体为空

- 请求字段说明

| 字段     | 类型     | 是否必须 | 默认值 | 说明   | Description |
|--------|--------|------|-----|------|-------------|
| taskId | string | 是    | 无   | 任务id | task id     |

- 响应体

```json
{
  "code": 0,
  "message": null,
  "data": {},
  "traceId": ""
} 
```

- data字段说明

响应体参考[创建扫描任务](./scan.md?id=创建扫描任务)响应体

## 分页获取扫描任务

- API: GET /analyst/api/scan/tasks
- API 名称: get_tasks
- 功能说明：
  - 中文：分页获取扫描任务
  - English：get scan tasks
- 请求体 此接口请求体为空

- 请求字段说明

| 字段          | 类型     | 是否必须 | 默认值 | 说明                                                                   | Description  |
|-------------|--------|------|-----|----------------------------------------------------------------------|--------------|
| pageSize    | number | 否    | 20  | 分页大小                                                                 | page size    |
| pageNumber  | number | 否    | 1   | 分页页码                                                                 | page number  |
| projectId   | string | 是    | 无   | 项目id                                                                 | projectId    |
| planId      | string | 否    | 无   | 扫描方案id                                                               | plan id      |
| triggerType | string | 否    | 无   | 触发方式MANUAL,PIPELINE,ON_NEW_ARTIFACT                                  | trigger type |
| after       | number | 否    | 无   | 在这个时间戳之后创建的任务                                                        | after        |
| before      | number | 否    | 无   | 在这个时间戳之前创建的任务                                                        | before       |
| scanner     | string | 否    | 无   | 扫描器名                                                                 | scanner      |
| scannerType | string | 否    | 无   | 扫描器类型                                                                | scanner type |
| status      | string | 否    | 无   | 扫描状态,PENDING,SCANNING_SUBMITTING,SCANNING_SUBMITTED,STOPPED,FINISHED | scan status  |


- 响应体

```json
{
    "code": 0,
    "message": null,
    "data": {
        "pageNumber": 1,
        "pageSize": 20,
        "totalRecords": 1,
        "totalPages": 1,
        "records": [],
        "count": 1,
        "page": 1
    },
    "traceId": ""
}
```
- data字段说明

响应体参考[分页接口响应格式](../common/common.md?id=统一分页接口响应格式)

响应体参考[创建扫描任务](./scan.md?id=创建扫描任务)响应体

## 获取扫描子任务

- API: GET /analyst/api/scan/tasks/{taskId}/subtasks/{subtaskId}
- API 名称: get_subtask
- 功能说明：
  - 中文：获取扫描子任务
  - English：get scan subtask
- 请求体 此接口请求体为空

- 请求字段说明

| 字段        | 类型     | 是否必须 | 默认值 | 说明    | Description |
|-----------|--------|------|-----|-------|-------------|
| taskId    | string | 是    | 无   | 任务id  | task id     |
| subtaskId | string | 是    | 无   | 子任务id | subtask id  |

- 响应体

```json
{
  "code": 0,
  "message": null,
  "data": {
    "subTaskId": "6295d720a987ad5a9f7b2766",
    "name": "jackson-datatype-jsr310-2.13.0.jar",
    "packageKey": null,
    "version": null,
    "fullPath": "/jar/jackson-datatype-jsr310-2.13.0.jar",
    "repoType": "GENERIC",
    "repoName": "custom",
    "highestLeakLevel": null,
    "critical": 0,
    "high": 0,
    "medium": 0,
    "low": 0,
    "total": 0,
    "finishTime": "2022-05-31T16:51:44.747",
    "qualityRedLine": true,
    "scanQuality": {},
    "duration": 0
  },
  "traceId": ""
}
```

- data字段说明

| 字段               | 类型      | 说明         | Description              |
|------------------|---------|------------|--------------------------|
| subTaskId        | string  | 子任务id      | subtask id               |
| name             | string  | 制品名        | artifact name            |
| packageKey       | string  | packageKey | packageKey               |
| version          | string  | 制品版本       | artifact version         |
| fullPath         | string  | 制品路径       | artifact full path       |
| repoType         | string  | 仓库类型       | repository type          |
| repoName         | string  | 仓库名        | repository name          |
| highestLeakLevel | string  | 最高漏洞等级     | highest vul level        |
| critical         | number  | 严重漏洞数      | critical level vul count |
| high             | number  | 高危漏洞数      | high level vul count     |
| medium           | number  | 中危漏洞数      | medium level vul count   |
| low              | number  | 低危漏洞数      | low level vul count      |
| total            | number  | 漏洞总数       | total vul count          |
| finishTime       | string  | 扫描完成时间     | scan finished time       |
| qualityRedLine   | boolean | 是否通过质量规则   | is pass quality red line |
| scanQuality      | object  | 扫描时方案的质量规则 | scan quality             |
| duration         | long    | 扫描时长       | scan duration            |


## 分页获取扫描子任务

- API: GET /analyst/api/scan/tasks/{taskId}/subtasks
- API 名称: get_subtasks
- 功能说明：
  - 中文：分页获取扫描子任务
  - English：get scan subtasks
- 请求体 此接口请求体为空

- 请求字段说明

| 字段               | 类型      | 是否必须 | 默认值 | 说明             | Description                |
|------------------|---------|------|-----|----------------|----------------------------|
| taskId           | string  | 是    | 无   | 任务id           | task id                    |
| pageSize         | number  | 否    | 20  | 分页大小           | page size                  |
| pageNumber       | number  | 否    | 1   | 分页页码           | page number                |
| projectId        | string  | 是    | 无   | 项目id           | project id                 |
| id               | string  | 否    | 无   | 扫描方案id         | scan plan id               |
| name             | string  | 否    | 无   | 制品名关键字         | artifact name              |
| highestLeakLevel | string  | 否    | 无   | 制品最高等级漏洞       | highest vul level          |
| repoType         | string  | 否    | 无   | 制品所属仓库类型       | repository type            |
| repoName         | string  | 否    | 无   | 制品所属仓库名        | repository name            |
| status           | string  | 否    | 无   | 制品扫描状态         | subtask status             |
| startTime        | string  | 否    | 无   | 制品扫描任务创建时间(开始) | subtask create time after  |
| endTime          | string  | 否    | 无   | 制品扫描任务创建时间(截止) | subtask create time before |
| qualityRedLine   | boolean | 否    | 无   | 是否通过质量规则       | pass quality red line      |

- 响应体

```json
{
    "code": 0,
    "message": null,
    "data": {
        "pageNumber": 1,
        "pageSize": 20,
        "totalRecords": 1,
        "totalPages": 1,
        "records": [],
        "count": 1,
        "page": 1
    },
    "traceId": ""
}
```
- data字段说明

响应体参考[分页接口响应格式](../common/common.md?id=统一分页接口响应格式)

响应体参考[获取扫描子任务](./scan.md?id=获取扫描子任务)响应体
