# 通用制品仓库分块文件操作指南

[toc]

## 初始化分块上传

- **接口地址**: `POST /generic/separate/{project}/{repo}/{path}`
- **接口名称**: `start_block_upload`
- **功能说明**:
  - 中文：初始化分块上传
  - English: Start block upload

### 请求参数

- **路径参数**

  | 字段    | 类型   | 必须 | 描述             | Description  |
  | ------- | ------ | ---- | ---------------- | ------------ |
  | project | string | 是   | 项目名称         | Project name |
  | repo    | string | 是   | 仓库名称         | Repo name    |
  | path    | string | 是   | 完整路径         | Full path    |

- **请求头**

  | 字段               | 类型    | 必须 | 默认值 | 描述                    | Description            |
  | ------------------ | ------- | ---- | ------ | ----------------------- | ---------------------- |
  | X-BKREPO-OVERWRITE | boolean | 否   | false  | 是否覆盖已存在文件      | Overwrite exist file   |

### 响应参数

- **响应体**

  ```json
  {
    "code": 0,
    "message": null,
    "data": {
      "uploadId": "8be31384f82a45b0aafb6c6add29e94f/xxxxxxxx",
      "expireSeconds": 43200
    },
    "traceId": null
  }
  ```

- **字段说明**

  | 字段          | 类型    | 描述                        | Description                      |
  | ------------- | ------- | --------------------------- | -------------------------------- |
  | code          | int     | 错误编码，0 表示成功        | 0: success, other: failure       |
  | message       | string  | 错误消息                    | The failure message              |
  | data          | object  | 返回数据                    | Response data                    |
  | ├─ uploadId   | string  | 分块上传 ID                 | Block upload ID                  |
  | ├─ expireSeconds | int  | 上传 ID 过期时间，单位：秒  | Upload ID expiration in seconds  |
  | traceId       | string  | 请求跟踪 ID                 | Trace ID                         |

---

## 上传分块文件

- **接口地址**: `PUT /generic/{project}/{repo}/{path}`
- **接口名称**: `block_upload`
- **功能说明**:
  - 中文：分块上传通用制品文件
  - English: Upload generic artifact file block

### 请求参数

- **路径参数**

  | 字段    | 类型   | 必须 | 描述             | Description  |
  | ------- | ------ | ---- | ---------------- | ------------ |
  | project | string | 是   | 项目名称         | Project name |
  | repo    | string | 是   | 仓库名称         | Repo name    |
  | path    | string | 是   | 完整路径         | Full path    |

- **请求头**

  | 字段               | 类型    | 必须 | 默认值 | 描述                                               | Description                          |
  | ------------------ | ------- | ---- | ------ | -------------------------------------------------- | ------------------------------------ |
  | X-BKREPO-UPLOAD-ID | string  | 是   | 无     | 分块上传 ID                                        | Block upload ID                      |
  | X-BKREPO-OFFSET    | int     | 是   | 无     | 分块偏移量，起始值为 0                             | Block offset (start from 0)          |
  | X-BKREPO-SHA256    | string  | 否   | 无     | 分块文件的 SHA256 校验值                           | SHA256 checksum of the block         |
  | X-BKREPO-MD5       | string  | 否   | 无     | 分块文件的 MD5 校验值                              | MD5 checksum of the block            |
  | UPLOAD-TYPE        | string  | 是   | 无     | 上传类型，值为 `SEPARATE-UPLOAD`                   | Upload type (`SEPARATE-UPLOAD`)      |

- **请求体**

  - 文件流（binary data）

### 响应参数

- **响应体**

  ```json
  {
    "code": 0,
    "message": null,
    "data": null,
    "traceId": null
  }
  ```

- **字段说明**

  | 字段     | 类型   | 描述                        | Description                |
  | -------- | ------ | --------------------------- | -------------------------- |
  | code     | int    | 错误编码，0 表示成功        | 0: success, other: failure |
  | message  | string | 错误消息                    | The failure message        |
  | data     | null   | 返回数据（为空）            | Response data (null)       |
  | traceId  | string | 请求跟踪 ID                 | Trace ID                   |

---

## 完成分块上传

- **接口地址**: `PUT /generic/separate/{project}/{repo}/{path}`
- **接口名称**: `complete_block_upload`
- **功能说明**:
  - 中文：完成分块上传
  - English: Complete block upload

### 请求参数

- **路径参数**

  | 字段    | 类型   | 必须 | 描述             | Description  |
  | ------- | ------ | ---- | ---------------- | ------------ |
  | project | string | 是   | 项目名称         | Project name |
  | repo    | string | 是   | 仓库名称         | Repo name    |
  | path    | string | 是   | 完整路径         | Full path    |

