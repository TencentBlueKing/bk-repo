## Node节点接口说明

Node接口使用统一接口协议，公共部分请参照[通用接口协议说明](./common.md)

#### 查询节点详情

- API: GET /repository/api/node/{project}/{repo}/{path}
- API 名称: query_node_detail
- 功能说明：
  - 中文：查询节点详情
  - English：query node detail

- 请求体
  此接口请求体为空

- 请求字段说明

| 字段    | 类型   | 是否必须 | 默认值 | 说明     | Description  |
| ------- | ------ | -------- | ------ | -------- | ------------ |
| project | string | 是       | 无     | 项目名称 | project name |
| repo    | string | 是       | 无     | 仓库名称 | repo name    |
| path    | string | 是       | 无     | 完整路径 | full path    |

- 响应体

``` json
{
  "code": 0,
  "message": null,
  "data": {
    "nodeInfo": {
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
    "metadata": {
      "key": "value"
    }
  },
  "traceId": ""
}
```

- data字段说明

| 字段     | 类型   | 说明                        | Description      |
| -------- | ------ | --------------------------- | ---------------- |
| nodeInfo | object | 节点信息                    | node information |
| metadata | object | 节点元数据，key-value键值对 | node metadata    |

- nodeInfo字段说明

| 字段             | 类型   | 说明         | Description          |
| ---------------- | ------ | ------------ | -------------------- |
| createdBy        | string | 创建者       | create user          |
| createdDate      | string | 创建时间     | create time          |
| lastModifiedBy   | string | 上次修改者   | last modify user     |
| lastModifiedDate | string | 上次修改时间 | last modify time     |
| folder           | bool   | 是否为文件夹 | is folder            |
| path             | string | 节点目录     | node path            |
| name             | string | 节点名称     | node name            |
| fullPath         | string | 节点完整路径 | node full path       |
| size             | long   | 节点大小     | file size            |
| sha256           | string | 节点sha256   | file sha256          |
| md5              | string | 节点md5      | file md5 checksum    |
| projectId        | string | 节点所属项目 | node project id      |
| repoName         | string | 节点所属仓库 | node repository name |

#### 分页查询节点

- API: GET /repository/api/node/page/{project}/{repo}/{path}?page=0&size=20&includeFolder=true&deep=false
- API 名称: list_node_page
- 功能说明：
  - 中文：分页查询节点
  - English：list node page
- 请求体
  此接口请求体为空
- 请求字段说明

| 字段          | 类型    | 是否必须 | 默认值 | 说明               | Description       |
| ------------- | ------- | -------- | ------ | ------------------ | ----------------- |
| project       | string  | 是       | 无     | 项目名称           | project name      |
| repo          | string  | 是       | 无     | 仓库名称           | repo name         |
| path          | string  | 是       | 无     | 完整路径           | full path         |
| page          | int     | 是       | 0      | 当前页             | current page      |
| size          | int     | 是       | 20     | 分页大小           | page size         |
| includeFolder | boolean | 否       | true   | 是否包含目录       | is include folder |
| deep          | boolean | 否       | false  | 是否查询子目录节点 | query deep node   |

- 响应体

```json
{
  "code": 0,
  "message": null,
  "data": {
    "count": 18,
    "page": 0,
    "pageSize": 1,
    "totalPages": 18,
    "records": [
      {
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
      }
    ]
  },
  "traceId": ""
}
```

- record字段说明

| 字段             | 类型   | 说明         | Description          |
| ---------------- | ------ | ------------ | -------------------- |
| createdBy        | string | 创建者       | create user          |
| createdDate      | string | 创建时间     | create time          |
| lastModifiedBy   | string | 上次修改者   | last modify user     |
| lastModifiedDate | string | 上次修改时间 | last modify time     |
| folder           | bool   | 是否为文件夹 | is folder            |
| path             | string | 节点目录     | node path            |
| name             | string | 节点名称     | node name            |
| fullPath         | string | 节点完整路径 | node full path       |
| size             | long   | 节点大小     | file size            |
| sha256           | string | 节点sha256   | file sha256          |
| md5              | string | 节点md5      | file md5 checksum    |
| projectId        | string | 节点所属项目 | node project id      |
| repoName         | string | 节点所属仓库 | node repository name |

#### 创建目录

- API: POST /repository/api/node/{project}/{repo}/{path}
- API 名称: mkdir
- 功能说明：
  - 中文：创建目录节点
  - English：create directory node
- 请求体
  此接口请求体为空
- 请求字段说明

