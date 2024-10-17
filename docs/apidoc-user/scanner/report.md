# 扫描报告接口

[toc]

## 获取扫描报告预览

- API: POST /analyst/api/scan/reports/overview
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

| 字段                                 | 类型     | 是否必须 | 默认值 | 说明                      | Description     |
|------------------------------------|--------|------|-----|-------------------------|-----------------|
| scanner                            | string | 是    | 无   | 要获取的报告使用的扫描器名称          | scanner name    |
| credentialsKeyFiles.credentialsKey | string | 否    | 无   | 被扫描文件所在存储,为null时表示在默认存储 | credentials key |
| credentialsKeyFiles.sha256List     | array  | 是    | 无   | 要查询报告的文件sha256列表        | sha256 list     |

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
                "cveHighCount": 44,
                "cveMediumCount": 35
            }
        }
    ],
    "traceId": ""
}
```
- data字段说明

| 字段               | 类型     | 说明                    | Description                         |
|------------------|--------|-----------------------|-------------------------------------|
| status           | string | 文件扫描状态                | file scan status                    |
| sha256           | string | 文件sha256              | file sha256                         |
| scanDate         | string | 文件扫描时间                | file scan datetime                  |
| overview         | object | 文件扫描结果预览，不同扫描器预览结果不一样 | file scan result overview           |
| cveLowCount      | number | 低风险漏洞数                | low risk vulnerabilities count      |
| cveMediumCount   | number | 中风险漏洞数                | mid risk vulnerabilities count      |
| cveHighCount     | number | 高风险漏洞数                | high risk vulnerabilities count     |
| cveCriticalCount | number | 严重风险漏洞数               | critical risk vulnerabilities count |

## 获取扫描报告详情

- API: POST /analyst/api/scan/reports/detail/{projectId}/{repoName}/{fullPath}
- API 名称: get_report_detail
- 功能说明：
  - 中文：获取扫描报告详情
  - English：scan report detail
- 请求体

```json
{
  "scanner": "default",
  "arguments": {
    "type": "standard",
    "reportType": "SECURITY",
    "pageLimit": {
      "pageNumber": 1,
      "pageSize": 10
    }
  }
}
```

- 请求字段说明

| 字段                            | 类型     | 是否必须 | 默认值 | 说明                                    | Description     |
|-------------------------------|--------|------|-----|---------------------------------------|-----------------|
| projectId                     | string | 是    | 无   | 文件所属项目id                              | project id      |
| repoName                      | string | 是    | 无   | 文件所属仓库名                               | repository name |
| fullPath                      | string | 是    | 无   | 文件完整路径                                | file path       |
| scanner                       | string | 是    | 无   | 扫描器名                                  | scanner name    |
| arguments.type                | string | 是    | 无   | 参数类型，固定为standard                      | arg type        |
| arguments.reportType          | string | 是    | 无   | 扫描报告类型，目前支持SECURITY，LICENSE，SENSITIVE | report type     |
| arguments.vulIds              | array  | 否    | 无   | 查询的漏洞列表                               | cve id          |
| arguments.vulnerabilityLevels | array  | 否    | 无   | 筛选漏洞等级                                | vul level       |
| pageLimit.pageNumber          | number | 否    | 1   | 分页页码                                  | page number     |
| pageLimit.pageSize            | number | 否    | 20  | 分页大小                                  | page size       |

- 响应体

```json
{
  "code": 0,
  "message": null,
  "data": {
    "status": "SUCCESS",
    "sha256": "dc435b35b5923eb05afe30a24f04e9a0a5372da8e76f986efe8508b96101c4ff",
    "detail": {
      "pageNumber": 1,
      "pageSize": 1,
      "totalRecords": 4,
      "totalPages": 4,
      "records": [
        {
          "vulId": "pcmgr-333682",
          "vulName": "Apache Log4j安全漏洞",
          "cveId": "CVE-2021-45046",
          "path": "/dc435b35b5923eb05afe30a24f04e9a0a5372da8e76f986efe8508b96101c4ff.jar",
          "versionsPaths": [
            {
              "version": "5.3.16",
              "paths": [
                "/0903d17e58654a2c79f4e46df79dc73ccaa49b6edbc7c3278359db403b687f6e.jar"
              ]
            }
          ],
          "pkgName": "org.apache.logging.log4j:log4j-core",
          "pkgVersions": ["2.9.1"],
          "fixedVersion": "2.16.0",
          "des": "ApacheLog4j是美国阿帕奇（Apache）基金会的一款基于Java的开源日志记录工具。Log4j2.16.0之前的版本(不包括2.3.1, 2.12.2)存在安全漏洞，该漏洞源于当日志配置使用非默认模式布局和上下文查找或线程上下文映射模式使用JNDI查找模式制作恶意输入数据，从而导致拒绝服务攻击。",
          "solution": "目前厂商已发布升级补丁以修复漏洞，补丁获取链接：https://logging.apache.org/log4j/2.x/security.html。",
          "references": [
            "http://www.openwall.com/lists/oss-security/2021/12/14/4",
            "https://logging.apache.org/log4j/2.x/security.html",
            "https://www.cve.org/CVERecord?id=CVE-2021-44228",
            "https://www.intel.com/content/www/us/en/security-center/advisory/intel-sa-00646.html",
            "https://tools.cisco.com/security/center/content/CiscoSecurityAdvisory/cisco-sa-apache-log4j-qRuKNEbd",
            "http://www.openwall.com/lists/oss-security/2021/12/15/3",
            "https://cert-portal.siemens.com/productcert/pdf/ssa-661247.pdf",
            "https://www.kb.cert.org/vuls/id/930724",
            "https://cert-portal.siemens.com/productcert/pdf/ssa-714170.pdf",
            "https://www.debian.org/security/2021/dsa-5022"
          ],
          "cvss": 9.0,
          "severity": "CRITICAL"
        }
      ],
      "count": 4,
      "page": 1
    }
  },
  "traceId": null
}
```

 data字段说明

| 字段                    | 类型     | 说明                                                     | Description               |
|-----------------------|--------|--------------------------------------------------------|---------------------------|
| status                | string | 文件扫描状态                                                 | file scan status          |
| sha256                | string | 文件sha256                                               | file sha256               |
| pageNumber            | number | 页码                                                     | page number               |
| pageSize              | number | 页大小                                                    | page size                 |
| totalRecords          | number | 总记录数量                                                  | total records             |
| totalPage             | number | 总页数                                                    | total page                |
| vulId                 | string | 漏洞id                                                   | vul id                    |
| vulName               | string | 漏洞名                                                    | vul Name                  |
| cveId                 | string | 漏洞cve id                                               | total page                |
| path                  | string | 已废弃，使用versionsPaths替代，当被扫描的制品是压缩包时，表示存在漏洞的文件的在被扫描包中的路径 | path                      |
| versionsPaths.version | string | 版本                                                     | version                   |
| versionsPaths.paths   | array  | 版本所在路径                                                 | version path              |
| pkgName               | string | 存在漏洞的组件名                                               | package name              |
| pkgVersions           | array  | 存在漏洞的组件版本                                              | package versions          |
| fixedVersion          | string | 修复版本                                                   | fixed version             |
| effectedVersion       | string | 影响版本                                                   | effected version          |
| des                   | string | 漏洞描述                                                   | vulnerability description |
| solution              | string | 漏洞解决方案                                                 | solution                  |
| references            | array  | 漏洞相关引用                                                 | references                |
| cvss                  | number | 漏洞cvss                                                 | cvss                      |
| severity              | string | 漏洞评级,取值CRITICAL,HIGH,MEDIUM,LOW                        | severity                  |
| licenseName           | string | 许可证名                                                   | license name              |

## 获取子任务扫描报告详情

- API: GET /analyst/api/scan/reports/{subScanTaskId}
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

- API: GET /analyst/api/scan/artifact/leak/{projectId}/{subScanTaskId}
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

- API: GET /analyst/api/scan/artifact/count/{projectId}/{subScanTaskId}
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