## Project项目相关接口

[toc]

### 查询项目列表

- API: GET /repository/api/project/list
- API 名称: get_project_list
- 功能说明：
  - 中文：查询项目列表
  - English：get project list
- 请求体
  此接口请求体为空
- 响应体

``` json
{
    "code":0,
    "message":"",
    "data":[
        {
            "name":"project1",
            "displayName":"project1",
            "description":"project1"
        }
    ],
    "traceId": null
}
```

- data 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|name|string|项目名|the project name |
|displayName|string|项目展示名称|the display name of project|
|description|string|项目描述|the description of project|

