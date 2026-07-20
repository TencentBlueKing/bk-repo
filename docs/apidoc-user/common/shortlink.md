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

- 备注
  - 网关 `/t/{code}` 无鉴权，反代到 repository 服务
  - 创建/查询/删除由各服务直接注入 `ShortLinkService` / `RShortLinkService` 调用，不提供用户态创建 HTTP 接口
  - 配置项前缀：`shortlink`（`public-host`、`allowed-hosts`、`default-ttl-days`、`max-ttl-days`）
