## bkrepo 用户相关接口

### 创建用户

- API: POST /auth/api/user/create
- API 名称: create_user
- 功能说明：
	- 中文：创建用户
	- English：create user

- input body:

``` json
{
    "userId":"public",
    "name":"public",
    "pwd":"blueking",
    "group":true,
    "asstUsers":["tt"]
}

```


- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|name|string|是|无|用户名|the  name|
|pwd|string|否|无|用户密码|the user password|
|userId|string|是|无|用户id|the user id|
|asstUsers|string array|否|[]|关联用户|association user|
|group|boot |否|false|是否群组账号|is group user|
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
|message|result message|错误消息 |the failure message |
|data | bool | result data |the data for response|
|traceId|string|请求跟踪id|the trace id|


### 创建用户到项目管理员

- API: POST /auth/api/user/create/project
- API 名称: create_user_to_project
- 功能说明：
	- 中文：创建用户
	- English：create user to project

- input body:

``` json
{
    "name":"string",
    "pwd":"string",
    "userId":"string",
    "asstUsers":[
        "owen"
    ],
    "group":true,
    "projectId":"test"
}
```


- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|name|string|是|无|用户名|the  name|
|pwd|string|是|无|用户密码|the user password|
|userId|string|是|无|用户id|the user id|
|asstUsers|string array|否|[]|关联用户|association user|
|group|boot |否|false|是否群组账号|is group user|
|projectId|string|是|无|关联到的项目|the association project|

- output:

```
{
"code": 0,
"message": null,
"data": true,
"traceId": ""
}

```

- output 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误 |0:success, other: failure|
|message|result message|错误消息 |the failure message |
|data | bool | result data |the data for response|
|traceId|string|请求跟踪id|the trace id|


### 创建用户到仓库管理员

- API: POST /auth/api/user/create/repo
- API 名称: create_user_to_repo
- 功能说明：
	- 中文：创建用户为仓库管理员
	- English：create user to repo

- input body:

``` json
{
    "name":"string",
    "pwd":"string",
    "userId":"string",
    "asstUsers":[
        "owen",
        "necr"
    ],
    "group":true,
    "projectId":"test",
    "repoName":"generic"
}
```


- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|name|string|是|无|用户名|the  name|
|pwd|string|是|无|用户密码|the user password|
|userId|string|是|无|用户id|the user id|
|asstUsers|string array|否|[]|关联用户|association user|
|group|boot |否|false|是否群组账号|is group user|
|projectId|string|是|无|关联到的项目|the association project|
|repoName|string|是|无|关联到的仓库|the association repo|

- output:

```
{
"code": 0,
"message": null,
"data": true,
"traceId": ""
}

```

- output 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误 |0:success, other: failure|
|message|result message|错误消息 |the failure message |
|data | bool | result data |the data for response|
|traceId|string|请求跟踪id|the trace id|


### 用户详情

- API:GET /auth/api/user/detail/{uid}
- API 名称: user_detail
- 功能说明：
	- 中文：用户详情
	- English：user detail

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
    "data":{
        "admin":true,
        "locked":true,
        "name":"string",
        "pwd":"string",
        "roles":[
            "632d9e127er87746e2320df1c"
        ],
        "tokens":[
        ],
        "uid":"owen"
    },
    "message":"",
    "traceId":""
}


```

- output 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误 |0:success, other: failure|
|message|result message|错误消息 |the failure message |
|data | object | user data info |the info of user|
|traceId|string|请求跟踪id|the trace id|

- data 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|roles|string array|用户所属的角色列表 |the user role list|
|tokens|object array|用户创建的所有token |the tokens of user |

- tokens 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|id|string |token id |the token id|
|createdAt|date time| token创建时间|create time |
|expiredAt|date time|token失效 |expire time |

                           |