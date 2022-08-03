# 扫描报告接口

[toc]

## 获取扫描报告预览

- API: POST /scanner/api/scan/reports/overview
- API 名称: get_report_overview
- 功能说明：
    - 中文：预览扫描报告
    - English：scan report overview
- 请求体

```json
{
	"scanner": "default",
	"credentialsKeyFiles": [
		{
			"credentialsKey": null,
			"sha256List":["af5d27f8921339531c5315c0928558f0de9ef1d27d07ff0f487602239cf885b5"]
		}	
	]
}
```

- 请求字段说明

| 字段                                      | 类型     | 是否必须 | 默认值 | 说明                      | Description     |
|-----------------------------------------|--------|------|-----|-------------------------|-----------------|
| scanner                                 | string | 是    | 无   | 要获取的报告使用的扫描器名称          | scanner name    |
| credentialsKeyFiles.credentialsKeyFiles | string | 否    | 无   | 被扫描文件所在存储,为null时表示在默认存储 | credentials key |
| credentialsKeyFiles.sha256List          | array  | 是    | 无   | 要查询报告的文件sha256列表        | sha256 list     |

- 响应体

```json
{
    "code": 0,
    "message": null,
    "data": [
        {
            "status": "SUCCESS",
            "sha256": "726683039c3af2005142c80af8ba823715ee07acbff17eb2662f08fb2b903d0f",
            "scanDate": "2022-03-10T17:06:05.122",
            "overview": {
                "cveLowCount": 5,
                "cveCriticalCount": 10,
                "licenseRiskLowCount": 52,
                "licenseRiskMidCount": 56,
                "sensitiveUriCount": 2095,
                "cveHighCount": 44,
                "sensitiveIpv4Count": 386,
                "licenseRiskNotAvailableCount": 30,
                "licenseRiskHighCount": 24,
                "cveMidCount": 35,
                "sensitiveEmailCount": 237
            }
        }
    ],
    "traceId": ""
}
```
- data字段说明

| 字段                           | 类型     | 说明                    | Description                         |
|------------------------------|--------|-----------------------|-------------------------------------|
| status                       | string | 文件扫描状态                | file scan status                    |
| sha256                       | string | 文件sha256              | file sha256                         |
| scanDate                     | string | 文件扫描时间                | file scan datetime                  |
| overview                     | object | 文件扫描结果预览，不同扫描器预览结果不一样 | file scan result overview           |
| cveLowCount                  | number | 低风险漏洞数                | low risk vulnerabilities count      |
| cveMidCount                  | number | 中风险漏洞数                | mid risk vulnerabilities count      |
| cveHighCount                 | number | 高风险漏洞数                | high risk vulnerabilities count     |
| cveCriticalCount             | number | 严重风险漏洞数               | critical risk vulnerabilities count |
| licenseRiskLowCount          | number | 制品依赖的库使用的低风险证书数量      | low risk license count              |
| licenseRiskMidCount          | number | 制品依赖的库使用的中风险证书数量      | mid risk license count              |
| licenseRiskHighCount         | number | 制品依赖的库使用的高风险证书数量      | high risk license count             |
| licenseRiskNotAvailableCount | number | 知识库未收录的证书数量           | unknown license count               |
| sensitiveEmailCount          | number | 敏感邮箱地址数               | sensitive email count               |
| sensitiveIpv4Count           | number | 敏感ipv4地址数             | sensitive ipv4 address count        |
| sensitiveIpv6Count           | number | 敏感ipv6地址数             | sensitive ipv6 address count        |
| sensitiveUriCount            | number | 敏感uri数                | sensitive uri count                 |
| sensitiveSecretCount         | number | 敏感密钥数                 | sensitive secret count              |
| sensitiveCryptoObjectCount   | number | 敏感密钥文件数               | sensitive crypto count              |

扫描结果预览字段参考[支持的扫描器](./supported-scanner.md)

## 获取扫描报告详情

- API: POST /scanner/api/scan/reports/detail/{projectId}/{repoName}/{artifactUri}
- API 名称: get_report_detail
- 功能说明：
  - 中文：获取扫描报告详情
  - English：scan report detail
- 请求体

```json
{
  "scanner": "default",
  "arguments": {
    "type": "arrowhead",
    "vulIds": ["CVE-2022-22965"],
    "vulnerabilityLevels": ["critical"],
    "reportType": "CVE_SEC_ITEM",
    "pageLimit": {
      "pageNumber": 1,
      "pageSize": 10
    }
  }

}
```

- 请求字段说明

