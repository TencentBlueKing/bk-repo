## bkrepo proxy相关接口

### 创建proxy

- API: POST /auth/api/proxy/create
- API 名称: create_proxy
- 功能说明：
  - 中文：创建proxy
  - English：create proxy

- input body:

``` 
{
    "projectId": "demo",
    "clusterName": "center",
    "displayName": "xxx",
    "domain": "http://xxx",
    "syncTimeRange": "0-24",
    "syncRateLimit": -1,
    "cacheExpireDays": 7
}
```

- input 字段说明

| 字段 | 类型   | 是否必须 | 默认值 | 说明     | Description   |
| ---- | ------ | -------- | ------ | -------- | ------------- |
| projectId | string | 是       | 无     | 项目Id | the project id  |
| clusterName  | string | 是       | 无     | 集群名称     | the cluster name |
| displayName | string | 是       | 无     | proxy展示名 | the display name  |
| domain | string | 是       | 无     | proxy访问域名 | the domain  |
| syncTimeRange | string | 是       | 0-24     | proxy同步时间范围 | the sync time range  |
| syncRateLimit | int | 否       | -1     | proxy同步速率限制，单位字节，-1表示不限制 | the sync rate limit  |
| cacheExpireDays | int | 否     | 7     | proxy缓存过期天数 | the cache expire days   |


- output:

```
{
    "code": 0,
    "message": null,
    "data": {
        "name": "557c8d7574",
        "displayName": "xxx",
        "projectId": "xxx",
        "clusterName": "center",
        "domain": "http://xxx",
        "ip": "Unknown",
        "status": "CREATE",
        "syncRateLimit": -1,
        "syncTimeRange": "0-24",
        "cacheExpireDays": 7
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


### 删除proxy

- API: DELETE /auth/api/proxy/delete/{projectId}/{name}
- API 名称: delete_proxy
- 功能说明：
  - 中文：删除proxy
  - English：delete proxy

- input body:

``` json

```

- input 字段说明

| 字段 | 类型   | 是否必须 | 默认值 | 说明   | Description |
| ---- | ------ | -------- | ------ | ------ | ----------- |
| projectId   | string | 是       | 无     | 项目ID | the project id  |
| name   | string | 是       | 无     | proxy名 | the proxy name  |

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

### 分页查询Proxy列表

- API: GET /auth/api/proxy/page/{projectId}?name=xxx&displayName=xxx&pageNumber=1&pageSize=20
- API 名称: list_proxy
- 功能说明：
  - 中文：分页查询Proxy列表
  - English：list proxy


- input 字段说明
| 字段 | 类型   | 是否必须 | 默认值 | 说明   | Description |
| ---- | ------ | -------- | ------ | ------ | ----------- |
| projectId   | string | 是       | 无     | 项目ID | the project id  |
| name   | string | 否       | 无     | proxy名 | the proxy name  |
| displayName   | string | 否       | 无     | proxy展示名 | the proxy display name  |
| pageNumber   | int | 是       | 无     | 页数 | the page number  |
| pageSize   | int | 是       | 无     | 页大小 | the page size  |


- output:

```
{
    "code": 0,
    "message": null,
    "data": {
        "pageNumber": 1,
        "pageSize": 20,
        "totalRecords": 1,
        "totalPages": 1,
        "records": [
            {
                "name": "0y54vD",
                "displayName": "test",
                "projectId": "demo",
                "clusterName": "test",
                "domain": "http://127.0.0.1",
                "ip": "127.0.0.1",
                "status": "OFFLINE",
                "syncRateLimit": 1024,
                "syncTimeRange": "0-24",
                "cacheExpireDays": 7
            }
        ],
        "count": 1,
        "page": 1
    },
    "traceId": "6bfc5132a349072726d688764a8b1d67"
}

```

- output 字段说明

| 字段    | 类型           | 说明                                    | Description               |
| ------- | -------------- | --------------------------------------- | ------------------------- |
| code    | bool           | 错误编码。 0表示success，>0表示失败错误 | 0:success, other: failure |
| message | result message | 错误消息                                | the failure message       |
| data    | object array   | result data                             | the data for response     |
| traceId | string         | 请求跟踪id                              | the trace id              |

### 下载proxy
- API: GET /generic/api/proxy/download/{projectId}/{name}
- API 名称: download proxy
- 功能说明：
  - 中文：下载proxy
  - English：download proxy


- input 字段说明
| 字段 | 类型   | 是否必须 | 默认值 | 说明   | Description |
| ---- | ------ | -------- | ------ | ------ | ----------- |
| projectId   | string | 是       | 无     | 项目ID | the project id  |
| name   | string | 否       | 无     | proxy名 | the proxy name  |