### 创建用户

- API: POST /api/auth/user/create
- API 名称: create_user
- 功能说明：
	- 中文：创建用户
	- English：create user

- input body:

``` json
{
    "admin":true,
    "name":"string",
    "pwd":"string",
    "userId":"string"
}

```


- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|name|string|是|无|用户名|the  name|
|pwd|string|是|无|用户密码|the user password|
|userId|string|是|无|用户id|the user id|
|admin|bool|否|false|是否管理员|is admin|

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


### 创建用户到项目管理员

- API: POST /api/auth/user/create/project
- API 名称: create_user_to_project
- 功能说明：
	- 中文：创建用户
	- English：create user to project

- input body:

``` json
{
    "admin":true,
    "name":"string",
    "pwd":"string",
    "userId":"string",
    "asstUsers":[
        "owen",
        "necr"
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
|admin|bool|否|false|是否管理员|is admin|
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

### 用户详情

- API:GET /api/auth/user/detail/{uid}
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
            "abcdegfffff"
        ],
        "tokens":[
            {
                "createdAt":"2019-12-21T08:47:36.656Z",
                "expiredAt":"2019-12-21T08:47:36.656Z",
                "id":"ssss-deeedd"
            }
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

### 删除用户

- API:DELETE /api/auth/user/{uid}
- API 名称: delete_user
- 功能说明：
	- 中文：删除用户
	- English：delete user

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

### 更新用户信息

- API:PUT  /api/auth/user/{uid}
- API 名称: update_user
- 功能说明：
	- 中文：更新用户信息
	- English：update user

- input body:

``` json
{
  "admin": true,
  "name": "string",
  "pwd": "string"
}

```


- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|name|string|是|无|用户名|the  name|
|pwd|string|是|无|用户密码|the user password|
|uid|string|是|无|用户id|the user id|
|admin|bool|否|false|是否管理员|is admin|

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


### 删除用户所属角色

- API:DELETE  /api/auth/user/role/{uid}/{rid}
- API 名称: delete_user_role
- 功能说明：
	- 中文：删除用户所属角色
	- English：delete user role

- input body:

``` json

```


- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|uid|string|是|无|用户id|the user id|
|rid|string|是|无|角色主键ID|the role key id|

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
                "id":"string"
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

### 新增用户所属角色

- API:POST  /api/auth/user/role/{uid}/{rid}
- API 名称: add_user_role
- 功能说明：
	- 中文：新增用户所属角色
	- English：add user role

- input body:

``` json