| 字段                            | 类型     | 是否必须 | 默认值 | 说明                                                                                     | Description     |
|-------------------------------|--------|------|-----|----------------------------------------------------------------------------------------|-----------------|
| projectId                     | string | 是    | 无   | 文件所属项目id                                                                               | project id      |
| repoName                      | string | 是    | 无   | 文件所属仓库名                                                                                | repository name |
| artifactUri                   | string | 是    | 无   | 文件路径                                                                                   | artifact uri    |
| scanner                       | string | 是    | 无   | 扫描器名                                                                                   | scanner name    |
| arguments.type                | string | 是    | 无   | 参数类型，目前支持arrowhead                                                                     | arg type        |
| arguments.reportType          | string | 是    | 无   | 扫描报告类型，arrowhead有CHECK_SEC_ITEM,APPLICATION_ITEM,CVE_SEC_ITEM,SENSITIVE_ITEM，4种类型的扫描报告 | report type     |
| arguments.vulIds              | array  | 否    | 无   | 查询的漏洞列表                                                                                | cve id          |
| arguments.vulnerabilityLevels | array  | 否    | 无   | 筛选漏洞等级                                                                                 | vul level       |
| pageLimit.pageNumber          | number | 否    | 1   | 分页页码                                                                                   | page number     |
| pageLimit.pageSize            | number | 否    | 20  | 分页大小                                                                                   | page size       |

- 响应体

```json
{
  "code": 0,
  "message": null,
  "data": {
    "status": "SUCCESS",
    "sha256": "a5c9f2b2250d8ef4a8d19b153404861d0db41fc24913b20f4fcad59c155bceda",
    "detail": {
      "pageNumber": 1,
      "pageSize": 10,
      "totalRecords": 1,
      "totalPages": 1,
      "records": [
        {
          "path": "/BOOT-INF/lib/spring-aop-5.2.12.RELEASE.jar",
          "component": "spring-aop",
          "version": "5.2.12.RELEASE",
          "versions": [
            "5.2.12.RELEASE"
          ],
          "versionEffected": "",
          "versionFixed": "",
          "name": "",
          "category": "",
          "categoryType": "",
          "description": "",
          "officialSolution": "",
          "defenseSolution": "",
          "references": [],
          "cveYear": "",
          "pocId": "",
          "cveId": "CVE-2022-22965",
          "cnvdId": "",
          "cnnvdId": "",
          "cweId": "",
          "cvssRank": "critical",
          "cvss": 0,
          "cvssV3": null,
          "cvssV2": null
        }
      ],
      "page": 1,
      "count": 1
    }
  },
  "traceId": ""
}
```

 data字段说明

| 字段                     | 类型     | 说明       | Description             |
|------------------------|--------|----------|-------------------------|
| status                 | string | 文件扫描状态   | file scan status        |
| sha256                 | string | 文件sha256 | file sha256             |
| detail.pageNumber      | number | 页码       | page number             |
| detail.pageSize        | number | 页大小      | page size               |
| detail.totalRecords    | number | 总记录数量    | total records           |
| detail.totalPage       | number | 总页数      | total page              |

扫描结果详情detail字段参考[支持的扫描器](./supported-scanner.md)

## 获取子任务扫描报告详情

- API: GET /scanner/api/scan/reports/{subScanTaskId}
- API 名称: get_subtask_report_detail
- 功能说明：
  - 中文：获取扫描子任务漏洞数据
  - English：scan subtask vul info
- 请求体 此接口请求体为空

- 请求字段说明

| 字段            | 类型     | 是否必须 | 默认值 | 说明    | Description |
|---------------|--------|------|-----|-------|-------------|
| subScanTaskId | string | 是    | 无   | 子任务id | subtask id  |
| vulId         | string | 否    | 无   | 漏洞id  | cve id      |
| leakType      | string | 否    | 无   | 漏洞等级  | vul level   |
| pageNumber    | number | 否    | 1   | 分页页码  | page number |
| pageSize      | number | 否    | 20  | 分页大小  | page size   |

- 响应体

