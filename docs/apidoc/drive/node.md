# Drive 节点操作接口

[toc]

## 批量变更节点

- API: POST /drive/node/batch/{projectId}/{repoName}
- API 名称: drive_node_batch
- 功能说明:
  - 中文: 批量执行节点创建、更新、删除、重命名、创建硬链接
  - English: batch create/update/delete/rename/hard-link nodes
- 请求体
  ```json
  [
    {
      "op": "create",
      "node": {
        "ino": 1001,
        "parent": 1,
        "name": "a.txt",
        "size": 12,
        "mode": 33188,
        "type": 1,
        "nlink": 1,
        "uid": 0,
        "gid": 0,
        "rdev": 0,
        "flags": 0
      }
    },
    {
      "op": "update",
      "node": {
        "ino": 1001,
        "size": 1024,
        "ifMatch": "2026-03-12T09:00:00"
      }
    },
    {
      "op": "delete",
      "node": {
        "ino": 1002,
        "ifMatch": "2026-03-12T09:10:00"
      }
    },
    {
      "op": "rename",
      "node": {
        "ino": 1003,
        "parent": 1,
        "name": "c-renamed.txt",
        "mtime": 1741771200000000000,
        "ctime": 1741771200000000000,
        "atime": 1741771200000000000,
        "ifMatch": "2026-03-12T09:10:00",
        "overwrite": true
      }
    }
  ]
  ```
- 请求字段说明

  | 字段        | 类型     | 是否必须 | 默认值 | 说明                                                  | Description    |
  | --------- | ------ | ---- | --- | --------------------------------------------------- | -------------- |
  | projectId | string | 是    | 无   | 项目名称                                                | project name   |
  | repoName  | string | 是    | 无   | 仓库名称                                                | repo name      |
  | op        | string | 是    | 无   | 操作类型: `create`/`update`/`delete`/`rename` | operation type |
  | node      | object | 是    | 无   | 操作对象                                                | operation node |

- `node` 字段说明

  | 字段            | 类型      | 是否必须 | 默认值   | 说明                                                                | Description            |
  | ------------- | ------- | ---- | ----- | ----------------------------------------------------------------- | ---------------------- |
  | ino           | long    | 否    | 无     | inode（create/update/delete/rename 必填）                   | inode                  |
  | targetIno     | long    | 否    | 无     | 硬链接目标 inode                                                       | hard-link target inode |
  | parent        | long    | 否    | 无     | 父目录 inode（create 常用，rename 时表示目标父目录）                                              | parent inode           |
  | name          | string  | 否    | 无     | 文件名（rename 时表示目标名称）                                                               | node name              |
  | size          | long    | 否    | 无     | 文件大小                                                              | file size              |
  | mode          | int     | 否    | 无     | 文件模式                                                              | file mode              |
  | type          | int     | 否    | 无     | 文件类型: 1 文件, 2 目录, 3 软链接                                           | file type              |
  | nlink         | int     | 否    | 无     | 硬链接数                                                              | hard link count        |
  | uid           | int     | 否    | 无     | 用户 ID                                                             | user id                |
  | gid           | int     | 否    | 无     | 组 ID                                                              | group id               |
  | rdev          | int     | 否    | 无     | 设备 ID                                                             | device id              |
  | flags         | int     | 否    | 无     | 文件标志                                                              | file flags             |
  | symlinkTarget | string  | 否    | 无     | 软链接目标路径                                                           | symlink target         |
  | mtime         | long    | 否    | 无     | 修改时间（纳秒时间戳），create 时不传则使用当前时间                                     | modify time (nanos)    |
  | ctime         | long    | 否    | 无     | 属性变更时间（纳秒时间戳），create 时不传则使用当前时间                                   | change time (nanos)    |
  | atime         | long    | 否    | 无     | 访问时间（纳秒时间戳），create 时不传则使用当前时间                                     | access time (nanos)    |
  | ifMatch       | string  | 否    | 无     | 前置条件检查，服务端的 lastModifiedDate 与该值不匹配时返回 PRECONDITION_FAILED，用于并发控制；不传则跳过检查 | precondition check     |
  | overwrite     | boolean | 否    | false  | 覆盖同名目标（仅 rename 生效）；为 true 时允许覆盖目标位置同名节点                       | overwrite destination (rename only) |

