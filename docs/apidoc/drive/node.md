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
  {
    "clientId": "client-1",
    "operations": [
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
  }
  ```
- 请求字段说明

  | 字段        | 类型     | 是否必须 | 默认值 | 说明                                                  | Description    |
  | --------- | ------ | ---- | --- | --------------------------------------------------- | -------------- |
  | projectId | string | 是    | 无   | 项目名称                                                | project name   |
  | repoName  | string | 是    | 无   | 仓库名称                                                | repo name      |
  | clientId  | string | 是    | 无   | 当前执行批量操作的客户端 ID（请求体字段）                             | client id (request body) |
  | operations| array  | 是    | 无   | 批量操作列表                                              | operations list |
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
  - 中文: 按最后修改时间使用游标查询增量变更节点；请求需携带当前客户端 ID，用于服务端判定是否存在其他活跃客户端
  - English: list modified nodes with cursor; client id is required for server-side active-client check
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
  | clientId              | string  | 是    | 无     | 当前请求客户端 ID（query 参数），用于判定是否存在其他活跃客户端                  | current client id (query param) |

- 排序与续页规则
  - 服务端固定按 `lastModifiedDate ASC, id ASC` 返回
  - `lastModifiedDate/lastId` 必须成对传递
  - 首次查询请传入“同步起点时间 + 最小 id（可为空字符串）”
  - 查询下一页时，传入上一页最后一条记录的 `lastModifiedDate/id`
  - 当服务端判定不存在其他活跃客户端时，可能直接返回空列表（`hasMore=false, records=[]`）

- 响应体
与“游标查询目录下节点”一致

## 完整文件上传

- API: PUT /drive/node/upload/{projectId}/{repoName}/{fullPath}
- API 名称: drive_node_upload
- 功能说明:
  - 中文: 通过单次 HTTP PUT 上传完整文件到 Drive 仓库，服务端自动创建目录节点、文件节点并写入块数据
  - English: upload a complete file to a drive repository in one HTTP PUT request
- 请求体
  - 文件二进制流（`application/octet-stream`），无需 multipart 包装
- 请求头

  | 字段 | 类型 | 是否必须 | 说明 |
  | --- | --- | --- | --- |
  | X-BKREPO-SHA256 | string | 否 | 携带时服务端校验文件摘要 |
  | X-BKREPO-OVERWRITE | boolean | 否 | false | 是否覆盖已存在同名文件 |
  | X-BKREPO-META-&lt;key&gt; | string | 否 | 单条元数据，值需 URL 编码，header 名大小写不敏感，解析后 key 转小写 |
  | X-BKREPO-META | string | 否 | base64 编码的批量元数据，格式为 `base64(key1=value1&key2=value2)`，大小写敏感 |

- 响应体
  返回 `DriveNode`，字段说明见下文「DriveNode 返回字段说明」。
  ```json
  {
    "code": 0,
    "message": null,
    "data": {
      "id": "674a1b2c3d4e5f6789012345",
      "projectId": "demo",
      "repoName": "drive-local",
      "ino": 1001,
      "realIno": 1001,
      "parent": 2,
      "name": "a.txt",
      "size": 1024,
      "type": 1
    },
    "traceId": null
  }
  ```

- 行为说明
  - 若目标路径父目录不存在，服务端会自动递归创建目录节点
  - 若目标路径已存在同名文件，需设置请求头 `X-BKREPO-OVERWRITE: true` 才会覆盖文件内容与元数据；未设置或为 `false` 时返回节点已存在错误
  - 若目标路径已存在同名目录，返回节点冲突错误
  - 上传操作会记录 `DRIVE_NODE_UPLOAD` 操作日志

## 搜索仓库内文件

- API: POST /drive/node/search/{projectId}/{repoName}
- API 名称: drive_search_files
- 功能说明:
  - 中文: 整仓递归游标分页查询普通文件（排除目录、软链、已删除），支持文件名规则与多条元数据过滤
  - English: cursor-page regular files across the whole drive repo with name/multi-metadata filters
- 请求体
  ```json
  {
    "pageSize": 20,
    "name": {
      "value": "*report*",
      "operation": "MATCH_I"
    },
    "direction": "DESC",
    "lastModifiedDate": "2026-07-27T10:00:00",
    "lastId": "67d074a13d19772f4b813f90",
    "metadata": [
      {
        "key": "worksCategory",
        "value": ["image", "pdf"],
        "operation": "IN"
      },
      {
        "key": "author",
        "value": "alice",
        "operation": "EQ"
      }
    ]
  }
  ```
- 请求字段说明

  | 字段               | 类型     | 是否必须 | 默认值  | 说明                                                                 | Description                          |
  | ---------------- | ------ | ---- | ---- | ------------------------------------------------------------------ | ------------------------------------ |
  | projectId        | string | 是    | 无    | 路径参数：项目名称                                                          | project name                         |
  | repoName         | string | 是    | 无    | 路径参数：仓库名称                                                          | repo name                            |
  | pageSize         | int    | 否    | 20   | 每次查询条数                                                             | page size                            |
  | name             | object | 否    | 无    | 文件名查询条件，语义对齐节点搜索 `name` 规则                                        | name query rule                      |
  | name.value       | any    | 是    | 无    | 文件名匹配值；`MATCH/MATCH_I` 支持 `*` 通配符（如 `*report*` 表示包含）               | name value                           |
  | name.operation   | string | 否    | EQ   | 操作类型，支持与节点搜索一致的 OperationType（如 EQ/IN/MATCH/MATCH_I 等）             | operation type                       |
  | metadata         | array  | 否    | []   | 元数据过滤条件列表，多条规则按 AND 组合；单条语义对齐节点搜索 `metadata.{key}`（elemMatch） | metadata query rules                 |
  | metadata[].key   | string | 是    | 无    | 元数据 key；作品集可传 `worksCategory`                                     | metadata key                         |
  | metadata[].value | any    | 是    | 无    | 元数据 value；`IN/NIN` 时为数组                                           | metadata value                       |
  | metadata[].operation | string | 否 | EQ | 操作类型，支持与节点搜索一致的 OperationType（如 EQ/IN/NIN/MATCH 等）                | operation type                       |
  | direction        | string | 否    | DESC | 排序方向：`ASC` / `DESC`                                                | sort direction                       |
  | lastModifiedDate | string | 否    | 无    | 上一页最后一条 `lastModifiedDate`（ISO-8601），首次不传；需与 `lastId` 成对        | last modifiedDate cursor             |
  | lastId           | string | 否    | 无    | 上一页最后一条 `id`，需与 `lastModifiedDate` 成对                            | last id cursor                       |

- 排序与续页规则
  - 默认按 `lastModifiedDate DESC, id DESC` 返回；可通过 `direction` 改为 ASC
  - 首次查询不传 `lastModifiedDate/lastId`
  - 查询下一页时，使用上一页最后一条记录的 `lastModifiedDate/id` 作为游标

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
          "createdDate": "2026-07-27T10:00:00.000",
          "lastModifiedBy": "admin",
          "lastModifiedDate": "2026-07-27T10:00:00.000",
          "mtime": 1741770000000000000,
          "ctime": 1741770000000000000,
          "atime": 1741770000000000000,
          "projectId": "demo",
          "repoName": "drive-local",
          "ino": 1001,
          "targetIno": null,
          "realIno": 1001,
          "parent": 1,
          "name": "a.png",
          "size": 1024,
          "mode": 33188,
          "type": 1,
          "nlink": 1,
          "uid": 0,
          "gid": 0,
          "rdev": 0,
          "flags": 0,
          "symlinkTarget": null,
          "metadata": [
            { "key": "worksCategory", "value": "image", "system": false }
          ]
        }
      ]
    },
    "traceId": null
  }
  ```

## 统计仓库内文件数量

- API: POST /drive/node/search/count/{projectId}/{repoName}
- API 名称: drive_search_count
- 功能说明:
  - 中文: 按与文件搜索相同的过滤条件统计数量；未指定 `distinctByMetadataKeys` 时统计普通文件数，指定后按元数据键组合去重并取各系列最新版本计数（缺任一汇聚键的节点不计入）；可选 `groupByMetadataKey` 在过滤/去重后按该元数据值分桶，响应附带 `groups`（缺失或空白值以 `null` 表示）
  - English: count with the same filters as search; without `distinctByMetadataKeys` counts regular files; with it counts distinct series by metadata key composite using each series' latest version (nodes missing any grouping key are excluded); optional `groupByMetadataKey` buckets after filter/distinct and returns `groups` (missing/blank values as `null`)
- 请求体
  ```json
  {
    "name": {
      "value": "*img*",
      "operation": "MATCH_I"
    },
    "metadata": [
      {
        "key": "worksCategory",
        "value": "image",
        "operation": "EQ"
      }
    ],
    "distinctByMetadataKeys": [
      "IMATE_AGENT_ID",
      "IMATE_CONVERSATION_ID",
      "IMATE_ARTIFACT_NAME"
    ],
    "groupByMetadataKey": "IMATE_ARTIFACT_TYPE"
  }
  ```
- 请求字段说明

  | 字段            | 类型     | 是否必须 | 默认值 | 说明                                                  | Description              |
  | ------------- | ------ | ---- | --- | --------------------------------------------------- | ------------------------ |
  | projectId     | string | 是    | 无   | 路径参数：项目名称                                           | project name             |
  | repoName      | string | 是    | 无   | 路径参数：仓库名称                                           | repo name                |
  | name          | object | 否    | 无   | 文件名查询条件，语义与 search 接口一致                            | name query rule          |
  | metadata      | array  | 否    | []  | 元数据过滤条件列表，语义与 search 接口一致；`IMATE_ARTIFACT_TYPE` 在按系列去重时作用于各系列最新版本 | metadata query rules     |
  | distinctByMetadataKeys | array | 否 | 无 | 按元数据键组合去重计数；缺省为文件总数 | distinct series grouping keys |
  | groupByMetadataKey | string | 否 | 无 | 分桶元数据键；与 distinct 正交——无 distinct 时按文件分桶，有 distinct 时按各系列最新版本分桶 | metadata key for buckets |

- 响应体
  ```json
  {
    "code": 0,
    "message": null,
    "data": {
      "total": 7,
      "groups": [
        { "value": "image", "count": 3 },
        { "value": "pdf", "count": 3 },
        { "value": null, "count": 1 }
      ]
    },
    "traceId": null
  }
  ```

  未指定 `groupByMetadataKey` 时 `groups` 为空数组；指定时 `total` 等于各桶 `count` 之和（含 `value: null`）。

## 生成节点预览 URL

- API: POST /drive/node/preview/url/{projectId}/{repoName}/{ino}?type=xxx
- API 名称: drive_preview_url
- 功能说明:
  - 中文: 按调用方类型生成文件预览 URL；需对仓库有 READ 权限，且目标 ino 必须是已存在的普通文件
  - English: build a preview URL by caller type; requires READ permission and an existing regular file inode
- 路径/查询参数说明

  | 字段        | 类型     | 是否必须 | 默认值 | 说明                                                                 | Description                                      |
  | --------- | ------ | ---- | --- | ------------------------------------------------------------------ | ------------------------------------------------ |
  | projectId | string | 是    | 无   | 路径参数：项目名称                                                         | project name                                     |
  | repoName  | string | 是    | 无   | 路径参数：仓库名称                                                         | repo name                                        |
  | ino       | long   | 是    | 无   | 路径参数：文件 inode                                                     | file inode                                       |
  | type      | string | 否    | 无   | 预览调用方类型；仅精确匹配 `IMATE_AGENT` 时返回 Agent 自定义 URL；其它值或缺失时返回 Client URL | caller type; exact `IMATE_AGENT` or client fallback |

- 行为说明
  - 按 ino 查节点并反查 fullPath；节点不存在或目标为目录时返回节点不存在错误
  - `type=IMATE_AGENT`：返回 `imate_artifact://{ino}?name={urlencoded}&type={type}`
    - `name`：元数据 `IMATE_ARTIFACT_NAME`，缺省为文件 basename
    - `type`：元数据 `IMATE_ARTIFACT_TYPE`（需为 image/pdf/html/code/table/slides/markdown/video/audio/other），缺省或非法为 `other`
  - 其它/缺失 `type`：返回 `{drive.domain}/ui/{projectId}/filePreview/local/0/{repoName}{fullPath}`
  - Client 打开该 URL 时，音视频走 HTML5 播放器，按 HTTP Range 从 Drive 节点流式读取，不落地整文件；Office/PDF 等转换预览仍拉取完整文件
  - Client URL 不签发临时 token、不生成短链；`drive.domain` 为空时报参数错误
  - 无请求体

- 响应体（IMATE_CLIENT 示例）
  ```json
  {
    "code": 0,
    "message": null,
    "data": "https://bkrepo.example.com/ui/blueking/filePreview/local/0/drive/docs/readme.txt",
    "traceId": null
  }
  ```

- 响应体（IMATE_AGENT 示例）
  ```json
  {
    "code": 0,
    "message": null,
    "data": "imate_artifact://100?name=%E5%91%A8%E6%8A%A5&type=table",
    "traceId": null
  }
  ```

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


