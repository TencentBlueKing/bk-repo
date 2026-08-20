# 作品分享接口

[toc]

作品分享权限是独立于仓库 `READ` 的访问域：`/mine`、`/accessible`、打开/短链以及后续预览/下载临时 token 均以分享可见性为准，不校验仓库读权限。访问记录会写入服务日志（userId、shareId、visibility、projectId、repoName）。

名称搜索对 `artifactName` 做转义后的大小写不敏感包含匹配（MongoDB `$regex`）。数据量大时可能扫描，不返回总数。

## 创建或更新作品分享

- API: POST /preview/api/artifact/share
- API 名称: artifact_share_upsert
- 功能说明:
  - 中文: 创建或更新当前用户作为作品创建者的分享配置
  - English: create or update artifact share owned by current user
- 鉴权: 需要登录；调用方必须是作品节点创建者
- 请求体
  ```json
  {
    "projectId": "demo",
    "repoName": "drive-local",
    "resourceId": 123,
    "visibility": "CUSTOM",
    "userIds": ["bob"],
    "orgIds": ["display-dept-1"]
  }
  ```
- 请求字段说明

  | 字段 | 类型 | 是否必须 | 默认值 | 说明 | Description |
  | --- | --- | --- | --- | --- | --- |
  | projectId | string | 是 | 无 | 项目 ID | project id |
  | repoName | string | 是 | 无 | 仓库名 | repo name |
  | resourceId | long | 是 | 无 | Drive inode | drive inode |
  | visibility | string | 是 | 无 | `PUBLIC` 或 `CUSTOM` | visibility |
  | userIds | list | 否 | [] | 指定用户；`PUBLIC` 时忽略并清空 | authorized user ids |
  | orgIds | list | 否 | [] | 指定组织展示 ID；`PUBLIC` 时忽略并清空 | authorized org display ids |

- `CUSTOM` 必须至少指定一个用户或部门。同一 `projectId + repoName + shareKind + resourceType + resourceId` 仅允许一条记录。
- 更新已有分享时保留当前作品名；新建时从节点元数据读取。
- `featured` 是独立于 `visibility` 的平台精选标记，用户分享接口不写入；更新时保留已有值。
- 作品类型 `type` 来自节点元数据 `IMATE_ARTIFACT_TYPE`；缺失时按 `fullPath` 扩展名回退。取值与搜索接口一致：`image` / `pdf` / `html` / `code` / `table` / `slides` / `markdown` / `video` / `audio`。

## 重命名作品分享

- API: PUT /preview/api/artifact/share/{shareId}/name
- API 名称: artifact_share_rename
- 功能说明:
  - 中文: 修改当前用户创建的分享展示名称。不重新签发访问 token
  - English: rename an owned share display name without reissuing tokens
- 鉴权: 需要登录；仅创建者可改名。路径不含 projectId/repoName，在服务内校验创建者
- 请求体
  ```json
  {
    "artifactName": "新站点名称"
  }
  ```
- 请求字段说明

  | 字段 | 类型 | 是否必须 | 默认值 | 说明 | Description |
  | --- | --- | --- | --- | --- | --- |
  | artifactName | string | 是 | 无 | 去首尾空白后不能为空 | display name |

- 分享不存在返回未找到；非创建者返回无权限。

## 我创建的作品分享列表

- API: GET /preview/api/artifact/share/mine
- API 名称: artifact_share_list_mine
- 功能说明:
  - 中文: 查询当前用户创建的分享，返回完整可编辑信息（不含 token）
  - English: list shares created by current user
- 鉴权: 需要登录；不校验仓库 READ
- 请求参数

  | 字段 | 类型 | 是否必须 | 默认值 | 说明 | Description |
  | --- | --- | --- | --- | --- | --- |
  | keyword | string | 否 | 无 | 按作品名包含匹配，空表示不筛选 | artifact name contains |
  | cursor | string | 否 | 无 | 不透明游标；非法值返回 400 | opaque cursor |
  | limit | int | 否 | 100 | 页大小，最大 500 | page size |

- 响应 data

  | 字段 | 类型 | 说明 | Description |
  | --- | --- | --- | --- |
  | records | list | 分享完整信息 | share details |
  | nextCursor | string | 下一页游标，无更多时为 null | next cursor |
  | limit | int | 本次页大小 | page size |

- 排序：`lastModifiedDate DESC, id DESC`。游标不含快照语义，翻页期间被更新的记录可能重复或遗漏。

## 我有权限访问的作品列表

- API: GET /preview/api/artifact/share/accessible
- API 名称: artifact_share_list_accessible
- 功能说明:
  - 中文: 查询当前用户可访问的作品摘要。权限为公开、创建者、指定用户、指定组织（含祖先部门）的并集
  - English: list artifacts accessible to current user
- 鉴权: 需要登录；以分享权限为准，不校验仓库 READ
- 请求参数: 与 `/mine` 相同，另可选 `channel`、`featured`
  - 缺省：公开 + 创建者本人 + 指定用户 + 指定组织（含祖先部门）的并集
  - `ALL`：公开 + 指定用户 + 指定组织
  - `PUBLIC`：社区公开
  - `CUSTOM`：指定分享（点名用户或部门命中）
  - `featured=true`：只返回 `featured=true` 的记录，与 `channel` 独立叠加；精选不授予额外权限
  - 非法 `channel` 返回 400
- 响应 records 仅含摘要：`shareId`、`shareKind`、`projectId`、`repoName`、`resourceId`、`artifactName`、`type`、`fullPath`、`downloadToken`、`createdBy`、`agentId`、`featured`、`lastModifiedDate`
- `downloadToken` 供调用方拼接 Drive 临时下载：`GET /web/fs-server/drive/temporary/download/{projectId}/{repoName}{fullPath}?token={downloadToken}`；记录尚未签发 token 时为 null

## 撤销分享

- API: DELETE /preview/api/artifact/share/{shareId}
- API 名称: artifact_share_revoke
- 功能说明:
  - 中文: 真实删除分享记录并废止临时 token。记录不存在时仍返回成功；非创建者返回无权限
  - English: hard-delete share; missing record is success
- 鉴权: 需要登录；仅创建者可删除

## 打开分享

- API: GET /preview/api/artifact/share/{shareId}/open
- API 名称: artifact_share_open
- 功能说明:
  - 中文: 校验当前用户是否在分享可见范围内后返回预览/下载 URL
  - English: open share after share-permission check
- 鉴权: 需要登录；公开分享任意已登录用户可打开；CUSTOM 需为创建者、指定用户，或所属组织命中指定组织（含祖先部门）。不校验仓库 READ。打开接口不返回指定用户/组织列表。

## 短链打开

- API: GET /preview/api/artifact/share/a/{shareId}
- API 名称: artifact_share_open_redirect
- 功能说明:
  - 中文: 与打开分享使用相同的分享权限校验，通过后返回内嵌预览 HTML。网关把 `/a/{shareId}` 反代到本接口，不 302，地址栏保持短链
  - English: same share-permission check as open, then 200 HTML that embeds the preview without changing the short URL
- 鉴权: 与打开分享相同；不校验仓库 READ。浏览器访问 `/a/{shareId}` 未登录时，网关回落前端页并由 `getLoginUrl` 跳转企业登录，`c_url` 为该短链；登录后继续打开。独立部署弹出前端登录框
- 响应: HTTP 200，`text/html`；iframe 加载 UTF-8 百分号编码后的预览地址
