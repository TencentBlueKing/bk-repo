### 上传文件

- API: PUT /generic/{project}/{repo}/{path}
- API 名称: upload
- 功能说明：
	- 中文：上传通用制品文件
	- English：upload generic artifact file

- 请求体

``` json
[文件流]
```
- 请求参数

此接口无请求参数

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
|X-BKREPO-SHA256|string|否|无|文件sha256|file sha256|
|X-BKREPO-MD5|string|否|无|文件md5|file md5|
|X-BKREPO-OVERWRITE|boolean|否|false|是否覆盖已存在文件|overwrite exist file|
|X-BKREPO-EXPIRES|long|否|0|过期时间，单位天(0代表永久保存)|file expired days|
|X-BKREPO-META-{key}|string|否|无|文件元数据，{key}表示元数据key，可以添加多个|file metadata|


- 响应体

``` json
{
  "code" : 0,
  "message" : null,
  "data" : {
    "createdBy" : "admin",
    "createdDate" : "2020-07-27T16:02:31.394",
    "lastModifiedBy" : "admin",
    "lastModifiedDate" : "2020-07-27T16:02:31.394",
    "folder" : false,
    "path" : "/",
    "name" : "test.json",
    "fullPath" : "/test.json",
    "size" : 34,
    "sha256" : "6a7983009447ecc725d2bb73a60b55d0ef5886884df0ffe3199f84b6df919895",
    "md5" : "2947b3932900d4534175d73964ec22ef",
    "projectId" : "test",
    "repoName" : "generic-local"
  },
  "traceId" : ""
}
```

- 响应字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误 |0:success, other: failure|
|message|string|错误消息|the failure message|
|data|object|文件节点信息|file node info|
|traceId|string|请求跟踪id|the trace id|

- data字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|createdBy|string|创建者|create user|
|createdDate|string|创建时间|create time|
|lastModifiedBy|string|上次修改者|last modify user|
|lastModifiedDate|string|上次修改时间|last modify time|
|folder|bool|是否为文件夹|is folder|
|path|string|节点目录|node path|
|name|string|节点名称|node name|
|fullPath|string|节点完整路径|node full path|
|size|long|节点大小|file size|
|sha256|string|节点sha256|file sha256|
|md5|string|节点md5|file md5 checksum|
|projectId|string|节点所属项目|node project id|
|repoName|string|节点所属仓库|node repository name|


### 初始化分块上传

- API: POST /generic/block/{project}/{repo}/{path}
- API 名称: start_block_upload
- 功能说明：
	- 中文：初始化分块上传
	- English：start block upload

- 请求体

此接口请求体为空

- 请求参数

此接口无请求参数

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
|X-BKREPO-MD5|string|否|无|文件md5|file md5|
|X-BKREPO-OVERWRITE|boolean|否|false|是否覆盖已存在文件|overwrite exist file|
|X-BKREPO-EXPIRES|long|否|0|过期时间，单位天(0代表永久保存)|file expired days|

- 响应体

``` json
{
  "code" : 0,
  "message" : null,
  "data" : {
    "uploadId" : "8be31384f82a45b0aafb6c6add29e94f",
    "expireSeconds" : 43200
  },
  "traceId" : ""
}
```

- 响应字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误 |0:success, other: failure|
|message|string|错误消息|the failure message|
|data|object|分块上传初始化结果|block upload result|
|traceId|string|请求跟踪id|the trace id|

- data字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|uploadId|string|分块上传id|block upload id|
|expireSeconds|string|上传有效期(秒)|expire time(seconds)|

### 上传分块文件

- API: PUT /{project}/{repo}/{path}
- API 名称: block_upload
- 功能说明：
	- 中文：分块上传通用制品文件
	- English：upload generic artifact file block

- 请求体

``` json
[文件流]
```
- 请求参数

此接口无请求参数

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
|X-BKREPO-UPLOAD-ID|string|否|无|分块上传id|block upload id|
|X-BKREPO-SEQUENCE|int|否|无|分块序号(从1开始)|block sequence(start from 1)|
|X-BKREPO-SHA256|string|否|无|文件sha256|file sha256|
|X-BKREPO-MD5|string|否|无|文件md5|file md5|

