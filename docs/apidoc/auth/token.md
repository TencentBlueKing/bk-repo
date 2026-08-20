## bkrepo token相关接口

### 新增用户token

- API:POST  /auth/api/user/token/{uid}/{name}?expiredAt=2019-12-21T09:46:37.877Z&projectId=aaa
- API 名称: add_user_token
- 功能说明：
	- 中文：新增用户token
	- English：add user token

- input body:

``` json

```

- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|name|string|是|无|token名|the token name|
|uid|string|是|无|用户id|the user id|
|expiredAt|datetime|否|无|过期时间|expiredAt|
|projectId|aaa|否|无|项目ID|projectId|

- output:

```
{
    "code":0,
    "data":{
        "createdAt":"2019-12-21T09:46:37.877Z",
        "expiredAt":"2019-12-21T09:46:37.877Z",
        "id":"abcd",
        "name":"abcd"
    },
    "message":"string",
    "traceId":"string"
}

```

- output 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误 |0:success, other: failure|
|message|result message|错误消息 |the failure message |
|data | bool | result data |the data for response|
|traceId|string|请求跟踪id|the trace id|


- data 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|createdAt|time|创建时间|create time|
|expiredAt|time|过期时间,null标识永久 |expired time |
|name | string | token name|the name of token|
|id|string|token id |the id of token|

### 用户token列表

- API:GET  /auth/api/user/list/token/{uid}
- API 名称: list_user_token
- 功能说明：
	- 中文：新增用户token
	- English：add user token

- input body:

``` json

```

- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|uid|string|是|无|用户id|the user id|

- output:

```
{
    "code":0,
    "data":[
        {
            "createdAt":"2019-12-21T09:46:37.877Z",
            "expiredAt":"2019-12-21T09:46:37.877Z",
            "name":"token1"
        },
        {
            "createdAt":"2019-12-21T09:46:37.877Z",
            "expiredAt":null,
            "name":"token2"
        }
    ],
    "message":"string",
    "traceId":"string"
}
```

- output 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误 |0:success, other: failure|
|message|result message|错误消息 |the failure message |
|data | bool | result data |the data for response|
|traceId|string|请求跟踪id|the trace id|


- data 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|createdAt|time|创建时间|create time|
|expiredAt|time|过期时间 |expired time |
|name | string | token name|the name of token|
|id|string|token id |the id of token|

### 删除用户token

- API:DELETE  /auth/api/user/token/{uid}/{name}
- API 名称: delete_user_token
- 功能说明：
	- 中文：删除用户token
	- English：delete user token

- input body:

``` json

```

- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|uid|string|是|无|用户id|the user id|
|name|string|是|无|用户token|the user token|

- output:

```
{
    "code":0,
    "data":{
        "admin":true,
        "locked":true,
        "name":"string",
        "pwd":"string",
        "roles":[
            "string"
        ],
        "tokens":[
            {
                "createdAt":"2019-12-21T09:46:37.877Z",
                "expiredAt":"2019-12-21T09:46:37.877Z",
                "name":"string"
            }
        ],
        "uid":"string"
    },
    "message":"string",
    "traceId":"string"
}

```

- output 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误 |0:success, other: failure|
|message|result message|错误消息 |the failure message |
|data | bool | result data |the data for response|
|traceId|string|请求跟踪id|the trace id|

### 校验用户token

- API:POST /auth/api/user/token
- API 名称: check_user_token
- 功能说明：
	- 中文：校验用户token
	- English：check user token

- input body:

``` json

```

- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|uid|string|是|无|用户id|the user id|
|token|string|是|无|用户token|the user token|

- output:

```
{
    "code":0,
    "message":null,
    "data":true,
    "traceId":""
}

```

- output 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误 |0:success, other: failure|
|message|string|错误消息,或者用户token |the failure message,or bkrepo token |
|data | bool | result data |the data for response|
|traceId|string|请求跟踪id|the trace id|

### 获取用户信息

- API:GET /auth/api/user/info
- API 名称: get_user_info
- 功能说明：
	- 中文：获取当前登录用户信息（含显示名、租户 ID、时区）。displayName / tenantId / timeZone 由网关（`bkrepo.web.conf`）在 `auth_request` 阶段解析登录态后通过响应头 `x-bkrepo-display-name` / `x-bkrepo-tenant-id` / `x-bkrepo-time-zone` 注入，后端从请求头读取并透传。
	- English：Get current login user info (with display name, tenant id and default time zone). The `displayName` / `tenantId` / `timeZone` fields are injected by the gateway via response headers during the `auth_request` phase, and passed through by the backend.

- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|x-bkrepo-uid|header|是|无|用户id|the user id|
|bkrepo_ticket|cookie|是|无|用户token|the user token|

- output:

```
{
    "code":0,
    "message":null,
    "data":{
        "userId":"owenlxu2",
        "displayName":"Owen Lxu",
        "tenantId":"default",
        "timeZone":"Asia/Shanghai"
    },
    "traceId":""
}

```

- output 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|int|错误编码。 0表示success，>0表示失败错误 |0:success, other: failure|
|message|string|错误消息 |the failure message|
|data | object | result data |the data for response|
|traceId|string|请求跟踪id|the trace id|


- data字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|userId | string | 用户id |the user Id|
|displayName | string | 用户显示名 |the user display name|
|tenantId | string | 企业空间/租户 ID（仅多租户模式下有值） |the tenant id (only in multi-tenant mode)|
|timeZone | string | 默认时区（IANA 名称，如 `Asia/Shanghai`，由网关注入） |the default time zone (IANA name, injected by gateway)|