- 响应体
  ```json
  {
    "code": 0,
    "message": null,
    "data": [
      {
        "op": "create",
        "ino": 1001,
        "nodeId": "67d074a13d19772f4b813f90",
        "node": {
          "id": "67d074a13d19772f4b813f90",
          "createdBy": "admin",
          "createdDate": "2026-03-12T09:00:00",
          "lastModifiedBy": "admin",
          "lastModifiedDate": "2026-03-12T09:00:00",
          "mtime": 1741770000000000000,
          "ctime": 1741770000000000000,
          "atime": 1741770000000000000,
          "projectId": "demo",
          "repoName": "drive-local",
          "ino": 1001,
          "targetIno": null,
          "realIno": 1001,
          "parent": 2,
          "name": "a.txt",
          "size": 12,
          "mode": 33188,
          "type": 1,
          "nlink": 1,
          "uid": 0,
          "gid": 0,
          "rdev": 0,
          "flags": 0,
          "symlinkTarget": null,
          "deleted": null
        },
        "code": 0,
        "message": null
      },
      {
        "op": "update",
        "ino": 1002,
        "nodeId": "67d074a13d19772f4b813f91",
        "node": {
          "id": "67d074a13d19772f4b813f91",
          "createdBy": "admin",
          "createdDate": "2026-03-12T08:00:00",
          "lastModifiedBy": "admin",
          "lastModifiedDate": "2026-03-12T09:00:00",
          "mtime": 1741770000000000000,
          "ctime": 1741770000000000000,
          "atime": 1741770000000000000,
          "projectId": "demo",
          "repoName": "drive-local",
          "ino": 1002,
          "targetIno": null,
          "realIno": 1002,
          "parent": 1,
          "name": "b.txt",
          "size": 1024,
          "mode": 33188,
          "type": 1,
          "nlink": 1,
          "uid": 0,
          "gid": 0,
          "rdev": 0,
          "flags": 0,
          "symlinkTarget": null,
          "deleted": null
        },
        "code": 0,
        "message": null
      },
      {
        "op": "delete",
        "ino": 1005,
        "nodeId": "67d074a13d19772f4b813f92",
        "node": null,
        "code": 0,
        "message": null
      },
      {
        "op": "rename",
        "ino": 1003,
        "nodeId": "67d074a13d19772f4b813f93",
        "node": {
          "id": "67d074a13d19772f4b813f93",
          "createdBy": "admin",
          "createdDate": "2026-03-12T08:00:00",
          "lastModifiedBy": "admin",
          "lastModifiedDate": "2026-03-12T09:20:00",
          "mtime": 1741770000000000000,
          "ctime": 1741770000000000000,
          "atime": 1741770000000000000,
          "projectId": "demo",
          "repoName": "drive-local",
          "ino": 1003,
          "targetIno": null,
          "realIno": 1003,
          "parent": 1,
          "name": "c-renamed.txt",
          "size": 12,
          "mode": 33188,
          "type": 1,
          "nlink": 1,
          "uid": 0,
          "gid": 0,
          "rdev": 0,
          "flags": 0,
          "symlinkTarget": null,
          "deleted": null
        },
        "code": 0,
        "message": null
      }
    ],
    "traceId": null
  }
  ```
- data 字段说明

  | 字段      | 类型     | 说明                                                  | Description                                    |
  | ------- | ------ | --------------------------------------------------- | ---------------------------------------------- |
  | op      | string | 操作类型: `create`/`update`/`delete`/`rename` | operation type                                 |
  | ino     | long   | 本次操作节点的 ino（操作失败时可能为空）                              | node ino                                       |
  | nodeId  | string | 本次操作节点 ID（操作失败时可能为空）                                | node id                                        |
  | node    | object | 节点详细信息，仅非删除操作存在该字段，删除操作为 null，字段同 DriveNode       | node detail, only exists for non-delete ops |
  | code    | int    | 操作结果码，0 表示成功                                        | result code                                    |
  | message | string | 失败消息                                                | failure message                                |

