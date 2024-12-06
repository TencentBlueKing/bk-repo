# Generic通用制品仓库分块文件操作

[toc]

## 初始化分块上传

- API: POST /generic/separate/block/{project}/{repo}/{path}
- API 名称: start_block_upload
- 功能说明：
	- 中文：初始化分块上传
	- English：start block upload

- 请求体
此接口请求体为空

- 请求字段说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |project|string|是|无|项目名称|project name|
  |repo|string|是|无|仓库名称|repo name|
  |path|string|是|无|完整路径|full path|

- 请求头

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
- |X-BKREPO-SIZE|string|是|0L|文件大小|上传文件的总大小|
  |X-BKREPO-MD5|string|否|无|文件md5|file md5|
  |X-BKREPO-OVERWRITE|boolean|否|false|是否覆盖已存在文件|overwrite exist file|

- 响应体

``` json
  {
    "code" : 0,
    "message" : null,
    "data" : null,
    "traceId" : null
  }
```

## 上传分块文件

- API: PUT /generic/separate/{project}/{repo}/{path}
- API 名称: block_upload
- 功能说明：
	- 中文：分块上传通用制品文件
	- English：upload generic artifact file block

- 请求体
[文件流]

- 请求字段说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |project|string|是|无|项目名称|project name|
  |repo|string|是|无|仓库名称|repo name|
  |path|string|是|无|完整路径|full path|

- 请求头

  |字段|类型| 是否必须 |默认值| 说明   | Description |
  |---|---|-|---|------|---------|
  |X-BKREPO-SEQUENCE|int|是|无| 分块序号(从1开始), SEQUENCE 和 OFFSET 二者不可同时为空 |block sequence(start from 1) |
- |X-BKREPO-OFFSET|int|是|无| 分块偏移量,SEQUENCE 和 OFFSET 二者不可同时为空| block offset(start from 0)|
  |X-BKREPO-SHA256|string|否|无| 文件sha256|file sha256|
  |X-BKREPO-MD5|string|否|无| 文件md5| file md5|

- 响应体

  ``` json
  {
    "code" : 0,
    "message" : null,
    "data" : null,
    "traceId" : null
  }
  ```

## 完成分块上传

- API: PUT /generic/separate/block/{project}/{repo}/{path}
- API 名称: complete_block_upload
- 功能说明：
	- 中文：完成化分块上传
	- English：complete block upload

- 请求体
此接口请求体为空

- 请求字段说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |project|string|是|无|项目名称|project name|
  |repo|string|是|无|仓库名称|repo name|
  |path|string|是|无|完整路径|full path|

- 请求头

- 响应体

  ``` json
  {
    "code" : 0,
    "message" : null,
    "data" : null,
    "traceId" : ""
  }
  ```

## 终止(取消)分块上传

- API: DELETE /generic/separate/block/{project}/{repo}/{path}
- API 名称: abort_block_upload
- 功能说明：
	- 中文：终止(取消)分块上传
	- English：abort block upload

- 请求体
此接口请求体为空

- 请求字段说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |project|string|是|无|项目名称|project name|
  |repo|string|是|无|仓库名称|repo name|
  |path|string|是|无|完整路径|full path|

- 请求头

- 响应体

  ``` json
  {
    "code" : 0,
    "message" : null,
    "data" : null,
    "traceId" : null
  }
  ```

## 查询已上传的分块列表

- API: GET /generic/separate/block/{project}/{repo}/{path}
- API 名称: list_upload_block
- 功能说明：
	- 中文：查询已上传的分块列表
	- English：list upload block

- 请求体
  此接口请求体为空

- 请求字段说明

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |project|string|是|无|项目名称|project name|
  |repo|string|是|无|仓库名称|repo name|
  |path|string|是|无|完整路径|full path|

- 请求头

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |Authorization|string|否|无|Basic Auth认证头，Basic base64(username:password)|Basic Auth header|

- 响应体

  ``` json
  {
    "code" : 0,
    "message" : null,
    "data" : [ {
      "size" : 10240,
      "sha256" : "d17f25ecfbcc7857f7bebea469308be0b2580943e96d13a3ad98a13675c4bfc2",
      "startPos" : xxx
    }, {
      "size" : 10240,
      "sha256" : "cc399d73903f06ee694032ab0538f05634ff7e1ce5e8e50ac330a871484f34cf",
      "startPos" : xxx
    } ],
    "traceId" : null
  }
  ```

- 响应字段说明

  |字段|类型|说明|Description|
  |---|---|---|---|
  |code|bool|错误编码。 0表示success，>0表示失败错误|0:success, other: failure|
  |message|string|错误消息|the failure message|
  |data|list|分块列表|block list|
  |traceId|string|请求跟踪id|trace id|

- 分块信息字段说明

  |字段| 类型  | 说明 |Description|
  |---|-----|----|---|
  |size| long |分块大小|block size|
  |sha256| string| 分块sha256|block sha256 checksum|
  |startPos| long| 分块起始位置|block sequence|