| 字段    | 类型   | 是否必须 | 默认值 | 说明     | Description  |
| ------- | ------ | -------- | ------ | -------- | ------------ |
| project | string | 是       | 无     | 项目名称 | project name |
| repo    | string | 是       | 无     | 仓库名称 | repo name    |
| path    | string | 是       | 无     | 完整路径 | full path    |

- 响应体

```json
{
  "code": 0,
  "message": null,
  "data": null,
  "traceId": ""
}
```

- data字段说明

  请求成功无返回数据

#### 删除节点

- API: DELETE /repository/api/node/{project}/{repo}/{path}
- API 名称: delete_node
- 功能说明：
  - 中文：删除节点，同时支持删除目录和文件节点
  - English：delete node
- 请求体
  此接口请求体为空
- 请求字段说明

| 字段    | 类型   | 是否必须 | 默认值 | 说明     | Description  |
| ------- | ------ | -------- | ------ | -------- | ------------ |
| project | string | 是       | 无     | 项目名称 | project name |
| repo    | string | 是       | 无     | 仓库名称 | repo name    |
| path    | string | 是       | 无     | 完整路径 | full path    |

- 响应体

```json
{
  "code": 0,
  "message": null,
  "data": null,
  "traceId": ""
}
```

- data字段说明

  请求成功无返回数据

#### 更新节点

- API: POST /repository/api/node/update
- API 名称: update_node
- 功能说明：
  - 中文：更新节点信息，目前支持修改文件过期时间
  - English：update node info
- 请求体

```json
{
  "projectId": "",
  "repoName": "",
  "fullPath": "",
  "expires": 0
}
```

- 请求字段说明

| 字段      | 类型   | 是否必须 | 默认值 | 说明                            | Description  |
| --------- | ------ | -------- | ------ | ------------------------------- | ------------ |
| projectId | string | 是       | 无     | 项目名称                        | project name |
| repoName  | string | 是       | 无     | 仓库名称                        | repo name    |
| fullPath  | string | 是       | 无     | 完整路径                        | full path    |
| expires   | long   | 否       | 0      | 过期时间，单位天(0代表永久保存) | expires day  |

- 响应体

```json
{
  "code": 0,
  "message": null,
  "data": null,
  "traceId": ""
}
```

- data字段说明

  请求成功无返回数据

#### 重命名节点

- API: POST /repository/api/node/rename
- API 名称: rename_node
- 功能说明：
  - 中文：重命名节点
  - English：rename node
- 请求体

```json
{
  "projectId": "",
  "repoName": "",
  "fullPath": "",
  "expires": 0
}
```

- 请求字段说明

| 字段        | 类型   | 是否必须 | 默认值 | 说明         | Description  |
| ----------- | ------ | -------- | ------ | ------------ | ------------ |
| projectId   | string | 是       | 无     | 项目名称     | project name |
| repoName    | string | 是       | 无     | 仓库名称     | repo name    |
| fullPath    | string | 是       | 无     | 完整路径     | full path    |
| newFullPath | string | 是       | 无     | 新的完整路径 | expires day  |

- 响应体

```json
{
  "code": 0,
  "message": null,
  "data": null,
  "traceId": ""
}
```

- data字段说明

  请求成功无返回数据

#### 移动节点

- API: POST /repository/api/node/move
- API 名称: move_node
- 功能说明：
  - 中文：移动节点
  - English：move node
- 请求体

```json
{
  "srcProjectId": "",
  "srcRepoName": "",
  "srcFullPath": "",
  "destProjectId": "",
  "destRepoName": "",
  "destFullPath": "",
  "overwrite": false
}
```

- 请求字段说明

| 字段          | 类型    | 是否必须 | 默认值 | 说明                         | Description          |
| ------------- | ------- | -------- | ------ | ---------------------------- | -------------------- |
| srcProjectId  | string  | 是       | 无     | 源项目名称                   | src project name     |
| srcRepoName   | string  | 是       | 无     | 源仓库名称                   | src repo name        |
| srcFullPath   | string  | 是       | 无     | 源完整路径                   | src full path        |
| destProjectId | string  | 否       | null   | 目的项目名称，null表示源项目 | dest project name    |
| destRepoName  | string  | 否       | null   | 目的仓库名称，null表示源仓库 | dest repo name       |
| destFullPath  | string  | 是       | 无     | 目的完整路径                 | dest full path       |
| overwrite     | boolean | 否       | false  | 同名文件是否覆盖             | overwrite  same node |

- 响应体

