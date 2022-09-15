# 包版本元数据接口

[TOC]

## 保存（更新）元数据
- API: POST /repository/api/metadata/package/{projectId}/{repoName}
- API 名称: save_metadata
- 功能说明：
  - 中文：保存（更新）元数据信息，元数据不存在则保存，存在则更新
  - English：save metadata info
- 请求体

  ```json
  {
    "packageKey": "docker://bkrepo-backend",
    "version": "1.0.0",
    "versionMetadata": [
      {
        "key": "key",
        "value":  "value",
        "description": "description"
      }
    ]
  }
  ```

- 请求字段说明

  | 字段        | 类型     |是否必须|默认值| 说明                   |Description|
  |--------|---|---|----------------------|---|---|
  | projectId | string |是|无| 项目名称                 |project name|
  | repoName  | string |是|无| 仓库名称                 |repo name|
  | packageKey  | string |是|无|   [包唯一key](../package/package.md?id=package%20key%20格式)              |package key|
  | version  | string |是|无| 包版本      |version|
  | versionMetadata | object |是|无| 元数据 |metadata|

- 响应体

  ``` json
  {
    "code": 0,
    "message": null,
    "data": null,
    "traceId": null
  }
  ```


## 根据元数据查询包版本

利用[查询版本列表](../package/package.md?id=查询包版本)接口查询符合元数据规则的包版本，请求体如下

- 请求体
```json
  {
    "pageNumber": 1,
    "pageSize": 20,
    "metadata": [
      {
        "key": "test",
        "value": "pass"
      }
    ]
  }
```