- 各操作返回的 code 说明
  > 每个操作独立执行，单个操作失败不影响其他操作。code 为 0 表示操作成功，非 0 表示失败。
  **通用错误码**（所有操作均可能返回）

  | code   | 错误码枚举             | 说明   | 触发场景                      |
  | ------ | ----------------- | ---- | ------------------------- |
  | 0      | SUCCESS           | 操作成功 | 操作执行成功                    |
  | 250102 | SYSTEM_ERROR      | 系统异常 | 服务端发生未预期的异常               |
  | 250104 | PARAMETER_INVALID | 参数非法 | 请求参数校验不通过，如必填字段缺失、字段值不合法等 |

  **create 操作**

  | code   | 错误码枚举          | 说明    | 触发场景              |
  | ------ | -------------- | ----- | ----------------- |
  | 251010 | NODE_NOT_FOUND | 节点不存在 | 指定的父目录（parent）不存在 |
  | 251012 | NODE_EXISTED   | 节点已存在 | 同一父目录下已存在同名节点     |

  **update 操作**

  | code   | 错误码枚举               | 说明     | 触发场景                                                    |
  | ------ | ------------------- | ------ | ------------------------------------------------------- |
  | 251010 | NODE_NOT_FOUND      | 节点不存在  | 指定的 ino 对应的节点不存在                                        |
  | 251012 | NODE_EXISTED        | 节点已存在  | 更新 parent 或 name 后，目标位置已存在同名节点                          |
  | 250112 | PRECONDITION_FAILED | 前置条件失败 | 传入了 ifMatch 且与服务端 lastModifiedDate 不一致 |

  **delete 操作**

  | code   | 错误码枚举               | 说明     | 触发场景                                                    |
  | ------ | ------------------- | ------ | ------------------------------------------------------- |
  | 250111 | METHOD_NOT_ALLOWED  | 操作不允许  | 尝试删除根节点                                                 |
  | 251001 | DIRECTORY_NOT_EMPTY | 目录非空   | 尝试删除的目录下仍存在子节点                                          |
  | 251010 | NODE_NOT_FOUND      | 节点不存在  | 指定的 ino 对应的节点不存在                                        |
  | 250112 | PRECONDITION_FAILED | 前置条件失败 | 传入了 ifMatch 且与服务端 lastModifiedDate 不一致 |

  **rename 操作**

  | code   | 错误码枚举          | 说明     | 触发场景                     |
  | ------ | ------------------ | -------- | ---------------------------- |
  | 251010 | NODE_NOT_FOUND     | 节点不存在 | 指定的 ino 或目标父目录不存在 |
  | 251012 | NODE_EXISTED       | 节点已存在 | 目标位置已存在同名节点        |
  | 250112 | PRECONDITION_FAILED | 前置条件失败 | 传入了 ifMatch 且与服务端 lastModifiedDate 不一致 |
  | 250104 | PARAMETER_INVALID  | 参数非法   | 目标名称非法或源目标相同      |


## 游标查询目录下节点

- API: GET /drive/node/page/{projectId}/{repoName}
- API 名称: drive_list_nodes_page
- 功能说明:
  - 中文: 使用游标查询指定父目录下的节点
  - English: list nodes by parent with cursor
- 请求体
此接口请求体为空
- 请求字段说明

  | 字段                  | 类型      | 是否必须 | 默认值   | 说明                                                  | Description           |
  | ------------------- | ------- | ---- | ----- | --------------------------------------------------- | --------------------- |
  | projectId           | string  | 是    | 无     | 项目名称                                                | project name          |
  | repoName            | string  | 是    | 无     | 仓库名称                                                | repo name             |
  | parent              | long    | 否    | 无     | 父目录 inode，不传表示查询根层                                  | parent inode          |
  | pageSize            | int     | 否    | 20    | 每次查询条数                                              | page size             |
  | snapSeq             | long    | 否    | 无     | 快照序号，不传则查询当前视图                                      | snapshot sequence     |
  | lastName            | string  | 否    | 无     | 上一页最后一条记录的 `name`，首次查询不传                            | last record name      |
  | lastId              | string  | 否    | 无     | 上一页最后一条记录的 `id`，与 `lastName` 一起用于续页                 | last record id        |

