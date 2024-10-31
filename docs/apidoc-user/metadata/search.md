# 利用元数据搜索制品
[TOC]

## 利用元数据搜索节点

利用[自定义搜索接口](../node/node.md?id=自定义搜索)查询节点，请求体如下

```json
{
  "select": ["xxx", "yyy", "xxx"],
  "page": {
    "pageNumber": 0,
    "pageSize": 20
  },
  "sort": {
    "properties": ["name"],
    "direction": "ASC"
  },
  "rule": {
    "rules": [
      {
        "field": "projectId",
        "value": "test",
        "operation": "EQ"
      },
      {
        "field": "repoName",
        "value": ["generic-local1", "generic-local2"],
        "operation": "IN"
      },
      {
        "field": "metadata.key1",
        "value": "xxxxx",
        "operation": "EQ"
      },
      {
        "field": "metadata.key2",
        "value": "xxxx",
        "operation": "EQ"
      }
    ],
    "relation": "AND"
  }
}
```