- 响应体

``` json
{
  "code" : 0,
  "message" : null,
  "data" : null,
  "traceId" : ""
}
```

- 响应字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误 |0:success, other: failure|
|message|string|错误消息|the failure message|
|data|null|空|null|
|traceId|string|请求跟踪id|the trace id|


### 完成分块上传

- API: PUT /generic/block/{project}/{repo}/{path}
- API 名称: complete_block_upload
- 功能说明：
	- 中文：完成化分块上传
	- English：complete block upload

- 请求体

此接口请求体为空

- 请求参数

此接口无请求参数

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
|X-BKREPO-UPLOAD-ID|string|是|无|分块上传ID|block upload id|

- 响应体

``` json
{
  "code" : 0,
  "message" : null,
  "data" : null,
  "traceId" : ""
}
```

- 响应字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误 |0:success, other: failure|
|message|string|错误消息|the failure message|
|data|null|null|null|
|traceId|string|请求跟踪id|the trace id|

### 终止(取消)分块上传

- API: DELETE /generic/block/{project}/{repo}/{path}
- API 名称: abort_block_upload
- 功能说明：
	- 中文：终止(取消)分块上传
	- English：abort block upload

- 请求体

此接口请求体为空

- 请求参数

此接口无请求参数

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
|X-BKREPO-UPLOAD-ID|string|是|无|分块上传ID|block upload id|

- 响应体

``` json
{
  "code" : 0,
  "message" : null,
  "data" : null,
  "traceId" : ""
}
```

- 响应字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误|0:success, other: failure|
|message|string|错误消息|the failure message|
|data|null|null|null|
|traceId|string|请求跟踪id|the trace id|

### 查询已上传的分块列表

- API: GET /generic/block/{project}/{repo}/{path}
- API 名称: list_upload_block
- 功能说明：
	- 中文：查询已上传的分块列表
	- English：list upload block

- 请求体

此接口请求体为空

- 请求参数

此接口无请求参数

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
|X-BKREPO-UPLOAD-ID|string|是|无|分块上传ID|block upload id|

- 响应体

``` json
{
  "code" : 0,
  "message" : null,
  "data" : [ {
    "size" : 10240,
    "sha256" : "d17f25ecfbcc7857f7bebea469308be0b2580943e96d13a3ad98a13675c4bfc2",
    "sequence" : 1
  }, {
    "size" : 10240,
    "sha256" : "cc399d73903f06ee694032ab0538f05634ff7e1ce5e8e50ac330a871484f34cf",
    "sequence" : 2
  } ],
  "traceId" : ""
}
```

- 响应字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误 |0:success, other: failure|
|message|string|错误消息 |the failure message |
|data|list|分块列表|block list|
|traceId|string|请求跟踪id|the trace id|

- 分块信息字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|size|long|分块大小|block size|
|sha256|string|分块sha256|block sha256 checksum|
|sequence|int|分块序号|block sequence|

### 下载通用制品文件

- API: GET /generic/{project}/{repo}/{path}
- API 名称: download
- 功能说明：
	- 中文：下载通用制品文件
	- English：download generic file

- 请求体

此接口请求体为空

- 请求参数

此接口无请求参数

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
|Range|string|否|无|RFC 2616 中定义的字节范围，范围值必须使用 bytes=first-last 格式且仅支持单一范围，不支持多重范围。first 和 last 都是基于0开始的偏移量。例如 bytes=0-9，表示下载对象的开头10个字节的数据；bytes=5-9，表示下载对象的第6到第10个字节。此时返回 HTTP 状态码206（Partial Content）及 Content-Range 响应头部。如果 first 超过对象的大小，则返回 HTTP 状态码416（Requested Range Not Satisfiable）错误。如果不指定，则表示下载整个对象|bytes range|

- 响应头