```json
{
  "code": 0,
  "message": null,
  "data": null,
  "traceId": ""
}
```

- data字段说明

  请求成功无返回数据

#### 拷贝节点

- API: POST /repository/api/node/copy
- API 名称: copy_node
- 功能说明：
  - 中文：拷贝节点
  - English：copy node
- 请求体

```json
{
  "srcProjectId": "",
  "srcRepoName": "",
  "srcFullPath": "",
  "destProjectId": "",
  "destRepoName": "",
  "destFullPath": "",
  "overwrite": false
}
```

- 请求字段说明

| 字段          | 类型    | 是否必须 | 默认值 | 说明                         | Description          |
| ------------- | ------- | -------- | ------ | ---------------------------- | -------------------- |
| srcProjectId  | string  | 是       | 无     | 源项目名称                   | src project name     |
| srcRepoName   | string  | 是       | 无     | 源仓库名称                   | src repo name        |
| srcFullPath   | string  | 是       | 无     | 源完整路径                   | src full path        |
| destProjectId | string  | 否       | null   | 目的项目名称，null表示源项目 | dest project name    |
| destRepoName  | string  | 否       | null   | 目的仓库名称，null表示源仓库 | dest repo name       |
| destFullPath  | string  | 是       | 无     | 目的完整路径                 | dest full path       |
| overwrite     | boolean | 否       | false  | 同名文件是否覆盖             | overwrite  same node |

- 响应体

```json
{
  "code": 0,
  "message": null,
  "data": null,
  "traceId": ""
}
```

- data字段说明

  请求成功无返回数据

#### 统计节点大小信息

- API: GET /repository/api/node/size/{project}/{repo}/{path}
- API 名称: compute_node_size
- 功能说明：
  - 中文：统计节点大小信息
  - English：compute node size

- 请求体
  此接口请求体为空

- 请求字段说明

| 字段    | 类型   | 是否必须 | 默认值 | 说明     | Description  |
| ------- | ------ | -------- | ------ | -------- | ------------ |
| project | string | 是       | 无     | 项目名称 | project name |
| repo    | string | 是       | 无     | 仓库名称 | repo name    |
| path    | string | 是       | 无     | 完整路径 | full path    |

- 响应体

``` json
{
  "code": 0,
  "message": null,
  "data": {
    "subNodeCount": 32,
    "size": 443022203
  },
  "traceId": ""
}
```

- data字段说明

| 字段         | 类型 | 说明          | Description         |
| ------------ | ---- | ------------- | ------------------- |
| subNodeCount | long | 创建者        | sub node count      |
| size         | long | 目录/文件大小 | directory/file size |

#### 自定义查询

- TODO

### 元数据接口

#### 查询元数据

- API: GET /repository/api/metadata/{project}/{repo}/{path}
- API 名称: query_metadata
- 功能说明：
  - 中文：查询元数据信息
  - English：query metadata info

- 请求体
  此接口请求体为空

- 请求字段说明

| 字段    | 类型   | 是否必须 | 默认值 | 说明     | Description  |
| ------- | ------ | -------- | ------ | -------- | ------------ |
| project | string | 是       | 无     | 项目名称 | project name |
| repo    | string | 是       | 无     | 仓库名称 | repo name    |
| path    | string | 是       | 无     | 完整路径 | full path    |

- 响应体

``` json
{
  "code": 0,
  "message": null,
  "data": {
    "key1": "value1",
    "key2": "value2"
  },
  "traceId": ""
}
```

- data字段说明

  键值对，key为元数据名称，value为元数据值

#### 保存（更新）元数据

- API: POST /repository/api/metadata/{project}/{repo}/{path}
- API 名称: save_metadata
- 功能说明：
  - 中文：保存（更新）元数据信息，元数据不存在则保存，存在则更新
  - English：save metadata info
- 请求体

```json
{
  "key1": "value1",
  "key2": "value2"
}
```

- 请求字段说明

| 字段    | 类型   | 是否必须 | 默认值 | 说明         | Description        |
| ------- | ------ | -------- | ------ | ------------ | ------------------ |
| project | string | 是       | 无     | 项目名称     | project name       |
| repo    | string | 是       | 无     | 仓库名称     | repo name          |
| path    | string | 是       | 无     | 完整路径     | full path          |
| key     | string | 否       | 无     | 元数据键值对 | metadata key-value |

- 响应体

``` json
{
  "code": 0,
  "message": null,
  "data": null,
  "traceId": ""
}
```

- data字段说明

  请求成功无返回数据

