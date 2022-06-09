# 已支持的扫描器

**扫描器配置公共字段**

| 字段                   | 类型     | 是否必须 | 默认值 | 说明             | Description       |
|----------------------|--------|------|-----|----------------|-------------------|
| name                 | string | 是    | 无   | 扫描器名           | scanner name      |
| type                 | string | 是    | 无   | 扫描器类型          | scanner type      |
| version              | string | 是    | 无   | 扫描器版本          | scanner version   |
| maxScanDurationPerMb | number | 否    | 6s  | 最大允许的1MB文件扫描时间 | max scan duration |

# Arrowhead扫描器

## 扫描器配置

| 字段               | 类型      | 是否必须 | 默认值              | 说明                                    | Description                             |
|------------------|---------|------|------------------|---------------------------------------|-----------------------------------------|
| rootPath         | string  | 是    | 无                | 扫描器工作根目录，扫描执行时每个扫描任务都会在这个目录下创建自己的工作目录 | scanner root path                       |
| configFilePath   | string  | 是    | /standalone.toml | 扫描器配置存放路径，相对于扫描任务的工作目录                | scanner config file relative path       |
| cleanWorkDir     | boolean | 否    | true             | 扫描结束后是否清理扫描任务的工作目录                    | clean scan task work dir after scanning |
| knowledgeBase    | object  | 是    | 无                | 漏洞库配置                                 | knowledge base config                   |
| container        | object  | 是    | 无                | 扫描器容器配置                               | container config                        |

### knowledgeBase

| 字段        | 类型      | 是否必须 | 默认值   | 说明      | Description                       |
|-----------|---------|------|-------|---------|-----------------------------------|
| secretId  | string  | 否    | null  | 漏洞库用户名  | scanner config file relative path |
| secretKey | string  | 否    | null  | 漏洞库凭据   | scanner config file relative path |
| endpoint  | string  | 否    | null  | 漏洞库地址   | scanner config file relative path |

### container


| 字段        | 类型     | 是否必须 | 默认值      | 说明                           | Description                    |
|-----------|--------|------|----------|------------------------------|--------------------------------|
| image     | string | 是    | 无        | 扫描器镜像tag                     | image tag                      |
| args      | string | 否    | 无        | 容器启动参数                       | container start args           | 
| workDir   | string | 否    | /data    | 容器内的工作目录，会将扫描任务的工作目录挂载到这个目录里 | scanner work dir in container  |
| inputDir  | string | 否    | /package | 输入目录，相对于workDir的路径           | scanner input dir in container | 
| outputDir | string | 否    | /output  | 输出目录，相对于workDir的路径           | scanner output dir incontainer |

## 扫描结果

### 扫描结果预览

| 字段                           | 类型     | 说明               | Description                         |
|------------------------------|--------|------------------|-------------------------------------|
| cveLowCount                  | number | 低风险漏洞数           | low risk vulnerabilities count      |
| cveMidCount                  | number | 中风险漏洞数           | mid risk vulnerabilities count      |
| cveHighCount                 | number | 高风险漏洞数           | high risk vulnerabilities count     |
| cveCriticalCount             | number | 严重风险漏洞数          | critical risk vulnerabilities count |
| licenseRiskLowCount          | number | 制品依赖的库使用的低风险证书数量 | low risk license count              |
| licenseRiskMidCount          | number | 制品依赖的库使用的中风险证书数量 | mid risk license count              |
| licenseRiskHighCount         | number | 制品依赖的库使用的高风险证书数量 | high risk license count             |
| licenseRiskNotAvailableCount | number | 知识库未收录的证书数量      | unknown license count               |
| sensitiveEmailCount          | number | 敏感邮箱地址数          | sensitive email count               |
| sensitiveIpv4Count           | number | 敏感ipv4地址数        | sensitive ipv4 address count        |
| sensitiveIpv6Count           | number | 敏感ipv6地址数        | sensitive ipv6 address count        |
| sensitiveUriCount            | number | 敏感uri数           | sensitive uri count                 |
| sensitiveSecretCount         | number | 敏感密钥数            | sensitive secret count              |
| sensitiveCryptoObjectCount   | number | 敏感密钥文件数          | sensitive crypto count              |

### 扫描详细结果

#### APPLICATION_ITEM

| 字段          | 类型     | 说明                      | Description     |
|-------------|--------|-------------------------|-----------------|
| path        | string | 使用的库在被扫描文件中的路径          | library path    |
| component   | string | 库名                      | library name    |
| version     | string | 库版本                     | library version |
| license     | object | 使用的证书                   | license         |

##### License

| 字段      | 类型     | 说明                        | Description |
|---------|--------|---------------------------|-------------|
| name    | string | 许可证名                      | name        |
| content | string | 许可证内容                     | content     |
| source  | string | 许可证链接                     | source      |
| risk    | string | 许可证风险等级,LOW, MEDIUM, HIGH | risk        |


#### SENSITIVE_ITEM

| 字段      | 类型     | 说明                  | Description               |
|---------|--------|---------------------|---------------------------|
| path    | string | 存在敏感信息的文件相对被扫描文件的路径 | sensitive file path       |
| type    | string | 敏感信息类型              | sensitive content type    |
| subtype | string | 敏感信息子类型             | sensitive content subtype |
| content | string | 敏感信息内容              | sensitive content         |
| domain  | string | uri,email类型的敏感信息的域名 | domain                    |
| attr    | object | 敏感信息属性              | attr                      |

#### CVE_SEC_ITEM

| 字段               | 类型     | 说明                | Description                                 |
|------------------|--------|-------------------|---------------------------------------------|
| path             | string | 存在漏洞的文件相对被扫描文件的路径 | path                                        |
| component        | string | 存在漏洞的组件           | component with the vulnerability            |
| version          | string | 组件版本              | component version                           |
| versionEffected  | string | 受影响组件版本           | component version effected by vulnerability |
| versionFixed     | string | 组件修复版本            | fixed component version                     |
| name             | string | 漏洞名               | vulnerability name                          |
| category         | string | 漏洞利用类型            | category                                    |
| categoryType     | string | 漏洞类型              | category type                               |
| description      | string | 描述                | description                                 |
| officialSolution | string | 官方解决方案            | official solution                           |
| defenseSolution  | string | 防御方案              | defense solution                            |
| references       | array  | 引用文档              | references                                  |
| cveYear          | string | 漏洞年份              | cve year                                    |
| pocId            | string | poc id            | poc id                                      |
| cveId            | string | cve id            | cve id                                      |
| cnvdId           | string | cnvd id           | cnvd id                                     |
| cnnvdId          | string | cnnvd id          | cnnvd id                                    |
| cweId            | string | cwe id            | cwe id                                      |
| cvssRank         | string | cvss等级            | cvss rank                                   |
| cvss             | number | cvss评分            | cvss                                        |
| cvssV3           | string | cvss V3 漏洞影响评价    | cvss v3 vector                              |
| cvssV2           | string | cvss V2 漏洞影响评价    | cvss v2 vector                              |
