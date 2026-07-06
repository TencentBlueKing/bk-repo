# Drive 临时访问接口

[toc]

## 创建临时 token

- API: POST /drive/temporary/token/create
- API 名称: drive_temporary_token_create
- 功能说明:
  - 中文: 为 Drive 仓库创建临时访问 token
  - English: create temporary access token for drive repository
- 鉴权: 需要 JWT 登录
- 请求体
  ```json
  {
    "projectId": "demo",
    "repoName": "drive-local",
    "fullPathSet": ["/a.txt"],
    "authorizedUserSet": [],
    "authorizedIpSet": [],
    "expireSeconds": 86400,
    "permits": 10,
    "type": "DOWNLOAD",
    "snapSeq": 123
  }
  ```
- 请求字段说明

  | 字段 | 类型 | 是否必须 | 默认值 | 说明 | Description |
  | --- | --- | --- | --- | --- | --- |
  | projectId | string | 是 | 无 | 项目名称 | project name |
  | repoName | string | 是 | 无 | Drive 仓库名称 | drive repo name |
  | fullPathSet | list | 是 | 无 | 授权路径列表 | authorized path set |
  | authorizedUserSet | list | 否 | [] | 授权用户（本期不校验） | authorized user set |
  | authorizedIpSet | list | 否 | [] | 授权 IP | authorized ip set |
  | expireSeconds | long | 否 | 86400 | 有效时间（秒） | expire seconds |
  | permits | int | 否 | 无限制 | 允许访问次数 | access permits |
  | type | string | 是 | 无 | `UPLOAD` 或 `DOWNLOAD` | token type |
  | snapSeq | long | 否 | 无 | 快照序列号，整批共用一个；为空表示只读最新 | snapshot sequence |

## 创建临时访问 URL

- API: POST /drive/temporary/url/create
- API 名称: drive_temporary_url_create
- 功能说明:
  - 中文: 为 Drive 仓库创建临时访问完整 URL
  - English: create temporary access url for drive repository
- 鉴权: 需要 JWT 登录
- 请求体: 在创建 token 请求字段基础上增加 `host`（可选，未传则使用 `drive.domain` 配置）

## 临时 token 文件下载

- API: GET /drive/temporary/download/{projectId}/{repoName}/{path}?token=xxx&snapSeq=123
- API 名称: drive_temporary_download
- 功能说明:
  - 中文: 按 fullPath 临时 token 下载 Drive 文件
  - English: temporary token download drive file by full path
- 鉴权: 无需 JWT，通过 query 参数 `token` 校验
- 请求参数

  | 字段 | 类型 | 是否必须 | 说明 |
  | --- | --- | --- | --- |
  | token | string | 是 | 临时访问 token |
  | snapSeq | long | 否 | 快照序列号；token 绑定了 snapSeq 时必须匹配或不传 |

- 请求头

  | 字段 | 类型 | 是否必须 | 说明 |
  | --- | --- | --- | --- |
  | Range | string | 否 | 字节范围，格式 `bytes=start-end` |

- 响应体: [文件流]

## 临时 token 文件上传

- API: PUT /drive/temporary/upload/{projectId}/{repoName}/{path}?token=xxx
- API 名称: drive_temporary_upload
- 功能说明:
  - 中文: 按 fullPath 临时 token 上传 Drive 完整文件
  - English: temporary token upload complete drive file by full path
- 鉴权: 无需 JWT，通过 query 参数 `token` 校验
- 请求头

  | 字段 | 类型 | 是否必须 | 默认值 | 说明 |
  | --- | --- | --- | --- | --- |
  | X-BKREPO-OVERWRITE | boolean | 否 | false | 是否覆盖已存在文件 |
  | X-BKREPO-SHA256 | string | 否 | 无 | 期望文件 sha256 |

- 请求体: [文件流]
- 响应体: 与 [Drive 节点上传](./node.md) 一致，返回 `DriveNode`