#### 删除元数据

- API: DELETE /repository/api/metadata/{project}/{repo}/{path}
- API 名称: delete_metadata
- 功能说明：
  - 中文：根据提供的key列表删除元数据
  - English：delete metadata info
- 请求体

```json
{
  "keyList": ["key1", "key2"]
}
```

- 请求字段说明

| 字段    | 类型   | 是否必须 | 默认值 | 说明                  | Description       |
| ------- | ------ | -------- | ------ | --------------------- | ----------------- |
| project | string | 是       | 无     | 项目名称              | project name      |
| repo    | string | 是       | 无     | 仓库名称              | repo name         |
| path    | string | 是       | 无     | 完整路径              | full path         |
| keyList | string | 是       | 无     | 待删除的元数据key列表 | metadata key list |

- 响应体

``` json
{
  "code": 0,
  "message": null,
  "data": null,
  "traceId": ""
}
```

- data字段说明

  请求成功无返回数据

### 分享链接

#### 创建分享下载链接

- API: POST /repository/api/share/{project}/{repo}/{path}
- API 名称: create_share_url
- 功能说明：
  - 中文：创建分享下载链接
  - English：create share url
- 请求体

```json
{
  "authorizedUserList": ["user1", "user2"],
  "authorizedIpList": ["192.168.1.1", "127.0.0.1"],
  "expireSeconds": 3600
}
```

- 请求字段说明

| 字段               | 类型   | 是否必须 | 默认值 | 说明                               | Description     |
| ------------------ | ------ | -------- | ------ | ---------------------------------- | --------------- |
| project            | string | 是       | 无     | 项目名称                           | project name    |
| repo               | string | 是       | 无     | 仓库名称                           | repo name       |
| path               | string | 是       | 无     | 完整路径                           | full path       |
| authorizedUserList | list   | 否       | []     | 授权用户列表，若为空所有用户可下载 | share user list |
| authorizedIpList   | list   | 否       | []     | 授权ip列表，若为空所有ip可下载     | share ip list   |
| expireSeconds      | long   | 否       | 0      | 下载链接有效时间，单位秒           | expire seconds  |

- 响应体

``` json
{
  "code": 0,
  "message": null,
  "data": {
    "projectId": "test",
    "repoName": "generic-local",
    "fullPath": "/test.txt",
    "shareUrl": "/api/share/test/generic-local/test.json?token=bef56a14c33342beba7fdb5f63508d24",
    "authorizedUserList": [
      "user1",
      "user2"
    ],
    "authorizedIpList": [
      "192.168.1.1",
      "127.0.0.1"
    ],
    "expireDate": "2020-08-13T12:35:38.541"
  },
  "traceId": ""
}
```

- data字段说明

| 字段               | 类型   | 说明         | Description          |
| ------------------ | ------ | ------------ | -------------------- |
| projectId          | string | 项目id       | project id           |
| repoName           | string | 仓库名称     | repo name            |
| fullPath           | string | 完整路径     | full path            |
| shareUrl           | string | 分享下载链接 | share url            |
| authorizedUserList | list   | 授权用户列表 | authorized user list |
| authorizedIpList   | list   | 授权ip列表   | authorized ip list   |
| expireDate         | string | 过期时间     | expire date          |

#### 创建分享下载链接（批量）

- API: POST /repository/api/share/batch
- API 名称: create_batch_share_url
- 功能说明：
  - 中文：创建分享下载链接（批量）
  - English：create batch share url
- 请求体

```json
{
  "projectId": "",
  "repoName": "",
  "fullPathList": "",
  "authorizedUserList": ["user1", "user2"],
  "authorizedIpList": ["192.168.1.1", "127.0.0.1"],
  "expireSeconds": 3600
}
```

- 请求字段说明

| 字段               | 类型   | 是否必须 | 默认值 | 说明                               | Description     |
| ------------------ | ------ | -------- | ------ | ---------------------------------- | --------------- |
| projectId          | string | 是       | 无     | 项目名称                           | project name    |
| repoName           | string | 是       | 无     | 仓库名称                           | repo name       |
| fullPathList       | string | 是       | 无     | 完整路径列表                       | full path list  |
| authorizedUserList | list   | 否       | []     | 授权用户列表，若为空所有用户可下载 | share user list |
| authorizedIpList   | list   | 否       | []     | 授权ip列表，若为空所有ip可下载     | share ip list   |
| expireSeconds      | long   | 否       | 0      | 下载链接有效时间，单位秒           | expire seconds  |

- 响应体

