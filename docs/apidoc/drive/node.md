# Drive 节点操作接口

[toc]

## 批量变更节点

- API: POST /drive/node/batch/{projectId}/{repoName}
- API 名称: drive_node_batch
- 功能说明:
  - 中文: 批量执行节点创建、更新、删除、创建硬链接
  - English: batch create/update/delete/hard-link nodes
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
        "nodeId": "67d074a13d19772f4b813f90",
        "size": 1024,
        "lastModifiedDate": "2026-03-12T09:00:00"
      }
    },
    {
      "op": "delete",
      "node": {
        "nodeId": "67d074a13d19772f4b813f91",
        "lastModifiedDate": "2026-03-12T09:10:00"
      }
    }
  ]
  ```
- 请求字段说明

  | 字段        | 类型     | 是否必须 | 默认值 | 说明                                                  | Description    |
  | --------- | ------ | ---- | --- | --------------------------------------------------- | -------------- |
  | projectId | string | 是    | 无   | 项目名称                                                | project name   |
  | repoName  | string | 是    | 无   | 仓库名称                                                | repo name      |
  | op        | string | 是    | 无   | 操作类型: `create`/`update`/`delete`/`create_hard_link` | operation type |
  | node      | object | 是    | 无   | 操作对象                                                | operation node |

- `node` 字段说明

  | 字段               | 类型      | 是否必须 | 默认值   | 说明                                                  | Description            |
  | ---------------- | ------- | ---- | ----- | --------------------------------------------------- | ---------------------- |
  | nodeId           | string  | 否    | 无     | 节点 ID（update/delete 必填）                             | node id                |
  | ino              | long    | 否    | 无     | inode（create/create_hard_link 必填）                   | inode                  |
  | targetIno        | long    | 否    | 无     | 硬链接目标 inode（可选）                                     | hard-link target inode |
  | parent           | long    | 否    | 无     | 父目录 inode（create 常用）                                | parent inode           |
  | name             | string  | 否    | 无     | 文件名                                                 | node name              |
  | size             | long    | 否    | 无     | 文件大小                                                | file size              |
  | mode             | int     | 否    | 无     | 文件模式                                                | file mode              |
  | type             | int     | 否    | 无     | 文件类型: 1 文件, 2 目录, 3 软链接                             | file type              |
  | nlink            | int     | 否    | 无     | 硬链接数                                                | hard link count        |
  | uid              | int     | 否    | 无     | 用户 ID                                               | user id                |
  | gid              | int     | 否    | 无     | 组 ID                                                | group id               |
  | rdev             | int     | 否    | 无     | 设备 ID                                               | device id              |
  | flags            | int     | 否    | 无     | 文件标志                                                | file flags             |
  | symlinkTarget    | string  | 否    | 无     | 软链接目标路径                                             | symlink target         |
  | mtime            | long    | 否    | 无     | 修改时间（纳秒时间戳），create 时不传则使用当前时间                       | modify time (nanos)    |
  | ctime            | long    | 否    | 无     | 属性变更时间（纳秒时间戳），create 时不传则使用当前时间                     | change time (nanos)    |
  | atime            | long    | 否    | 无     | 访问时间（纳秒时间戳），create 时不传则使用当前时间                       | access time (nanos)    |
  | lastModifiedDate | string  | 否    | 无     | 注意该字段不是用于更新，而是服务端的最后修改时间与参数值不匹配时将返回错误，用于并发控制避免数据被覆盖 | last modified date     |
  | force            | boolean | 否    | false | 是否强制忽略并发检查                                          | force update/delete    |

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
      }
    ],
    "traceId": null
  }
  ```
- data 字段说明

  | 字段      | 类型       | 说明                                                 | Description                                       |
  | ------- | -------- |----------------------------------------------------| ------------------------------------------------- |
  | op      | string   | 操作类型: `create`/`update`/`delete`/`create_hard_link` | operation type                                    |
  | ino     | long     | 本次操作节点的 ino（操作失败时可能为空）                             | node ino                                          |
  | nodeId  | string   | 本次操作节点 ID（操作失败时可能为空）                                      | node id                                           |
  | node    | object   | 节点详细信息，仅创建和更新操作存在该字段，删除操作为 null，字段同 DriveNode      | node detail, only exists for create/update ops    |
  | code    | int      | 操作结果码，0 表示成功                                       | result code                                       |
  | message | string   | 失败消息                                               | failure message                                   |


## 分页查询目录下节点

- API: GET /drive/node/page/{projectId}/{repoName}
- API 名称: drive_list_nodes_page
- 功能说明:
  - 中文: 分页查询指定父目录下的节点
  - English: list nodes by parent in page
- 请求体
此接口请求体为空
- 请求字段说明

  | 字段                  | 类型      | 是否必须 | 默认值   | 说明                 | Description           |
  | ------------------- | ------- | ---- | ----- | ------------------ | --------------------- |
  | projectId           | string  | 是    | 无     | 项目名称               | project name          |
  | repoName            | string  | 是    | 无     | 仓库名称               | repo name             |
  | parent              | long    | 否    | 无     | 父目录 inode，不传表示查询根层 | parent inode          |
  | pageNum             | int     | 否    | 0     | 页码，从 0 开始          | page number           |
  | pageSize            | int     | 否    | 20    | 每页条数               | page size             |
  | includeTotalRecords | boolean | 否    | false | 是否统计总条数            | include total records |
  | snapSeq             | long    | 否    | 无     | 快照序号，不传则查询当前视图     | snapshot sequence     |

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
          "createdDate": "2026-03-12T09:00:00",
          "lastModifiedBy": "admin",
          "lastModifiedDate": "2026-03-12T09:00:00",
          "mtime": 1741770000000000000,
          "ctime": 1741770000000000000,
          "atime": 1741770000000000000,
          "projectId": "demo",
          "repoName": "drive-local",
          "ino": 1001,
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

## 分页查询增量变更节点

- API: GET /drive/node/modified/page/{projectId}/{repoName}
- API 名称: drive_list_modified_nodes_page
- 功能说明:
  - 中文: 按最后修改时间分页查询增量变更节点
  - English: list modified nodes in page
- 请求体
此接口请求体为空
- 请求字段说明

  | 字段                  | 类型      | 是否必须 | 默认值   | 说明                          | Description                    |
  | ------------------- | ------- | ---- | ----- | --------------------------- | ------------------------------ |
  | projectId           | string  | 是    | 无     | 项目名称                        | project name                   |
  | repoName            | string  | 是    | 无     | 仓库名称                        | repo name                      |
  | lastModifiedDate    | string  | 是    | 无     | 查询该时间之后的变更，ISO_DATE_TIME 格式 | last modified date lower bound |
  | pageNum             | int     | 否    | 0     | 页码，从 0 开始                   | page number                    |
  | pageSize            | int     | 否    | 20    | 每页条数                        | page size                      |
  | includeTotalRecords | boolean | 否    | false | 是否统计总条数                     | include total records          |

- 响应体
与“分页查询目录下节点”一致

## DriveNode 文件类型枚举


| 枚举值 | 说明   | Description   |
| --- | ---- | ------------- |
| 1   | 普通文件 | regular file  |
| 2   | 目录   | directory     |
| 3   | 软链接  | symbolic link |