- **请求头**

  | 字段               | 类型    | 必须 | 默认值 | 描述                    | Description            |
  | ------------------ | ------- | ---- | ------ | ----------------------- | ---------------------- |
  | X-BKREPO-UPLOAD-ID | string  | 是   | 无     | 分块上传 ID             | Block upload ID        |
  | X-BKREPO-SIZE      | string  | 是   | 0      | 文件总大小              | Total size of the file |
  | X-BKREPO-OVERWRITE | boolean | 否   | false  | 是否覆盖已存在文件      | Overwrite exist file   |

- **请求体**

  - 此接口请求体为空。

### 响应参数

- **响应体**

  ```json
  {
    "code": 0,
    "message": null,
    "data": null,
    "traceId": ""
  }
  ```

- **字段说明**

  | 字段     | 类型   | 描述                        | Description                |
  | -------- | ------ | --------------------------- | -------------------------- |
  | code     | int    | 错误编码，0 表示成功        | 0: success, other: failure |
  | message  | string | 错误消息                    | The failure message        |
  | data     | null   | 返回数据（为空）            | Response data (null)       |
  | traceId  | string | 请求跟踪 ID                 | Trace ID                   |

---

## 终止（取消）分块上传

- **接口地址**: `DELETE /generic/separate/{project}/{repo}/{path}`
- **接口名称**: `abort_block_upload`
- **功能说明**:
  - 中文：终止（取消）分块上传
  - English: Abort block upload

### 请求参数

- **路径参数**

  | 字段    | 类型   | 必须 | 描述             | Description  |
  | ------- | ------ | ---- | ---------------- | ------------ |
  | project | string | 是   | 项目名称         | Project name |
  | repo    | string | 是   | 仓库名称         | Repo name    |
  | path    | string | 是   | 完整路径         | Full path    |

- **请求头**

  | 字段               | 类型   | 必须 | 默认值 | 描述          | Description      |
  | ------------------ | ------ | ---- | ------ | ------------- | ---------------- |
  | X-BKREPO-UPLOAD-ID | string | 是   | 无     | 分块上传 ID   | Block upload ID  |

- **请求体**

  - 此接口请求体为空。

### 响应参数

- **响应体**

  ```json
  {
    "code": 0,
    "message": null,
    "data": null,
    "traceId": null
  }
  ```

- **字段说明**

  | 字段     | 类型   | 描述                        | Description                |
  | -------- | ------ | --------------------------- | -------------------------- |
  | code     | int    | 错误编码，0 表示成功        | 0: success, other: failure |
  | message  | string | 错误消息                    | The failure message        |
  | data     | null   | 返回数据（为空）            | Response data (null)       |
  | traceId  | string | 请求跟踪 ID                 | Trace ID                   |

---

## 查询已上传的分块列表

- **接口地址**: `GET /generic/separate/{project}/{repo}/{path}`
- **接口名称**: `list_upload_block`
- **功能说明**:
  - 中文：查询已上传的分块列表
  - English: List uploaded blocks

### 请求参数

- **路径参数**

  | 字段    | 类型   | 必须 | 描述             | Description  |
  | ------- | ------ | ---- | ---------------- | ------------ |
  | project | string | 是   | 项目名称         | Project name |
  | repo    | string | 是   | 仓库名称         | Repo name    |
  | path    | string | 是   | 完整路径         | Full path    |

- **请求头**

  | 字段               | 类型   | 必须 | 默认值 | 描述          | Description      |
  | ------------------ | ------ | ---- | ------ | ------------- | ---------------- |
  | X-BKREPO-UPLOAD-ID | string | 是   | 无     | 分块上传 ID   | Block upload ID  |

- **请求体**

  - 此接口请求体为空。

### 响应参数

- **响应体**

  ```json
  {
    "code": 0,
    "message": null,
    "data": [
      {
        "size": 10240,
        "sha256": "000000000000000000000000000000000000000000000",
        "startPos": 0,
        "uploadId": "1.0"
      },
      {
        "size": 10240,
        "sha256": "000000000000000000000000000000000000000000000",
        "startPos": 10240,
        "uploadId": "1.0"
      }
    ],
    "traceId": null
  }
  ```

- **字段说明**

  | 字段     | 类型   | 描述                        | Description                |
  | -------- | ------ | --------------------------- | -------------------------- |
  | code     | int    | 错误编码，0 表示成功        | 0: success, other: failure |
  | message  | string | 错误消息                    | The failure message        |
  | data     | array  | 分块列表                    | Block list                 |
  | traceId  | string | 请求跟踪 ID                 | Trace ID                   |

- **分块信息字段说明**

  | 字段     | 类型   | 描述           | Description                |
  | -------- | ------ | -------------- | -------------------------- |
  | size     | long   | 分块大小       | Block size                 |
  | sha256   | string | 分块 SHA256 值 | Block SHA256 checksum      |
  | startPos | long   | 分块起始位置   | Block start position       |
  | uploadId | string | 分块版本信息   | Block upload ID            |

---