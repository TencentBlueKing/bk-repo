## bkrepo router-controller相关接口

### 增加路由策略

- API: POST /router-controller/api/router/admin/policy
- API 名称: add_policy
- 功能说明：
  - 中文：增加路由策略
  - English：add policy

- input body:

``` 
{
    "users": ["user1", "user2"],
    "projectIds": ["project1", "project2"],
    "destRouterNodeId": "proxy1"
}
```

- input 字段说明

| 字段 | 类型   | 是否必须 | 默认值 | 说明     | Description   |
| ---- | ------ | -------- | ------ | -------- | ------------- |
| users | list | 是       | 无     | 需要路由的用户 | the users  |
| projectIds  | list | 是       | 无     | 需要路由的项目     | the project id list |
| destRouterNodeId | string | 是       | 无     | proxy名 | the proxy name  |


- output:

```
{
    "code": 0,
    "message": null,
    "data": {
        "id": "xxxx",
        "createdBy": "admin",
        "createdDate": "",
        "users": ["user1", "user2"],
        "projectIds": ["project1", "project2"],
        "destRouterNodeId": "proxy1"
    },
    "traceId": "4d617888730986188f0ed7564269728d"
}


```

- output 字段说明

| 字段    | 类型           | 说明                                    | Description               |
| ------- | -------------- | --------------------------------------- | ------------------------- |
| code    | bool           | 错误编码。 0表示success，>0表示失败错误 | 0:success, other: failure |
| message | result message | 错误消息                                | the failure message       |
| data    | object         | result data                             | the data for response     |
| traceId | string         | 请求跟踪id                              | the trace id              |


### 删除路由策略

- API: DELETE /router-controller/api/router/admin/policy
- API 名称: delete_policy
- 功能说明：
  - 中文：删除路由策略
  - English：delete policy

- input body:

``` json
    {
        "policyId": "xxx"
    }
```

- input 字段说明

| 字段 | 类型   | 是否必须 | 默认值 | 说明   | Description |
| ---- | ------ | -------- | ------ | ------ | ----------- |
| policyId   | string | 是       | 无     | 路由策略ID | the policy id  |

- output:

```
{
    "code":0,
    "data":null,
    "message":"",
    "traceId":""
}

```

- output 字段说明

| 字段    | 类型           | 说明                                    | Description               |
| ------- | -------------- | --------------------------------------- | ------------------------- |
| code    | bool           | 错误编码。 0表示success，>0表示失败错误 | 0:success, other: failure |
| message | result message | 错误消息                                | the failure message       |
| data    | object         | result data                             | the data for response     |
| traceId | string         | 请求跟踪id                              | the trace id              |