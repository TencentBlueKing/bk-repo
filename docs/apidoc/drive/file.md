# Drive 文件操作接口

[toc]

## 读取文件

- API: GET /{projectId}/{repoName}/{fullPath}
- API 名称: drive_read_file
- 功能说明:
  - 中文: 读取 Drive 文件，支持范围读取
  - English: read drive file with range support
- 请求体
  此接口请求体为空

- 请求字段说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |projectId|string|是|无|项目名称|project name|
  |repoName|string|是|无|仓库名称|repo name|
  |fullPath|string|是|无|完整路径（`/**` 捕获）|full path|
  |category|string|否|LOCAL|节点位置，支持 `LOCAL`/`REMOTE`|node category|
  |mode|int|否|无|节点模式|node mode|
  |flags|int|否|无|节点标志|node flags|
  |rdev|int|否|无|设备号|device id|
  |type|int|否|无|文件类型|file type|

- 请求头

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |Range|string|否|无|字节范围，格式 `bytes=start-end`，单区间|bytes range|

- 响应头

  |字段|类型|说明|Description|
  |---|---|---|---|
  |Accept-Ranges|string|固定为 `bytes`|accept byte range|
  |Content-Range|string|分段读取时返回的范围|content range|
  |Content-Length|long|返回内容长度（字节）|content length|

- 响应体
  [文件流]

## 写入文件块

- API: PUT /block/{offset}/{projectId}/{repoName}/{fullPath}
- API 名称: drive_write_block
- 功能说明:
  - 中文: 在指定偏移量写入一个文件块
  - English: write a block at offset
- 请求体
  [文件流]

- 请求字段说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |offset|long|是|无|块起始偏移量|block start offset|
  |projectId|string|是|无|项目名称|project name|
  |repoName|string|是|无|仓库名称|repo name|
  |fullPath|string|是|无|完整路径（`/**` 捕获）|full path|
  |category|string|否|LOCAL|节点位置，支持 `LOCAL`/`REMOTE`|node category|
  |mode|int|否|无|节点模式|node mode|
  |flags|int|否|无|节点标志|node flags|
  |rdev|int|否|无|设备号|device id|
  |type|int|否|无|文件类型|file type|

- 响应体

  ```json
  {
    "code": 0,
    "message": null,
    "data": {
      "createdBy": "admin",
      "createdDate": "2026-03-12T11:00:00",
      "nodeFullPath": "/a/b.txt",
      "startPos": 0,
      "sha256": "d17f25ecfbcc7857f7bebea469308be0b2580943e96d13a3ad98a13675c4bfc2",
      "crc64ecma": "0",
      "projectId": "demo",
      "repoName": "drive-local",
      "size": 4096,
      "endPos": 4095
    },
    "traceId": null
  }
  ```

## 写入并立即刷新

- API: PUT /block/write-flush/{offset}/{projectId}/{repoName}/{fullPath}?length={length}
- API 名称: drive_write_and_flush
- 功能说明:
  - 中文: 写入文件块后立即刷新目标文件长度，适用于小文件单次提交
  - English: write block and flush length in one request
- 请求体
  [文件流]

- 请求字段说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |offset|long|是|无|块起始偏移量|block start offset|
  |projectId|string|是|无|项目名称|project name|
  |repoName|string|是|无|仓库名称|repo name|
  |fullPath|string|是|无|完整路径（`/**` 捕获）|full path|
  |length|long|是|无|刷新后的文件总长度|target file length|

- 响应体
  与“写入文件块”一致

## 刷新文件长度

- API: PUT /block/flush/{projectId}/{repoName}/{fullPath}?length={length}
- API 名称: drive_flush_file
- 功能说明:
  - 中文: 根据已写入块刷新文件长度并完成提交
  - English: flush file by expected length
- 请求体
  此接口请求体为空

- 请求字段说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |projectId|string|是|无|项目名称|project name|
  |repoName|string|是|无|仓库名称|repo name|
  |fullPath|string|是|无|完整路径（`/**` 捕获）|full path|
  |length|long|是|无|刷新后的文件总长度|target file length|

- 响应体

  ```json
  {
    "code": 0,
    "message": null,
    "data": null,
    "traceId": null
  }
  ```

## 流式上传文件

- API: PUT /stream/{projectId}/{repoName}/{fullPath}?size={size}
- API 名称: drive_stream_upload
- 功能说明:
  - 中文: 通过流式方式上传文件，服务端按块落盘
  - English: upload file by stream mode
- 请求体
  [文件流]

- 请求字段说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |projectId|string|是|无|项目名称|project name|
  |repoName|string|是|无|仓库名称|repo name|
  |fullPath|string|是|无|完整路径（`/**` 捕获）|full path|
  |size|long|是|无|文件总大小（字节）|file size|

- 请求头

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |X-BKREPO-OVERWRITE|boolean|否|false|是否覆盖已存在文件|overwrite existing file|
  |X-BKREPO-EXPIRES|long|否|0|过期时间（天），0 表示永久|file expired days|

- 响应体

  ```json
  {
    "code": 0,
    "message": null,
    "data": {
      "projectId": "demo",
      "repoName": "drive-local",
      "fullPath": "/a/b.txt",
      "folder": false,
      "size": 10240,
      "sha256": "0000000000000000000000000000000000000000000000000000000000000000",
      "md5": "00000000000000000000000000000000"
    },
    "traceId": null
  }
  ```
