## Project项目接口说明


### project项目接口

#### 查询项目列表

- API: GET /repository/api/project/list
- API 名称: get_project_list
- 功能说明：
  - 中文：查询项目列表
  - English：get project list

- 请求体
  此接口请求体为空

- 请求字段说明


- 响应体

``` json
{
{
    "code":0,
    "data":[
        {
            "description":"project1",
            "displayName":"project1",
            "name":""
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
|data | bool | result data |the data for response|
|traceId|string|请求跟踪id|the trace id|

- data 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|name|string|项目名|the project name |
|displayName | string | 项目展示名称 |the display name of project|
|description|string|项目描述|the description of project|

