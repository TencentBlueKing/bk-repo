# Drive 快照操作接口

[toc]

## 创建快照

- API: POST /drive/snapshot/create/{projectId}/{repoName}
- API 名称: drive_create_snapshot
- 功能说明:
  - 中文: 创建 Drive 文件系统快照
  - English: create a drive snapshot
- 请求体
  ```json
  {
    "name": "snapshot-v1",
    "description": "第一个快照"
  }
  ```
- 请求字段说明

  | 字段          | 类型     | 是否必须 | 默认值 | 说明     | Description      |
  | ----------- | ------ | ---- | --- | ------ | ---------------- |
  | projectId   | string | 是    | 无   | 项目名称   | project name     |
  | repoName    | string | 是    | 无   | 仓库名称   | repo name        |
  | name        | string | 是    | 无   | 快照名称   | snapshot name    |
  | description | string | 否    | 无   | 快照描述信息 | snapshot description |

- 响应体
  ```json
  {
    "code": 0,
    "message": null,
    "data": {
      "id": "67d074a13d19772f4b813f90",
      "createdBy": "admin",
      "createdDate": "2026-03-12T11:00:00",
      "lastModifiedBy": "admin",
      "lastModifiedDate": "2026-03-12T11:00:00",
      "projectId": "demo",
      "repoName": "drive-local",
      "name": "snapshot-v1",
      "description": "第一个快照",
      "snapSeq": 1
    },
    "traceId": null
  }
  ```
- data 字段说明

  | 字段               | 类型     | 说明        | Description        |
  | ---------------- | ------ | --------- | ------------------ |
  | id               | string | 快照 ID     | snapshot id        |
  | createdBy        | string | 创建者       | created by         |
  | createdDate      | string | 创建时间      | created date       |
  | lastModifiedBy   | string | 最后修改者     | last modified by   |
  | lastModifiedDate | string | 最后修改时间    | last modified date |
  | projectId        | string | 项目名称      | project name       |
  | repoName         | string | 仓库名称      | repo name          |
  | name             | string | 快照名称      | snapshot name      |
  | description      | string | 快照描述信息    | snapshot description |
  | snapSeq          | long   | 快照序列号     | snapshot sequence  |

## 分页查询快照

- API: GET /drive/snapshot/page/{projectId}/{repoName}
- API 名称: drive_list_snapshots_page
- 功能说明:
  - 中文: 分页查询 Drive 快照列表
  - English: list drive snapshots in page
- 请求体
此接口请求体为空
- 请求字段说明

  | 字段        | 类型     | 是否必须 | 默认值 | 说明       | Description  |
  | --------- | ------ | ---- | --- | -------- | ------------ |
  | projectId | string | 是    | 无   | 项目名称     | project name |
  | repoName  | string | 是    | 无   | 仓库名称     | repo name    |
  | pageNum   | int    | 否    | 0   | 页码，从 0 开始 | page number  |
  | pageSize  | int    | 否    | 20  | 每页条数     | page size    |

- 响应体
  ```json
  {
    "code": 0,
    "message": null,
    "data": {
      "pageNumber": 0,
      "pageSize": 20,
      "totalRecords": 2,
      "totalPages": 1,
      "records": [
        {
          "id": "67d074a13d19772f4b813f90",
          "createdBy": "admin",
          "createdDate": "2026-03-12T11:00:00",
          "lastModifiedBy": "admin",
          "lastModifiedDate": "2026-03-12T11:00:00",
          "projectId": "demo",
          "repoName": "drive-local",
          "name": "snapshot-v1",
          "description": "第一个快照",
          "snapSeq": 1
        },
        {
          "id": "67d074a13d19772f4b813f91",
          "createdBy": "admin",
          "createdDate": "2026-03-13T09:00:00",
          "lastModifiedBy": "admin",
          "lastModifiedDate": "2026-03-13T09:00:00",
          "projectId": "demo",
          "repoName": "drive-local",
          "name": "snapshot-v2",
          "description": null,
          "snapSeq": 2
        }
      ]
    },
    "traceId": null
  }
  ```
- records 字段说明与"创建快照"的 data 字段一致

## 更新快照

- API: PUT /drive/snapshot/{projectId}/{repoName}/{id}
- API 名称: drive_update_snapshot
- 功能说明:
  - 中文: 更新 Drive 快照名称或描述
  - English: update drive snapshot name or description
- 请求体
  ```json
  {
    "name": "snapshot-v1-renamed",
    "description": "更新后的描述"
  }
  ```
- 请求字段说明

  | 字段          | 类型     | 是否必须 | 默认值 | 说明       | Description          |
  | ----------- | ------ | ---- | --- | -------- | -------------------- |
  | projectId   | string | 是    | 无   | 项目名称     | project name         |
  | repoName    | string | 是    | 无   | 仓库名称     | repo name            |
  | id          | string | 是    | 无   | 快照 ID    | snapshot id          |
  | name        | string | 否    | 无   | 新的快照名称   | new snapshot name    |
  | description | string | 否    | 无   | 新的快照描述信息 | new snapshot description |

- 响应体
  ```json
  {
    "code": 0,
    "message": null,
    "data": {
      "id": "67d074a13d19772f4b813f90",
      "createdBy": "admin",
      "createdDate": "2026-03-12T11:00:00",
      "lastModifiedBy": "admin",
      "lastModifiedDate": "2026-03-14T10:00:00",
      "projectId": "demo",
      "repoName": "drive-local",
      "name": "snapshot-v1-renamed",
      "description": "更新后的描述",
      "snapSeq": 1
    },
    "traceId": null
  }
  ```
- data 字段说明与"创建快照"的 data 字段一致

## 删除快照

- API: DELETE /drive/snapshot/{projectId}/{repoName}/{id}
- API 名称: drive_delete_snapshot
- 功能说明:
  - 中文: 删除 Drive 快照（软删除）
  - English: delete a drive snapshot (soft delete)
- 请求体
此接口请求体为空
- 请求字段说明

  | 字段        | 类型     | 是否必须 | 默认值 | 说明    | Description  |
  | --------- | ------ | ---- | --- | ----- | ------------ |
  | projectId | string | 是    | 无   | 项目名称  | project name |
  | repoName  | string | 是    | 无   | 仓库名称  | repo name    |
  | id        | string | 是    | 无   | 快照 ID | snapshot id  |

- 响应体
  ```json
  {
    "code": 0,
    "message": null,
    "data": null,
    "traceId": null
  }
  ```
