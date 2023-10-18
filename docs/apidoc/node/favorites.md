# 收藏文件夹相关接口

[toc]

## 创建收藏文件夹

- API: POST /repository/api/favorite/create
- API 名称: create_favorite
- 功能说明：

    - 中文：创建收藏文件夹
    - English：create favorite folder
- 请求体

  ```json
  {
  "projectId": "devops",
  "repoName": "custom",
  "path": "/test/a/",
  "type": "USER"
  }
  ```
- 请求字段说明

| 字段 | 类型 | 是否必须 | 默认值 | 说明 | Description      |
| ---| ---- | --------| ----- | --- |------------------| 
| projectId | string | 是 | 无 | 项目名称 | project id       |
| repoName | string | 是 | 无 | 仓库名称 | repo name        |
| path | string | 是 | 无 | 完整路径 | path             |
| type | enum | 是 | USER | [USER , PROJECT] | [USER , PROJECT] |

- 响应体

  ```json
  {
    "code": 0,
    "message": null,
    "data": null,
    "traceId": null
  }
  ```

## 分页查询收藏文件夹

- API: POST /repository/api/favorite/query
- API 名称: favorite_page
- 功能说明：

    - 中文：分页查询收藏文件夹
    - English：list favorite page
- 请求体

  ```json
  {
  "projectId": "",
  "type": "USER",
  "pageNumber": 1,
  "pageSize": 20
  }
  ```
- 请求字段说明

| 字段       | 类型   | 是否必须 | 默认值 | 说明     | Description      |
| ----------| ----- |------| ----- | ------- |------------------|
| type | enum | 是    | USER | [USER , PROJECT] | [USER , PROJECT] |
| projectId | string | 是    | 无     | 项目名称 | project id       |
| pageNumber | int   | 否    | 1     | 当前页   | current page     |
| pageSize  | int   | 否    | 20    | 分页大小 | page size        |


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
      "records": [
        {
          "id" : "64e4634342ad44416be9f675",
          "projectId" : "blueking",
          "repoName" : "generic-local",
          "path" : "/123",
          "userId" : "admin",
          "createdDate" : "2020-07-27T16:02:31.394",
          "type": "USER"
        }
      ]
    },
    "traceId": null
  }
  ```
- record字段说明

| 字段        | 类型   | 说明     | Description         |
| --------- |--------|----------| ------------------- |
| id        | string | id     | id                  |
| projectId | string | 节点所属项目 | node project id     |
| repoName  | string | 节点所属仓库 | node repository name |
| path      | string | 目录完整路径 | node path           |
| userId    | string | 创建者    | userId              |
| createdDate | string | 创建时间   | create time         |
| type | string | 类型     | data type           |

## 删除收藏

- API: DELETE /repository/api/favorite/delete/{id}
- API 名称: delete favorite
- 功能说明：

    - 中文：删除收藏
    - English：delete favorite
- 请求体
  此接口请求体为空
- 请求字段说明

| 字段 | 类型   | 是否必须 | 默认值 | 说明 | Description |
| --- | ----- | ------ | ----- | --- | ------------ |
| id  | string | 是     | 无     | id  | id         |

- 响应体

  ```json
  {
    "code": 0,
    "message": null,
    "data": null,
    "traceId": null
  }
  ```