```json
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
                "vulId": "CVE-2021-44228",
                "severity": "CRITICAL",
                "pkgName": "org.apache.logging.log4j:log4j-core",
                "installedVersion": [
                    "2.14.1"
                ],
                "title": "Apache log4j2 远程代码执行漏洞",
                "vulnerabilityName": "Apache log4j2 远程代码执行漏洞",
                "description": "ApacheLog4j是美国阿帕奇（Apache）基金会的一款基于Java的开源日志记录工具。ApacheLog4J存在输入验证错误漏洞.Log4j-2中存在JNDI注入漏洞，当程序将用户输入的数据进行日志记录时，即可触发此漏洞，成功利用此漏洞可以在目标服务器上执行任意代码。",
                "officialSolution": "目前厂商已发布升级补丁以修复漏洞，补丁获取链接：https://logging.apache.org/log4j/2.x/security.html",
                "reference": [
                    "https://logging.apache.org/log4j/2.x/security.html",
                    "http://www.openwall.com/lists/oss-security/2021/12/10/1",
                    "http://www.openwall.com/lists/oss-security/2021/12/10/2",
                    "http://packetstormsecurity.com/files/165225/Apache-Log4j2-2.14.1-Remote-Code-Execution.html",
                    "https://security.netapp.com/advisory/ntap-20211210-0007/",
                    "https://tools.cisco.com/security/center/content/CiscoSecurityAdvisory/cisco-sa-apache-log4j-qRuKNEbd",
                    "http://www.openwall.com/lists/oss-security/2021/12/10/3",
                    "https://psirt.global.sonicwall.com/vuln-detail/SNWLID-2021-0032",
                    "https://www.oracle.com/security-alerts/alert-cve-2021-44228.html",
                    "https://lists.fedoraproject.org/archives/list/package-announce@lists.fedoraproject.org/message/VU57UJDCFIASIO35GC55JMKSRXJMCDFM/",
                    "http://www.openwall.com/lists/oss-security/2021/12/13/1",
                    "http://www.openwall.com/lists/oss-security/2021/12/13/2",
                    "https://twitter.com/kurtseifried/status/1469345530182455296",
                    "https://lists.debian.org/debian-lts-announce/2021/12/msg00007.html",
                    "https://www.debian.org/security/2021/dsa-5020",
                    "https://cert-portal.siemens.com/productcert/pdf/ssa-661247.pdf",
                    "http://packetstormsecurity.com/files/165270/Apache-Log4j2-2.14.1-Remote-Code-Execution.html",
                    "http://packetstormsecurity.com/files/165260/VMware-Security-Advisory-2021-0028.html",
                    "http://packetstormsecurity.com/files/165261/Apache-Log4j2-2.14.1-Information-Disclosure.html",
                    "http://www.openwall.com/lists/oss-security/2021/12/14/4",
                    "https://www.intel.com/content/www/us/en/security-center/advisory/intel-sa-00646.html",
                    "https://www.kb.cert.org/vuls/id/930724",
                    "http://packetstormsecurity.com/files/165282/Log4j-Payload-Generator.html",
                    "http://packetstormsecurity.com/files/165281/Log4j2-Log4Shell-Regexes.html",
                    "http://packetstormsecurity.com/files/165306/L4sh-Log4j-Remote-Code-Execution.html",
                    "http://packetstormsecurity.com/files/165307/Log4j-Remote-Code-Execution-Word-Bypassing.html",
                    "http://packetstormsecurity.com/files/165311/log4j-scan-Extensive-Scanner.html",
                    "http://www.openwall.com/lists/oss-security/2021/12/15/3",
                    "https://cert-portal.siemens.com/productcert/pdf/ssa-714170.pdf",
                    "https://msrc-blog.microsoft.com/2021/12/11/microsofts-response-to-cve-2021-44228-apache-log4j2/",
                    "https://tools.cisco.com/security/center/content/CiscoSecurityAdvisory/cisco-sa-apache-log4j-qRuKNEbd"
                ],
                "path": "/97eef8b72e121347074c8b3062b010170187a6fa7375555fd1ed68540adaea1f.jar"
            }
        ],
        "page": 1,
        "count": 4
    },
    "traceId": ""
}
```

data字段说明

| 字段                | 类型     | 说明     | Description       |
|-------------------|--------|--------|-------------------|
| vulId             | string | 漏洞id   | vul id            |
| severity          | string | 漏洞等级   | vul severity      |
| pkgName           | string | 所属依赖   | dependency        |
| installedVersion  | array  | 使用的版本  | installed version |
| title             | string | 漏洞标题   | vul title         |
| vulnerabilityName | string | 漏洞名    | vul name          |
| description       | string | 漏洞描述   | description       |
| officialSolution  | string | 官方解决方案 | official solution |
| reference         | array  | 关联引用   | reference         |
| path              | string | 漏洞文件路径 | vul path          |

响应体参考[分页接口响应格式](../common/common.md?id=统一分页接口响应格式)

## 获取属于方案的子任务扫描报告详情

- API: GET /scanner/api/scan/artifact/leak/{projectId}/{subScanTaskId}
- API 名称: get_plan_subtask_report_detail
- 功能说明：
  - 中文：获取属于方案的扫描子任务漏洞数据
  - English：scan plan subtask vul info
- 请求体 此接口请求体为空

- 请求字段说明

| 字段            | 类型     | 是否必须 | 默认值 | 说明    | Description |
|---------------|--------|------|-----|-------|-------------|
| projectId     | string | 是    | 无   | 项目id  | project id  |
| subScanTaskId | string | 是    | 无   | 子任务id | project id  |
| vulId         | string | 否    | 无   | 漏洞id  | cve id      |
| leakType      | string | 否    | 无   | 漏洞等级  | vul level   |
| pageNumber    | number | 否    | 1   | 分页页码  | page number |
| pageSize      | number | 否    | 20  | 分页大小  | page size   |

- 响应体

响应体参考[获取子任务扫描报告详情](./report.md?id=获取子任务扫描报告详情)

## 获取属于方案的子任务信息

- API: GET /scanner/api/scan/artifact/count/{projectId}/{subScanTaskId}
- API 名称: get_plan_subtask_report_detail
- 功能说明：
  - 中文：获取属于方案的扫描子任务信息
  - English：get scan plan subtask
- 请求体 此接口请求体为空

- 请求字段说明

| 字段            | 类型     | 是否必须 | 默认值 | 说明    | Description |
|---------------|--------|------|-----|-------|-------------|
| projectId     | string | 是    | 无   | 项目id  | project id  |
| subScanTaskId | string | 是    | 无   | 子任务id | project id  |

- 响应体

响应体参考[获取扫描子任务](./scan.md?id=获取扫描子任务)