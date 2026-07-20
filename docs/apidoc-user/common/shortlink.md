# 短链接接口

[toc]

## 短链接跳转

- API: GET /t/{code}
- 后端实际路径: GET /repository/api/shortlink/{code}
- API 名称: redirect_short_link
- 功能说明：
  - 中文：根据短码 302 跳转到目标 URL（匿名可访问）
  - English：Redirect to target URL by short code (anonymous allowed)
- 路径参数

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |code|string|是|无|8 位短码|short code|

- 响应说明

  |HTTP Status|说明|
  |---|---|
  |302|跳转到目标 URL，`Location` 为绝对地址|
  |404|短码不存在|
  |410|短链已过期|

## 查询短链接

- API: GET /web/repository/api/shortlink/info/{code}
- 后端实际路径: GET /repository/api/shortlink/info/{code}
- API 名称: get_short_link
- 功能说明：
  - 中文：按短码查询短链接详情（含已过期记录，仅管理员）
  - English：Get short link by code including expired records (admin only)
- 鉴权：管理员（`PrincipalType.ADMIN`）
- 路径参数

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |code|string|是|无|8 位短码|short code|

- 响应字段

  |字段|类型|说明|Description|
  |---|---|---|---|
  |code|string|短码|short code|
  |target|string|目标 URL（相对路径或绝对内部 URL）|target URL|
  |shortUrl|string|完整短链地址|full short URL|
  |expiredDate|string|过期时间|expired datetime|
  |createdBy|string|创建人|creator|
  |createdDate|string|创建时间|created datetime|

- 响应说明

  |HTTP Status|说明|
  |---|---|
  |200|查询成功|
  |404|短码不存在|

## 删除短链接

- API: DELETE /web/repository/api/shortlink/{code}
- 后端实际路径: DELETE /repository/api/shortlink/{code}
- API 名称: delete_short_link
- 功能说明：
  - 中文：按短码硬删短链接（仅管理员）
  - English：Hard delete short link by code (admin only)
- 鉴权：管理员（`PrincipalType.ADMIN`）
- 路径参数

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |code|string|是|无|8 位短码|short code|

- 响应说明

  |HTTP Status|说明|
  |---|---|
  |200|删除成功|
  |404|短码不存在|

## 按创建人分页查询短链接

- API: GET /web/repository/api/shortlink/list
- 后端实际路径: GET /repository/api/shortlink/list
- API 名称: list_short_link
- 功能说明：
  - 中文：按创建人分页查询短链接（仅管理员）
  - English：Page short links by creator (admin only)
- 鉴权：管理员（`PrincipalType.ADMIN`）
- 查询参数

  |字段|类型|是否必须|默认值|说明|Description|
  |---|---|---|---|---|---|
  |createdBy|string|是|无|创建人|creator|
  |pageNumber|int|否|1|页码，从 1 开始|page number|
  |pageSize|int|否|20|每页大小|page size|

- 响应字段：分页结构，`records` 元素同「查询短链接」响应字段

- 响应说明

  |HTTP Status|说明|
  |---|---|
  |200|查询成功|

- 备注
  - 网关 `/t/{code}` 无鉴权，反代到 repository 服务
  - 创建由各服务直接注入 `ShortLinkService` / `RShortLinkService` 调用，不提供用户态创建 HTTP 接口
  - 配置项前缀：`shortlink`（`public-host`、`allowed-hosts`、`default-ttl-days`、`max-ttl-days`）