``` json
{
  "code": 0,
  "message": null,
  "data": {
    [
      "projectId": "test",
      "repoName": "generic-local",
      "fullPath": "/test.txt",
      "shareUrl": "/api/share/test/generic-local/test.json?token=bef56a14c33342beba7fdb5f63508d24",
      "authorizedUserList": [
        "user1",
        "user2"
      ],
      "authorizedIpList": [
        "192.168.1.1",
        "127.0.0.1"
      ],
      "expireDate": "2020-08-13T12:35:38.541"
    ]
  },
  "traceId": ""
}
```

- data字段说明

  ShareRecordInfo列表

- ShareRecordInfo字段说明

| 字段               | 类型   | 说明         | Description          |
| ------------------ | ------ | ------------ | -------------------- |
| projectId          | string | 项目id       | project id           |
| repoName           | string | 仓库名称     | repo name            |
| fullPath           | string | 完整路径     | full path            |
| shareUrl           | string | 分享下载链接 | share url            |
| authorizedUserList | list   | 授权用户列表 | authorized user list |
| authorizedIpList   | list   | 授权ip列表   | authorized ip list   |
| expireDate         | string | 过期时间     | expire date          |

#### 分享链接下载

- API: GET /repository/api/share/{project}/{repo}/{path}?token=bef56a14c33342beba7fdb5f63508d24
- API 名称: download_share_url
- 功能说明：
  - 中文：分享链接下载，支持HEAD操作
  - English：download by share url

- 请求体
  此接口请求体为空

- 请求字段说明

| 字段    | 类型   | 是否必须 | 默认值 | 说明     | Description    |
| ------- | ------ | -------- | ------ | -------- | -------------- |
| project | string | 是       | 无     | 项目名称 | project name   |
| repo    | string | 是       | 无     | 仓库名称 | repo name      |
| path    | string | 是       | 无     | 完整路径 | full path      |
| token   | string | 是       | 无     | 下载凭证 | download token |

- 请求头

| 字段  | 类型   | 是否必须 | 默认值 | 说明                                                         | Description |
| ----- | ------ | -------- | ------ | ------------------------------------------------------------ | ----------- |
| Range | string | 否       | 无     | RFC 2616 中定义的字节范围，范围值必须使用 bytes=first-last 格式且仅支持单一范围，不支持多重范围。first 和 last 都是基于0开始的偏移量。例如 bytes=0-9，表示下载对象的开头10个字节的数据；bytes=5-9，表示下载对象的第6到第10个字节。此时返回 HTTP 状态码206（Partial Content）及 Content-Range 响应头部。如果 first 超过对象的大小，则返回 HTTP 状态码416（Requested Range Not Satisfiable）错误。如果不指定，则表示下载整个对象 | bytes range |

- 响应头

| 字段                | 类型   | 说明                                                         | Description                  |
| ------------------- | ------ | ------------------------------------------------------------ | ---------------------------- |
| Accept-Ranges       | string | RFC 2616 中定义的服务器接收Range范围                         | RFC 2616 Accept-Ranges       |
| Cache-Control       | string | RFC 2616 中定义的缓存指令                                    | RFC 2616 Cache-Control       |
| Connection          | string | RFC 2616 中定义，表明响应完成后是否会关闭网络连接。枚举值：keep-alive，close。 | RFC 2616 Connection          |
| Content-Disposition | string | RFC 2616 中定义的文件名称                                    | RFC 2616 Content-Disposition |
| Content-Length      | long   | RFC 2616 中定义的 HTTP 响应内容长度（字节）                  | RFC 2616 Content Length      |
| Content-Range       | string | RFC 2616 中定义的返回内容的字节范围，仅当请求中指定了 Range 请求头部时才会返回该头部 | RFC 2616 Content-Range       |
| Content-Type        | string | RFC 2616 中定义的 HTTP 响应内容类型（MIME）                  | RFC 2616 Content Length      |
| Date                | string | RFC 1123 中定义的 GMT 格式服务端响应时间，例如Mon, 27 Jul 2020 08:51:59 GMT | RFC 1123 Content Length      |
| Etag                | string | ETag 全称为 Entity Tag，是文件被创建时标识对象内容的信息标签，可用于检查对象的内容是否发生变化，通用制品文件会返回文件的sha256值 | ETag, file sha256 checksum   |
| Last-Modified       | string | 文件的最近一次上传的时间，例如Mon, 27 Jul 2020 08:51:58 GMT  | file last modified time      |

- 响应体
  [文件流]

### 