- 排序与续页规则
  - 服务端固定按 `name ASC, id ASC` 返回
  - 首次查询不传 `lastName/lastId`
  - 查询下一页时，使用上一页最后一条记录的 `name/id` 作为 `lastName/lastId`

- 响应体
  ```json
  {
    "code": 0,
    "message": null,
    "data": {
      "pageSize": 20,
      "hasMore": false,
      "records": [
        {
          "id": "67d074a13d19772f4b813f90",
          "createdBy": "admin",
          "createdDate": "2026-03-12T09:00:00",
          "lastModifiedBy": "admin",
          "lastModifiedDate": "2026-03-12T09:00:00",
          "mtime": 1741770000000000000,
          "ctime": 1741770000000000000,
          "atime": 1741770000000000000,
          "projectId": "demo",
          "repoName": "drive-local",
          "ino": 1001,
          "targetIno": null,
          "realIno": 1001,
          "parent": 1,
          "name": "a.txt",
          "size": 1024,
          "mode": 33188,
          "type": 1,
          "nlink": 1,
          "uid": 0,
          "gid": 0,
          "rdev": 0,
          "flags": 0,
          "symlinkTarget": null
        }
      ]
    },
    "traceId": null
  }
  ```

## 游标查询增量变更节点

- API: GET /drive/node/modified/page/{projectId}/{repoName}
- API 名称: drive_list_modified_nodes_page
- 功能说明:
  - 中文: 按最后修改时间使用游标查询增量变更节点
  - English: list modified nodes with cursor
- 请求体
此接口请求体为空
- 请求字段说明

  | 字段                    | 类型      | 是否必须 | 默认值   | 说明                                                            | Description                    |
  | --------------------- | ------- | ---- | ----- | ------------------------------------------------------------- | ------------------------------ |
  | projectId             | string  | 是    | 无     | 项目名称                                                          | project name                   |
  | repoName              | string  | 是    | 无     | 仓库名称                                                          | repo name                      |
  | pageSize              | int     | 否    | 20    | 每次查询条数                                                        | page size                      |
  | lastModifiedDate      | string  | 是    | 无     | 上一条已消费记录的 `lastModifiedDate`，ISO_DATE_TIME 格式                | last modified cursor           |
  | lastId                | string  | 是    | 无     | 上一条已消费记录的 `id`，与 `lastModifiedDate` 一起用于续页                  | last record id                 |

- 排序与续页规则
  - 服务端固定按 `lastModifiedDate ASC, id ASC` 返回
  - `lastModifiedDate/lastId` 必须成对传递
  - 首次查询请传入“同步起点时间 + 最小 id（可为空字符串）”
  - 查询下一页时，传入上一页最后一条记录的 `lastModifiedDate/id`

- 响应体
与“游标查询目录下节点”一致

## DriveNode 返回字段说明

| 字段        | 类型   | 说明                                                                 | Description                          |
|-----------|------|--------------------------------------------------------------------|--------------------------------------|
| ino       | long | 节点 inode；硬链接场景下可能为占位 inode                                         | node inode (may be placeholder for hard-link) |
| targetIno | long | 硬链接目标 inode；非硬链接节点为 null                                           | hard-link target inode, null for non hard-link |
| realIno   | long | 实际用于访问内容的 inode；普通节点等于 ino，硬链接节点等于 targetIno                        | effective inode for data access      |

## DriveNode 文件类型枚举


| 枚举值 | 说明   | Description   |
| --- | ---- | ------------- |
| 1   | 普通文件 | regular file  |
| 2   | 目录   | directory     |
| 3   | 软链接  | symbolic link |


