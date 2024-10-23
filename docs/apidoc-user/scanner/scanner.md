# 扫描器接口

[toc]

## 查询扫描器

- API: GET /analyst/api/scanners/{scannerName}
- API 名称: get_scanner
- 功能说明：
    - 中文：查询扫描器
    - English：get scanner
- 请求体 此接口请求体为空
- 请求字段说明

| 字段          | 类型     | 是否必须 | 默认值 | 说明   | Description  |
|-------------|--------|------|-----|------|--------------|
| scannerName | string | 否    | 无   | 扫描器名 | scanner name |

- 响应体

```json
{
  "code": 0,
  "message": null,
  "data": {
    "name": "arrowhead",
    "image": "example.com/example/scanner:1.0",
    "cmd": "scan",
    "version": "1.0",
    "args": [
      {
        "type": "STRING",
        "key": "knowledgeBaseEndpoint",
        "value": "https://kb.exmaple.com/",
        "des": "knowledge base endpoint"
      }
    ],
    "type": "standard",
    "description": "arrowhead scanner",
    "rootPath": "/standard",
    "cleanWorkDir": true,
    "maxScanDurationPerMb": 6000,
    "supportFileNameExt": ["tar", "apk", "ipa", "jar"],
    "supportPackageTypes": ["DOCKER", "GENERIC", "MAVEN"],
    "supportScanTypes": ["SECURITY", "LICENSE"]
  },
  "traceId": ""
}
 ```

- data字段说明
详情参考[创建扫描器请求体](./scanner.md?id=创建扫描器)


## 获取扫描器列表

- API: GET /analyst/api/scanners
- API 名称: list_scanner
- 功能说明：
    - 中文：获取扫描器列表
    - English：list scanner
- 请求体 此接口请求体为空

- 响应体

```json
{
  "code": 0,
  "message": null,
  "data": [
    {
      "code": 0,
      "message": null,
      "data": {
        "name": "arrowhead",
        "image": "example.com/example/scanner:1.0",
        "cmd": "scan",
        "version": "1.0",
        "args": [
          {
            "type": "STRING",
            "key": "knowledgeBaseEndpoint",
            "value": "https://kb.exmaple.com/",
            "des": "knowledge base endpoint"
          }
        ],
        "type": "standard",
        "description": "arrowhead scanner",
        "rootPath": "/standard",
        "cleanWorkDir": true,
        "maxScanDurationPerMb": 6000,
        "supportFileNameExt": ["tar", "apk", "ipa", "jar"],
        "supportPackageTypes": ["DOCKER", "GENERIC", "MAVEN"],
        "supportScanTypes": ["SECURITY", "LICENSE"]
      },
      "traceId": ""
    }
  ],
  "traceId": ""
}
```

- data字段说明

详情参考[创建扫描器请求体](./scanner.md?id=创建扫描器)
