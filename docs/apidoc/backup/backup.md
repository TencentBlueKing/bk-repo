# 系统备份以及恢复接口

[toc]

## 创建备份/恢复任务

- API: POST /job/api/job/backup
- API 名称: 创建备份/恢复任务
- 功能说明：
	- 中文：创建备份/恢复任务
	- English：create backup/restore task

- 请求体
  ```json
  新建备份任务
  {
      "name":"test",
      "storeLocation":"F:\\backuptest",
      "content":{
          "commonData": true,
          "compression":true,
          "projects":[{
              "projectRegex":"*"
          }]
      }
  }
  ```
  ```json
  新建恢复任务
  {
      "name":"test1-restore",
      "storeLocation":"F:\\backuptest\\test1\\test1-20241224.171354.zip",
      "type":"DATA_RESTORE",
      "content":{
          "commonData": true,
          "compression":true,
          "projects":[{
              "projectRegex":"*"
          }]
      }
  }
  ```
- 请求字段说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |name|string|是|无|任务名|task name|
  |storeLocation|string|是|无|本地存储地址|store folder|
  |backupSetting|backupSetting|否|有|任务设置| task setting|
  |content|BackupContent|是|无|任务内容| task content|
  |type|string|否|有|任务类型：备份DATA_BACKUP/恢复DATA_RESTORE| task type: DATA_BACKUP/DATA_RESTORE |


- BackupContent对象说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |commonData|bool|否|false|是否备份或者恢复基础数据|backup/restore basic data|
  |compression|bool|否|false|是否压缩最终备份文件目录|compress backup folder|
  |increment|bool|否|null|是否增量备份|incremental backup|
  |incrementDate|string|否|无|如开启增量备份，日期不指定则从任务执行日期前一天开始备份；如指定，则从指定日期开始|if increment is true and incrementDate is not set, then the default incrementDate is current date minus 1. Backup tasks will backup all the data after incrementDate|
  |projects|arrays[ProjectContentInfo]|否|null|具体项目仓库信息|project or repo info|

- ProjectContentInfo对象说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |projectId|string|否|null|指定备份/恢复项目|target projectId|
  |projectRegex|string|否|null|备份/恢复项目正则|regex of target projectid|
  |repoList|arrays|否|null|指定备份/恢复仓库列表|target repos|
  |repoRegex|string|否|null|备份/恢复仓库正则|regex of target repo|
  |excludeProjects|arrays|否|null|排除项目列表|exclude project list|
  |excludeRepos|arrays|否|null|排除仓库列表|exclude repo list|

- setting对象说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |conflictStrategy|enum|是|OVERWRITE|[SKIP,OVERWRITE]|conflict strategy|
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

  ``` json
  {
    "code" : 0,
    "message" : null,
    "data" : "676a8abd60cc914e2a8a26f0",
    "traceId" : null
  }
  ```

- data字段说明
 返回任务id


## 执行任务

- API: POST /api/job/backup/execute/{taskId}
- API 名称: 执行任务
- 功能说明：
  - 中文：执行任务
  - English：execute task

- 请求体
  此接口请求体为空

- 请求字段说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |taskId|string|是|无|任务id|task id|

- 请求头

- 响应头

- 响应体

  ``` json
  {
    "code" : 0,
    "message" : null,
    "data" : null,
    "traceId" : null
  }
  ```

## 分页获取任务列表

- API: GET /job/api/job/backup/tasks?state={state}&pageSize={pageSize}&pageNumber={pageNumber}
- API 名称: 获取任务列表
- 功能说明：
  - 中文：获取任务列表
  - English：get task list

- 请求体
  此接口请求体为空

- 请求字段说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |state|string|否|null|任务执行状态|task state: PENDING/RUNNING/FINISHED|
  |pageNumber|int|否|1|页码|page number|
  |pageSize|int|否|20|页大小|page size|

- 请求头

- 响应头

- 响应体
  ``` json
  {
      "code": 0,
      "message": null,
      "data": {
          "pageNumber": 1,
          "pageSize": 1,
          "totalRecords": 7,
          "totalPages": 7,
          "records": [
              {
                  "id": "675ab19767a452255ba84d8b",
                  "createdBy": "xxxx",
                  "createdDate": "xxxx",
                  "lastModifiedBy": "xxx",
                  "lastModifiedDate": "xxx",
                  "startDate": "xxx",
                  "endDate": "xxx",
                  "state": "FINISHED",
                  "content": {
                      "commonData": false,
                      "compression": false,
                      "projects": [
                          {
                              "projectId": "backuptest",
                              "projectRegex": null,
                              "repoList": null,
                              "repoRegex": null,
                              "excludeProjects": null,
                              "excludeRepos": null
                          }
                      ],
                      "increment": null,
                      "incrementDate": null
                  },
                  "storeLocation": "F:\\backuptest",
                  "backupSetting": {
                      "conflictStrategy": "SKIP",
                      "errorStrategy": "CONTINUE",
                      "executionStrategy": "IMMEDIATELY",
                      "executionPlan": {
                          "executeImmediately": true,
                          "executeTime": null,
                          "cronExpression": null
                      },
                      "spaceCapCheck": true
                  },
                  "type": "DATA_BACKUP"
              }
          ],
          "count": 7,
          "page": 1
      },
      "traceId": "xxx"
  }
  ``` json
