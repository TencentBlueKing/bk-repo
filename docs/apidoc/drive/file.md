# Drive 文件操作接口

[toc]

## 读取文件

- API: GET /drive/{projectId}/{repoName}/{ino}
- API 名称: drive_read_file
- 功能说明:
  - 中文: 按 inode 读取 Drive 文件，支持范围读取
  - English: read drive file by inode with range support
- 请求体
此接口请求体为空
- 请求字段说明

  | 字段        | 类型     | 是否必须 | 默认值 | 说明       | Description  |
  | --------- | ------ | ---- | --- | -------- | ------------ |
  | projectId | string | 是    | 无   | 项目名称     | project name |
  | repoName  | string | 是    | 无   | 仓库名称     | repo name    |
  | ino       | long   | 是    | 无   | 文件 inode | file inode   |

- 请求头

  | 字段    | 类型     | 是否必须 | 默认值 | 说明                            | Description |
  | ----- | ------ | ---- | --- | ----------------------------- | ----------- |
  | Range | string | 否    | 无   | 字节范围，格式 `bytes=start-end`，单区间 | bytes range |

- 响应头

  | 字段             | 类型     | 说明                              | Description       |
  | -------------- | ------ | ------------------------------- | ----------------- |
  | Accept-Ranges  | string | 固定为 `bytes`                     | accept byte range |
  | Content-Range  | string | 返回范围，格式 `bytes start-end/total` | content range     |
  | Content-Length | long   | 返回内容长度（字节）                      | content length    |

- 响应体
[文件流]

## 写入文件块

- API: PUT /drive/block/{projectId}/{repoName}/{ino}/{offset}
- API 名称: drive_write_block
- 功能说明:
  - 中文: 向指定 inode 在指定偏移量写入一个文件块
  - English: write a block to inode at offset
- 请求体
[文件流]
- 请求字段说明

  | 字段        | 类型     | 是否必须 | 默认值 | 说明       | Description        |
  | --------- | ------ | ---- | --- | -------- | ------------------ |
  | projectId | string | 是    | 无   | 项目名称     | project name       |
  | repoName  | string | 是    | 无   | 仓库名称     | repo name          |
  | ino       | long   | 是    | 无   | 文件 inode | file inode         |
  | offset    | long   | 是    | 无   | 块起始偏移量   | block start offset |

- 响应体
  ```json
  {
    "code": 0,
    "message": null,
    "data": {
      "id": "67d074a13d19772f4b813f90",
      "createdBy": "admin",
      "createdDate": "2026-03-12T11:00:00",
      "projectId": "demo",
      "repoName": "drive-local",
      "ino": 1001,
      "startPos": 0,
      "sha256": "d17f25ecfbcc7857f7bebea469308be0b2580943e96d13a3ad98a13675c4bfc2",
      "crc64ecma": "0",
      "size": 4096,
      "endPos": 4095
    },
    "traceId": null
  }
  ```

