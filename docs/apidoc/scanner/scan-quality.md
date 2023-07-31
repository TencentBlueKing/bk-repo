# 扫描方案质量规则接口

[toc]

## 获取扫描方案质量规则

- API: GET /analyst/api/scan/quality/{planId}
- API 名称: get_scan_quality
- 功能说明：
    - 中文：获取扫描方案质量规则
    - English：get scan quality
- 请求体 此接口请求体为空

- 请求字段说明

| 字段     | 类型     | 是否必须 | 默认值 | 说明   | Description |
|--------|--------|------|-----|------|-------------|
| planId | string | 是    | 无   | 方案id | plan id     |

- 响应体

```json
{
  "code": 0,
  "message": null,
  "data": {
    "critical": 1,
    "high": 2,
    "medium": 3,
    "low": 4,
    "forbidScanUnFinished": false,
    "forbidQualityUnPass": false,
    "recommend": true,
    "compliance": false,
    "unknown": false
  },
  "traceId": ""
}
```
- data字段说明

| 字段                   | 类型      | 说明            | Description                         |
|----------------------|---------|---------------|-------------------------------------|
| critical             | number  | 严重漏洞数         | critical risk vulnerabilities count |
| high                 | number  | 高危漏洞数         | high risk license count             |
| medium               | number  | 中危漏洞数         | mid risk license count              |
| low                  | number  | 低危漏洞数         | low risk license count              |
| forbidScanUnFinished | boolean | 扫描未完成是否禁用制品   | whether forbid on scan unfinished   |
| forbidQualityUnPass  | boolean | 质量规则未通过是否禁用制品 | whether forbid on quality failed    |
| recommend            | boolean | 许可是否推荐使用      | whether recommend                   |
| compliance           | boolean | 许可是否合规        | whether compliance                  |
| unknown              | boolean | 许可是否未知        | whether compliance                  |

## 更新扫描方案质量规则

- API: post /analyst/api/scan/quality/{planId}
- API 名称: update_scan_quality
- 功能说明：
  - 中文：更新扫描方案质量规则
  - English：update scan quality
- 请求体

```json
{
  "critical": 1,
  "high": 2,
  "medium": 3,
  "low": 4,
  "forbidScanUnFinished": false,
  "forbidQualityUnPass": false,
  "recommend": true,
  "compliance": false,
  "unknown": false
}
```

- 请求字段说明

| 字段                   | 类型      | 是否必须 | 默认值 | 说明            | Description                         |
|----------------------|---------|------|-----|---------------|-------------------------------------|
| critical             | number  | 否    | 无   | 严重漏洞数         | critical risk vulnerabilities count |
| high                 | number  | 否    | 无   | 高危漏洞数         | high risk license count             |
| medium               | number  | 否    | 无   | 中危漏洞数         | mid risk license count              |
| low                  | number  | 否    | 无   | 低危漏洞数         | low risk license count              |
| forbidScanUnFinished | boolean | 否    | 无   | 扫描未完成是否禁用制品   | whether forbid on scan unfinished   |
| forbidQualityUnPass  | boolean | 否    | 无   | 质量规则未通过是否禁用制品 | whether forbid on quality failed    |
| recommend            | boolean | 否    | 无   | 许可是否推荐使用      | whether recommend                   |
| compliance           | boolean | 否    | 无   | 许可是否合规        | whether compliance                  |
| unknown              | boolean | 否    | 无   | 许可是否未知        | whether compliance                  |

- 响应体

```json
{
    "code": 0,
    "message": null,
    "data": true,
    "traceId": ""
}
```

- data字段说明 是否更新成功
