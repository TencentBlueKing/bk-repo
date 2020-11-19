## bkrepo 权限相关接口

### 创建权限

- API: POST /auth/api/permission/create
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

- API: DELETE /auth/api/permission/delete/{id}
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

- API: POST /auth/api/permission/ckeck
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

- API: GET /auth/api/permission/checkAdmin/{uid}
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

- API: PUT /auth/api/permission/excludePath/{id}

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

- API: PUT /auth/api/permission/includePath/{id}
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

- API: GET /auth/api/permission/list?projectId=ops&resourceType=REPO
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

- API: PUT /auth/api/permission/repo/{id}
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

- API: POST /auth/api/permission/user/{id}/{uid}
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

- API: POST /auth/api/permission/role/{id}/{rid}
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

- API: DELETE /auth/api/permission/role/{id}/{rid}
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

- API: DELETE /auth/api/permission/user/{id}/{uid}
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

