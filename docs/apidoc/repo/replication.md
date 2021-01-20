## Replication仓库同步接口

[toc]

### replication说明
- 现有方案需要把`operate_log` collection设置为Capped Collection
```bash
db.operate_log.isCapped()
db.runCommand({"convertToCapped":"operate_log",size:10000})
```
### 测试集群连通状态

- API: GET /replication/task/connect/test
- API 名称: test_connect
- 功能说明：
	- 中文：测试集群连通状态
	- English：test connect status

- 请求体:

  ``` json
  {
      "url":"http://bkrepo.com/replication",
      "username":"admin",
      "password":"password"
  }
  ```

- 请求字段说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |url|string|是|无|同步请求地址|request url|
  |username|string|否|无|用户名|user name|
  |password|string|否|无|用户密码|user password|

- 响应体:

  ```
  {
    "code": 0,
    "message": null,
    "data": null,
    "traceId": null
  }
  ```

### 创建集群同步任务

- API: POST  /replication/task/create
- API 名称: create_replication_task
- 功能说明：
	- 中文：创建集群同步任务
	- English：create replication task
- 请求体:

  ``` json
  {
      "type":"INCREMENTAL",
      "includeAllProject":false,
      "localProjectId":"ops",
      "localRepoName":"test",
      "remoteProjectId":"ops",
      "remoteRepoName":"test",
      "setting":{
          "includeMetadata":true,
          "includePermission":true,
          "conflictStrategy":"SKIP",
          "remoteClusterInfo":{
              "url":"http://bkrepo.com/replication",
              "username":"admin",
              "password":"password"
          },
          "executionPlan":{
              "executeImmediately":true
          }
      }
  }
  ```

- 请求字段说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |type|enum|是|无|[INCREMENTAL,FULL]|replication type|
  |includeAllProject|bool|是|无|是否包含所有项目|do include all project|
  |localProjectId|string|是|无|本地项目ID|the local project Id|
  |localRepoName|string|是|无|本地仓库名|the local repo name|
  |remoteProjectId|string|是|无|远端项目ID|the remote project id|
  |remoteRepoName|string|是|无|远端仓库名|the remote repo name|

- 响应体

  ```
  {
    "code": 0,
    "message": null,
    "data": null,
    "traceId": null
  }
  ```