| 字段|类型|说明|Description|
|---|---|---|---|
|Accept-Ranges|string|RFC 2616 中定义的服务器接收Range范围|RFC 2616 Accept-Ranges|
|Cache-Control|string|RFC 2616 中定义的缓存指令|RFC 2616 Cache-Control|
|Connection|string|RFC 2616 中定义，表明响应完成后是否会关闭网络连接。枚举值：keep-alive，close。|RFC 2616 Connection|
|Content-Disposition|string|RFC 2616 中定义的文件名称|RFC 2616 Content-Disposition|
|Content-Length|long|RFC 2616 中定义的 HTTP 响应内容长度（字节）|RFC 2616 Content Length|
|Content-Range|string|RFC 2616 中定义的返回内容的字节范围，仅当请求中指定了 Range 请求头部时才会返回该头部|RFC 2616 Content-Range|
|Content-Type|string|RFC 2616 中定义的 HTTP 响应内容类型（MIME）|RFC 2616 Content Length|
|Date|string|RFC 1123 中定义的 GMT 格式服务端响应时间，例如Mon, 27 Jul 2020 08:51:59 GMT|RFC 1123 Content Length|
|Etag|string|ETag 全称为 Entity Tag，是文件被创建时标识对象内容的信息标签，可用于检查对象的内容是否发生变化，通用制品文件会返回文件的sha256值|ETag, file sha256 checksum|
|Last-Modified|string|文件的最近一次上传的时间，例如Mon, 27 Jul 2020 08:51:58 GMT|file last modified time|

- 响应体

``` json
[文件流]
```

### 获取通用制品文件头部信息

- API: HEAD /generic/{project}/{repo}/{path}
- API 名称: head
- 功能说明：
	- 中文：获取通用制品文件头部信息
	- English：get generic file head info

- 请求体

此接口请求体为空

- 请求参数

此接口无请求参数

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
|Range|string|否|无|RFC 2616 中定义的字节范围，范围值必须使用 bytes=first-last 格式且仅支持单一范围，不支持多重范围。first 和 last 都是基于0开始的偏移量。例如 bytes=0-9，表示下载对象的开头10个字节的数据；bytes=5-9，表示下载对象的第6到第10个字节。此时返回 HTTP 状态码206（Partial Content）及 Content-Range 响应头部。如果 first 超过对象的大小，则返回 HTTP 状态码416（Requested Range Not Satisfiable）错误。如果不指定，则表示下载整个对象|bytes range|

- 响应头

| 字段|类型|说明|Description|
|---|---|---|---|
|Accept-Ranges|string|RFC 2616 中定义的服务器接收Range范围|RFC 2616 Accept-Ranges|
|Cache-Control|string|RFC 2616 中定义的缓存指令|RFC 2616 Cache-Control|
|Connection|string|RFC 2616 中定义，表明响应完成后是否会关闭网络连接。枚举值：keep-alive，close。|RFC 2616 Connection|
|Content-Disposition|string|RFC 2616 中定义的文件名称|RFC 2616 Content-Disposition|
|Content-Length|long|RFC 2616 中定义的 HTTP 响应内容长度（字节）|RFC 2616 Content Length|
|Content-Range|string|RFC 2616 中定义的返回内容的字节范围，仅当请求中指定了 Range 请求头部时才会返回该头部|RFC 2616 Content-Range|
|Content-Type|string|RFC 2616 中定义的 HTTP 响应内容类型（MIME）|RFC 2616 Content Length|
|Date|string|RFC 1123 中定义的 GMT 格式服务端响应时间，例如Mon, 27 Jul 2020 08:51:59 GMT|RFC 1123 Content Length|
|Etag|string|ETag 全称为 Entity Tag，是文件被创建时标识对象内容的信息标签，可用于检查对象的内容是否发生变化，通用制品文件会返回文件的sha256值|ETag, file sha256 checksum|
|Last-Modified|string|文件的最近一次上传的时间，例如Mon, 27 Jul 2020 08:51:58 GMT|file last modified time|

- 响应体

此接口响应体为空

### 删除通用制品文件

- API: DELETE /generic/{project}/{repo}/{path}
- API 名称: delete
- 功能说明：
	- 中文：删除通用制品文件
	- English：delete generic file

- 请求体

此接口请求体为空

- 请求参数

此接口无请求参数

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
  "data" : null,
  "traceId" : ""
}
```

- 响应字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误|0:success, other: failure|
|message|string|错误消息|the failure message|
|data|null|null|null|
|traceId|string|请求跟踪id|the trace id|