```

- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|uid|string|是|无|用户id|the user id|
|rid|string|是|无|角色主键ID|the role key id|

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
                "id":"string"
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


### 新增用户token

- API:POST  /api/auth/user/token/{uid}
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
|uid|string|是|无|用户id|the user id|

- output:

```
{
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
                "id":"string"
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

### 删除用户token

- API:DELETE  /api/auth/user/token/{uid}/{token}
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
|token|string|是|无|用户token|the user token|

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
                "id":"string"
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

### 校验用户token

- API:GET /api/auth/user/token/{uid}/{token}
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

### 批量新增用户所属角色

- API:PATCH /api/auth/user/role/add/{rid}
- API 名称: patch_create_user_role
- 功能说明：
	- 中文：批量新增用户所属角色
	- English：add user role patch

- input body:

``` json
[
    "owen",
    "tt"
]
```

- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|uid|string array|是|无|用户id数组|the user id array|
|rid|string|是|无|角色主键ID|the role token|

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

### 批量删除用户所属角色

- API:PATCH /api/auth/user/role/add/{rid}
- API 名称: patch_delete_user_role
- 功能说明：
	- 中文：批量删除用户所属角色
	- English：delete user role patch

- input body:

``` json
[
    "owen",
    "tt"
]
```

- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|uid|string array|是|无|用户id数组|the user id array|
|rid|string|是|无|角色主键ID|the role token|

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




### 新增集群

- API: POST /api/auth/cluster/add
- API 名称: add_cluster
- 功能说明：
	- 中文：新增集群
	- English：add cluster

- input body:


``` json
{
    "clusterAddr":"${domain}",
    "cert":"-----BEGIN CERTIFICATE-----
MIID7TCCAtWgAwIBAgIJAIrUulvdIEJ/MA0GCSqGSIb3DQEBCwUAMIGMMQswCQYD
VQQGEwJjbjELMAkGA1UECAwCZ2QxCzAJBgNVBAcMAnN6MRAwDgYDVQQKDAd0ZW5j
ZW50MQswCQYDVQQLDAJiazEgMB4GA1UEAwwXYXV0aC50ZXN0LmJrcmVwby5vYS5j
b20xIjAgBgkqhkiG9w0BCQEWE293ZW5seHVAdGVuY2VudC5jb20wHhcNMjAwMzAz
MDkxNTU5WhcNMjAwNDAyMDkxNTU5WjCBjDELMAkGA1UEBhMCY24xCzAJBgNVBAgM
AmdkMQswCQYDVQQHDAJzejEQMA4GA1UECgwHdGVuY2VudDELMAkGA1UECwwCYmsx
IDAeBgNVBAMMF2F1dGgudGVzdC5ia3JlcG8ub2EuY29tMSIwIAYJKoZIhvcNAQkB
FhNvd2VubHh1QHRlbmNlbnQuY29tMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIB
CgKCAQEAxmd2xtB44LxTE2eKl5qskmTf4/Rm9l9c1jJeVoBqaLl5XzlwRl5K3xBt
PCYkSdegNveTcnG8NGAsLPVwHLYwbf6WHZY3TOtovZG9KFXmNdnKBR9KgQq/NmCe
TwkT7HBrF7l5BtTziwd1+4NKB+DWcI1rwSTRkd66Kzj7SDrZP6pFz9Oq9G2lBuIx
2Dsqpo0V4CI1EPZV83LCbw7MP5ip7+edilvyyWuVhHFV2Q9Bd266flcrdSZ06n1/
BVbvILQQ3Aif8+OYOQR3pvrwQOpuwEIitxOkNaABg8AotgO+QviDhqi0mrxclGpl
s9E3SDBWHAMrOhqAoX29GNYzOchbCwIDAQABo1AwTjAdBgNVHQ4EFgQU1vfUyGB9
h2VTopptm+nqquwk+4IwHwYDVR0jBBgwFoAU1vfUyGB9h2VTopptm+nqquwk+4Iw
DAYDVR0TBAUwAwEB/zANBgkqhkiG9w0BAQsFAAOCAQEAmdxJD2Pyq6kelkkqT9kb
S31KzN6xU8Xtvn5GMNavlN6pje15skG986EL+J8KhyyitONitPxW0Pb3b22rqfp8
6G0dFlx1njF7tVKeFZfqnLTol5yp2h0LQmdQKgniePFD7SQmv2HUoqgCMH5iYXR2
tCShdiWFaOod2jwfEgXBxSnAtn2o+pGKc8eNs35a8JlV8q8XHbgeeXkWGz7w5yUw
IvikEd2luq0dCZYNhal06sNIrvKvDA/XMCrMnbe9YEbkm0qqxRchSfqVcvashQFW
l3+iyoEIMpDGXQIZMoSWDYnSGql1pGGROFAbv6SukZuGMO8tqnlKWE8fSyb0hDz3
Sw==
-----END CERTIFICATE-----",
    "clusterId":"test_cluster",
    "credentialStatus":false
}
```


- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|clusterAddr|string|是|无|集群地址|the cluster address|
|cert|string|是|无|集群访问证书|the cluster cert|
|clusterId|string|是|无|集群 id|the cluster id|
|credentialStatus|bool|是|false|认证状态|the cluster credential status|



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

### 查询所有集群

- API: POST /api/auth/cluster/list
- API 名称: list_cluster
- 功能说明：
	- 中文：列出所有集群
	- English：list cluster

- input body:

``` json

```

- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|


- output:

```
{
  "code": 0,
  "message": null,
  "data": [
    {
      "clusterId": "test_cluster",
      "clusterAddr": "${domain}",
      "cert": "-----BEGIN CERTIFICATE-----\nMIID7TCCAtWgAwIBAgIJAIrUulvdIEJ/MA0GCSqGSIb3DQEBCwUAMIGMMQswCQYD\nVQQGEwJjbjELMAkGA1UECAwCZ2QxCzAJBgNVBAcMAnN6MRAwDgYDVQQKDAd0ZW5j\nZW50MQswCQYDVQQLDAJiazEgMB4GA1UEAwwXYXV0aC50ZXN0LmJrcmVwby5vYS5j\nb20xIjAgBgkqhkiG9w0BCQEWE293ZW5seHVAdGVuY2VudC5jb20wHhcNMjAwMzAz\nMDkxNTU5WhcNMjAwNDAyMDkxNTU5WjCBjDELMAkGA1UEBhMCY24xCzAJBgNVBAgM\nAmdkMQswCQYDVQQHDAJzejEQMA4GA1UECgwHdGVuY2VudDELMAkGA1UECwwCYmsx\nIDAeBgNVBAMMF2F1dGgudGVzdC5ia3JlcG8ub2EuY29tMSIwIAYJKoZIhvcNAQkB\nFhNvd2VubHh1QHRlbmNlbnQuY29tMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIB\nCgKCAQEAxmd2xtB44LxTE2eKl5qskmTf4/Rm9l9c1jJeVoBqaLl5XzlwRl5K3xBt\nPCYkSdegNveTcnG8NGAsLPVwHLYwbf6WHZY3TOtovZG9KFXmNdnKBR9KgQq/NmCe\nTwkT7HBrF7l5BtTziwd1+4NKB+DWcI1rwSTRkd66Kzj7SDrZP6pFz9Oq9G2lBuIx\n2Dsqpo0V4CI1EPZV83LCbw7MP5ip7+edilvyyWuVhHFV2Q9Bd266flcrdSZ06n1/\nBVbvILQQ3Aif8+OYOQR3pvrwQOpuwEIitxOkNaABg8AotgO+QviDhqi0mrxclGpl\ns9E3SDBWHAMrOhqAoX29GNYzOchbCwIDAQABo1AwTjAdBgNVHQ4EFgQU1vfUyGB9\nh2VTopptm+nqquwk+4IwHwYDVR0jBBgwFoAU1vfUyGB9h2VTopptm+nqquwk+4Iw\nDAYDVR0TBAUwAwEB/zANBgkqhkiG9w0BAQsFAAOCAQEAmdxJD2Pyq6kelkkqT9kb\nS31KzN6xU8Xtvn5GMNavlN6pje15skG986EL+J8KhyyitONitPxW0Pb3b22rqfp8\n6G0dFlx1njF7tVKeFZfqnLTol5yp2h0LQmdQKgniePFD7SQmv2HUoqgCMH5iYXR2\ntCShdiWFaOod2jwfEgXBxSnAtn2o+pGKc8eNs35a8JlV8q8XHbgeeXkWGz7w5yUw\nIvikEd2luq0dCZYNhal06sNIrvKvDA/XMCrMnbe9YEbkm0qqxRchSfqVcvashQFW\nl3+iyoEIMpDGXQIZMoSWDYnSGql1pGGROFAbv6SukZuGMO8tqnlKWE8fSyb0hDz3\nSw==\n-----END CERTIFICATE-----",
      "credentialStatus": true
    }
  ],
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

### 删除集群

- API: DELETE /api/auth/cluster/{clusterId}

- API 名称: delete_cluster
- 功能说明：
	- 中文：删除集群
	- English：delete cluster

- input body:

``` json

```

- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|clusterId|string|是|无|集群Id|the cluster id|

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

### 修改集群

- API: GET /api/auth/cluster/{clusterId}

- API 名称: update_cluster
- 功能说明：
	- 中文：更新集群信息
	- English  update cluster

- input body:

``` json
{
    "clusterAddr":"${domain}",
    "cert":"-----BEGIN CERTIFICATE-----
MIID7TCCAtWgAwIBAgIJAIrUulvdIEJ/MA0GCSqGSIb3DQEBCwUAMIGMMQswCQYD
VQQGEwJjbjELMAkGA1UECAwCZ2QxCzAJBgNVBAcMAnN6MRAwDgYDVQQKDAd0ZW5j
ZW50MQswCQYDVQQLDAJiazEgMB4GA1UEAwwXYXV0aC50ZXN0LmJrcmVwby5vYS5j
b20xIjAgBgkqhkiG9w0BCQEWE293ZW5seHVAdGVuY2VudC5jb20wHhcNMjAwMzAz
MDkxNTU5WhcNMjAwNDAyMDkxNTU5WjCBjDELMAkGA1UEBhMCY24xCzAJBgNVBAgM
AmdkMQswCQYDVQQHDAJzejEQMA4GA1UECgwHdGVuY2VudDELMAkGA1UECwwCYmsx
IDAeBgNVBAMMF2F1dGgudGVzdC5ia3JlcG8ub2EuY29tMSIwIAYJKoZIhvcNAQkB
FhNvd2VubHh1QHRlbmNlbnQuY29tMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIB
CgKCAQEAxmd2xtB44LxTE2eKl5qskmTf4/Rm9l9c1jJeVoBqaLl5XzlwRl5K3xBt
PCYkSdegNveTcnG8NGAsLPVwHLYwbf6WHZY3TOtovZG9KFXmNdnKBR9KgQq/NmCe
TwkT7HBrF7l5BtTziwd1+4NKB+DWcI1rwSTRkd66Kzj7SDrZP6pFz9Oq9G2lBuIx
2Dsqpo0V4CI1EPZV83LCbw7MP5ip7+edilvyyWuVhHFV2Q9Bd266flcrdSZ06n1/
BVbvILQQ3Aif8+OYOQR3pvrwQOpuwEIitxOkNaABg8AotgO+QviDhqi0mrxclGpl
s9E3SDBWHAMrOhqAoX29GNYzOchbCwIDAQABo1AwTjAdBgNVHQ4EFgQU1vfUyGB9
h2VTopptm+nqquwk+4IwHwYDVR0jBBgwFoAU1vfUyGB9h2VTopptm+nqquwk+4Iw
DAYDVR0TBAUwAwEB/zANBgkqhkiG9w0BAQsFAAOCAQEAmdxJD2Pyq6kelkkqT9kb
S31KzN6xU8Xtvn5GMNavlN6pje15skG986EL+J8KhyyitONitPxW0Pb3b22rqfp8
6G0dFlx1njF7tVKeFZfqnLTol5yp2h0LQmdQKgniePFD7SQmv2HUoqgCMH5iYXR2
tCShdiWFaOod2jwfEgXBxSnAtn2o+pGKc8eNs35a8JlV8q8XHbgeeXkWGz7w5yUw
IvikEd2luq0dCZYNhal06sNIrvKvDA/XMCrMnbe9YEbkm0qqxRchSfqVcvashQFW
l3+iyoEIMpDGXQIZMoSWDYnSGql1pGGROFAbv6SukZuGMO8tqnlKWE8fSyb0hDz3
Sw==
-----END CERTIFICATE-----",
    "credentialStatus":false
}
```

- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|clusterAddr|string|是|无|集群地址|the cluster address|
|cert|string|是|无|集群访问证书|the cluster cert|
|clusterId|string|是|无|集群 id|the cluster id|
|credentialStatus|bool|是|false|认证状态|the cluster credential status|

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
|data | object | result data |the data for response|
|traceId|string|请求跟踪id|the trace id|

### ping集群状态

- API: GET /api/auth/cluster/ping/{clusterId}
- API 名称: ping_cluster
- 功能说明：
	- 中文：ping cluster认证状态
	- English：ping cluster

- input body:

``` json

```

- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|clusterId|string|是|无|集群 id|the cluster id|

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
|data | boool | result data |the data for response|
|traceId|string|请求跟踪id|the trace id|

### 集群认证

- API: GET /api/auth/cluster/credential


- API 名称: cluster_credential
- 功能说明：
	- 中文：集群认证状态
	- English：cluster credential status

- input body:

``` json

```

- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|

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
|data | boool | result data |the data for response|
|traceId|string|请求跟踪id|the trace id|



### 创建访问账号

- API: POST /api/auth/account/create
- API 名称: create_account
- 功能说明：
	- 中文：创建访问账号
	- English：create account

- input body:

``` json
{
  "appId": "string",
  "locked": true
}

```
- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|appId|string|是|无|应用ID|the application id|
|locked|bool|否|false|是否锁定|the account status|

- output:

```
{
    "code":0,
    "data":{
        "appId":"string",
        "credentials":[
            {
                "accessKey":"string",
                "createdAt":"2019-12-21T16:54:58.749Z",
                "secretKey":"string",
                "status":"ENABLE"
            }
        ],
        "locked":true
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
|data | object | result data |the data for response|
|traceId|string|请求跟踪id|the trace id|

- credentials字段说明
| 字段|类型|说明|Description|
|---|---|---|---|
|accessKey|string|accessKey |accessKey|
|secretKey|string|secretKey|secretKey |
|createdAt | date time | 创建时间 |the create time|
|status|ENUM|[ENABLE,DISABLE]|[ENABLE,DISABLE]|


### 删除访问账号

- API: DELETE /api/auth/account/delete/{appId}
- API 名称: delete_account
- 功能说明：
	- 中文：删除访问账号
	- English：delete account

- input body:

``` json

```
- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|appId|string|是|无|应用ID|the application id|

- output:

```
{
    "code":0,
    "data":true,
    "message":"",
    "traceId":""
}

```

- output 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误 |0:success, other: failure|
|message|result message|错误消息 |the failure message |
|data | object | result data |the data for response|
|traceId|string|请求跟踪id|the trace id|

### 更新访问状态访问账号

- API: PUT /api/auth/account/{appId}/{locked}
- API 名称: update_account_status
- 功能说明：
	- 中文：删除访问账号
	- English：delete account

- input body:

``` json

```
- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|appId|string|是|无|应用ID|the application id|
|locked|bool|是|无|账号锁定状态|the account locked status|

- output:

```
{
    "code":0,
    "data":true,
    "message":"",
    "traceId":""
}

```

- output 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误 |0:success, other: failure|
|message|result message|错误消息 |the failure message |
|data | object | result data |the data for response|
|traceId|string|请求跟踪id|the trace id|

### 查询所有访问账号

- API: GET /api/auth/account/
- API 名称: list_account
- 功能说明：
	- 中文：查询所有访问账号
	- English：list account

- input body:

``` json

```
- input 字段说明


- output:

```
{
    "code":0,
    "data":[
        {
            "appId":"bkdevops",
            "credentials":[
                {
                    "accessKey":"aaaassveee",
                    "createdAt":"2019-12-22T10:33:11.957Z",
                    "secretKey":"ssdverrrr",
                    "status":"ENABLE"
                }
            ],
            "locked":true
        }
    ],
    "message":"",
    "traceId":""
}


```

- output 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误 |0:success, other: failure|
|message|result message|错误消息 |the failure message |
|data | object array| result data |the data for response|
|traceId|string|请求跟踪id|the trace id|

### 获取账号下的ak/sk对

- API: GET /api/auth/account/credential/list/{appId}
- API 名称: list_account_credential
- 功能说明：
	- 中文：查询账号的认证方式
	- English：list account credential

- input body:

``` json

```
- input 字段说明

- output:

```
{
    "code":0,
    "data":[
        {
            "accessKey":"aaaa",
            "createdAt":"2019-12-22T10:33:11.929Z",
            "secretKey":"vbbbbb",
            "status":"ENABLE"
        }
    ],
    "message":"",
    "traceId":""
}

```

- output 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误 |0:success, other: failure|
|message|result message|错误消息 |the failure message |
|data | object array| result data |the data for response|
|traceId|string|请求跟踪id|the trace id|

### 创建账号下的ak/sk对

- API: POST /api/auth/account/credential/{appId}
- API 名称: create_account_credential
- 功能说明：
	- 中文：查询账号的认证方式
	- English：create account credential

- input body:

``` json

```
- input 字段说明
|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|appId|string|是|无|应用ID|the application id|

- output:

```
{
    "code":0,
    "data":[
        {
            "accessKey":"aaaaa",
            "createdAt":"2019-12-22T10:50:37.073Z",
            "secretKey":"cccccc",
            "status":"ENABLE"
        }
    ],
    "message":"",
    "traceId":""
}

```

- output 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误 |0:success, other: failure|
|message|result message|错误消息 |the failure message |
|data | object array| result data |the data for response|
|traceId|string|请求跟踪id|the trace id|

### 删除账号下的ak/sk对

- API: DELETE /api/auth/account/credential/{appId}/{acessKey}
- API 名称: delete_account_credential
- 功能说明：
	- 中文：删除账号下的ak/sk对
	- English：delete account credential

- input body:

``` json

```
- input 字段说明
|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|appId|string|是|无|应用ID|the application id|
|acessKey|string|是|无|acessKey|acessKey|

- output:

```
{
    "code":0,
    "data":[
        {
            "accessKey":"aaaaa",
            "createdAt":"2019-12-22T10:50:37.073Z",
            "secretKey":"cccccc",
            "status":"ENABLE"
        }
    ],
    "message":"",
    "traceId":""
}

```

- output 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误 |0:success, other: failure|
|message|result message|错误消息 |the failure message |
|data | object array| result data |the data for response|
|traceId|string|请求跟踪id|the trace id|

### 校验账号下的ak/sk对

- API: GET /api/auth/account/credential/{appId}/{acessKey}/{secretKey}
- API 名称: check_account_credential
- 功能说明：
	- 中文：校验账号下的ak/sk对
	- English：check account credential

- input body:

``` json

```
- input 字段说明
|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|appId|string|是|无|应用ID|the application id|
|acessKey|string|是|无|acessKey|acessKey|
|secretKey|string|是|无|secretKey|secretKey|

- output:

```
{
    "code":0,
    "data":true,
    "message":"",
    "traceId":""
}


```

- output 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误 |0:success, other: failure|
|message|result message|错误消息 |the failure message |
|data | object array| result data |the data for response|
|traceId|string|请求跟踪id|the trace id|

### 更新账号下的ak/sk对状态

- API: PUT /api/auth/account/credential/{appId}/{acessKey}/{status}
- API 名称: update_account_credential_status
- 功能说明：
	- 中文：更新账号下的ak/sk对状态
	- English：update account credential status

- input body:

``` json

```
- input 字段说明
|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|appId|string|是|无|应用ID|the application id|
|acessKey|string|是|无|acessKey|acessKey|
|status|ENUM|是|无|[ ENABLE, DISABLE]|[ ENABLE, DISABLE]|

- output:

```
{
    "code":0,
    "data":true,
    "message":"",
    "traceId":""
}


```

- output 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误 |0:success, other: failure|
|message|result message|错误消息 |the failure message |
|data | object array| result data |the data for response|
|traceId|string|请求跟踪id|the trace id|


### 创建权限

- API: POST /api/auth/permission/create
- API 名称: create_permission
- 功能说明：
	- 中文：创建权限
	- English：create permission

- input body:

``` json
{
    "createBy":"owen",
    "excludePattern":[
        "/index"
    ],
    "includePattern":[
        "/path1"
    ],
    "permName":"perm1",
    "projectId":"ops",
    "repos":[
        "owen"
    ],
    "resourceType":"PROJECT",
    "roles":[
        {
            "id":"abcdef",
            "action":[
                "MANAGE"
            ]
        }
    ],
    "users":[
        {
            "id":"owen",
            "action":[
                "MANAGE"
            ]
        }
    ]
}


```


- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|createBy|string|是|无|创建人|the man create it|
|excludePattern|string array|否|empty array|排除路径|the exclude path|
|includePattern|string array|否|empty array|包含路径|the include path|
|projectId|string|否|null|项目ID|the project id|
|repos|string array|否|empty array|关联仓库列表|the associate repo list|
|resourceType|ENUM|是|REPO|权限类型[REPO,PROJECT,SYSTEM,NODE]|permission type [REPO,PROJECT,SYSTEM,NODE]|
|users|object array|否|empty array|权限授权用户|the auth user|
|roles|object array|否|empty array|权限授权角色|the auth role|

- roles,user字段说明
|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|id|string|是|无|用户ID/角色主键ID|the user id or role key id|
|action|ENUM array|否|empty array|权限列表|the permission action array|

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

### 删除权限

- API: DELETE /api/auth/permission/delete/{id}
- API 名称: delete_permission
- 功能说明：
	- 中文：删除权限
	- English：delete permission

- input body:

``` json


```


- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|id|string|是|无|权限主键ID|the permission key id|


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

### 校验权限

- API: POST /api/auth/permission/ckeck
- API 名称: check_permission
- 功能说明：
	- 中文：校验权限
	- English：check permission

- input body:

``` json
{
    "action":"MANAGE",
    "path":"/index",
    "projectId":"ops",
    "repoName":"docker-local",
    "resourceType":"PROJECT",
    "uid":"owen"
}


```

- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|action|ENUM|是|无|动作|the action|
|path|string|否|无|路径|the path|
|projectId|string|是|无|项目ID|the project id |
|repoName|string|否|无|仓库名|the name of repo |
|resourceType|ENUM|是|REPO|权限类型[REPO,PROJECT,SYSTEM,NODE]|permission type [REPO,PROJECT,SYSTEM,NODE]|
|uid|string|是|无|用户ID|the user id|

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

### 校验管理员

- API: GET /api/auth/permission/ckeckAdmin/{uid}
- API 名称: check_admin
- 功能说明：
	- 中文：校验管理员权限
	- English：check admin permission

- input body:

``` json

```

- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|uid|string|是|无|用户ID|the user id|

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

### 更新权限排除路径

- API: PUT /api/auth/permission/excludePath/{id}

- API 名称: update_exclude_path
- 功能说明：
	- 中文：更新权限排除路径
	- English：update permission exclude path

- input body:

``` json
[
    "/path1",
    "/path2"
]
```

- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|id|string|是|无|权限主键ID|the permission key  id|

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

### 更新权限包含路径

- API: PUT /api/auth/permission/includePath/{id}
- API 名称: update_include_path
- 功能说明：
	- 中文：更新权限包含路径
	- English：update permission include path
- input body:

``` json
[
    "/path1",
    "/path2"
]
```

- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|id|string|是|无|权限主键ID|the permission key  id|

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

### 权限列表

- API: GET /api/auth/permission/list?projectId=ops&resourceType=REPO
- API 名称: list_permission
- 功能说明：
	- 中文：权限列表
	- English：the permission list
- input body:

``` json

```

- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|projectId|string|否|无|项目ID|the project id|
|resourceType|ENUM|否|无|权限类型[REPO,PROJECT,SYSTEM,NODE]|permission type [REPO,PROJECT,SYSTEM,NODE]|

- output:
```
{
    "code":0,
    "data":[
        {
            "createAt":"2019-12-21T09:46:37.792Z",
            "createBy":"string",
            "excludePattern":[
                "/index"
            ],
            "id":"abcdef",
            "includePattern":[
                "/path1"
            ],
            "permName":"perm1",
            "projectId":"ops",
            "repos":[
                "docker-local"
            ],
            "resourceType":"PROJECT",
            "roles":[
                {
                    "id":"string",
                    "action":[
                        "MANAGE"
                    ]
                }
            ],
            "updateAt":"2019-12-21T09:46:37.792Z",
            "updatedBy":"string",
            "users":[
                {
                    "id":"owen",
                    "action":[
                        "MANAGE"
                    ]
                }
            ]
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
|data | object array | result data,具体字段见创建请求 |the data for response|
|traceId|string|请求跟踪id|the trace id|

### 更新权限绑定仓库

- API: PUT /api/auth/permission/repo/{id}
- 功能说明：
	- 中文：更新权限绑定仓库
	- English：update permission repo
- input body:

``` json
[
    "docker-local1",
    "docker-remote1"
]
```
- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|id|string|是|无|权限主键ID|the permission key  id|

- output:

```

```
- output 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误 |0:success, other: failure|
|message|result message|错误消息 |the failure message |
|data | object array | result data,具体字段见创建请求 |the data for response|
|traceId|string|请求跟踪id|the trace id|

### 更新用户绑定权限

- API: POST /api/auth/permission/user/{id}/{uid}
- 功能说明：
	- 中文：更新用户绑定权限
	- English：update user permission
- input body:

``` json
[
    "MANAGE",
    "DELETE"
]
```
- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|id|string|是|无|权限主键ID|the permission key  id|
|uid|string|是|无|用户ID|the user id|

- output:

```

```
- output 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误 |0:success, other: failure|
|message|result message|错误消息 |the failure message |
|data | object array | result data,具体字段见创建请求 |the data for response|
|traceId|string|请求跟踪id|the trace id|

### 更新角色绑定权限

- API: POST /api/auth/permission/role/{id}/{rid}
- 功能说明：
	- 中文：更新角色绑定权限
	- English：update role permission
- input body:

``` json
[
    "MANAGE",
    "DELETE"
]
```
- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|id|string|是|无|权限主键ID|the permission key  id|
|rid|string|是|无|角色主键ID|the role key id|

- output:

```

```
- output 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误 |0:success, other: failure|
|message|result message|错误消息 |the failure message |
|data | object array | result data,具体字段见创建请求 |the data for response|
|traceId|string|请求跟踪id|the trace id|


### 删除角色绑定权限

- API: DELET /api/auth/permission/role/{id}/{rid}
- 功能说明：
	- 中文：删除角色权限
	- English：delete role permission
- input body:

``` json
[
    "MANAGE",
    "DELETE"
]
```
- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|id|string|是|无|权限主键ID|the permission key  id|
|rid|string|是|无|角色主键ID|the role key id|

- output:

```

```
- output 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误 |0:success, other: failure|
|message|result message|错误消息 |the failure message |
|data | object array | result data,具体字段见创建请求 |the data for response|
|traceId|string|请求跟踪id|the trace id|

### 删除用户绑定权限

- API: DELET /api/auth/permission/user/{id}/{uid}
- 功能说明：
	- 中文：删除角色权限
	- English：delete role permission
- input body:

``` json
[
    "MANAGE",
    "DELETE"
]
```
- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|id|string|是|无|权限主键ID|the permission key  id|
|uid|string|是|无|用户ID|the user id|

- output:

```

```
- output 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误 |0:success, other: failure|
|message|result message|错误消息 |the failure message |
|data | object array | result data,具体字段见创建请求 |the data for response|
|traceId|string|请求跟踪id|the trace id|




### 创建角色

- API: POST /api/auth/role/create
- API 名称: create_role
- 功能说明：
	- 中文：创建角色
	- English：create role

- input body:


``` json
{
    "name":"运维",
    "projectId":"ops",
    "rid":"operation",
    "type":"PROJECT",
    "admin":false
}
```


- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|name|string|是|无|角色名|the role name|
|projectId|string|是|无|项目id|the project id|
|rid|string|是|无|角色id|the role id|
|type|ENUM|是|无|角色类型[REPO,PROJECT]|the type of role[REPO,PROJECT]|
|admin|bool|否|falase|是否管理员|is admin|



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


### 删除角色

- API: DELETE /api/auth/role/delete/{id}

- API 名称: delete_role
- 功能说明：
	- 中文：删除角色
	- English：delete role

- input body:

``` json

```

- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|id|string|是|无|角色主键id|the role key id|

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

### 角色详情

- API: GET /api/auth/role/detail/{id}

- API 名称: role_detail
- 功能说明：
	- 中文：角色详情
	- English ：role detail

- input body:

``` json

```

- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|id|string|是|无|角色主键id|the role key id|

- output:

```
{
    "code":0,
    "message":null,
    "data":{
        "admin":false,
        "id":"aaaaaaaa",
        "name":"运维",
        "projectId":"ops",
        "rid":"op",
        "type":"PROJECT"
    },
    "traceId":""
}


```
- output 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误 |0:success, other: failure|
|message|result message|错误消息 |the failure message |
|data | object | result data |the data for response|
|traceId|string|请求跟踪id|the trace id|

### 角色列表

- API: GET /api/auth/role/list?projectId=ops&type=PROJECT

- API 名称: role_list
- 功能说明：
	- 中文：角色列表
	- English：list role

- input body:

``` json

```

- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|projectId|string|否|无|项目id|the project id|
|type|ENUM|否|无|角色类型|the type of role[REPO,PROJECT]|

- output:

```
{
    "code":0,
    "message":null,
    "data":[
        {
            "admin":false,
            "id":"aaaaaaaa",
            "name":"运维",
            "projectId":"ops",
            "rid":"op",
            "type":"PROJECT"
        }
    ],
    "traceId":""
}


```
- output 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误 |0:success, other: failure|
|message|result message|错误消息 |the failure message |
|data | object array | result data |the data for response|
|traceId|string|请求跟踪id|the trace id|



