# 扫描器接口

[toc]

## 创建扫描器

- API: POST /analyst/api/scanners
- API 名称: create_scanner
- 功能说明：
    - 中文：创建扫描器
    - English：create scanner
- 创建扫描器请求体

```json
{
  "name": "arrowhead",
  "image": "example.com/example/scanner:1.0",
  "dockerRegistryUsername": "xxx",
  "dockerRegistryPassword": "xxx",
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
  "supportScanTypes": ["SECURITY", "LICENSE"],
  "supportDispatchers": ["k8s-1", "k8s-2"],
  "limitMem": 34359738368,
  "requestMem": 17179869184,
  "requestStorage": 17179869184,
  "limitStorage": 137438953472,
  "requestCpu": 4.0,
  "limitCpu": 16.0,
  "unsupportedArtifactNameRegex": [".*\\.jar"]
}
```

- 请求字段说明

| 字段                           | 类型      | 是否必须 | 默认值          | 说明                                             | Description                          |
|------------------------------|---------|------|--------------|------------------------------------------------|--------------------------------------|
| name                         | string  | 是    | 无            | 扫描器名                                           | scanner name                         |
| image                        | string  | 是    | 无            | 扫描器镜像                                          | scanner image                        |
| dockerRegistryUsername       | string  | 否    | 无            | 扫描器镜像所在仓库用户名                                   | scanner image                        |
| dockerRegistryPassword       | string  | 否    | 无            | 扫描器镜像所在仓库密码                                    | scanner image                        |
| cmd                          | string  | 是    | 无            | 扫描器启动命令，扫描器镜像不需要设置entrypoint，而是制品库启动扫描器时候设置cmd | scanner cmd                          |
| version                      | string  | 是    | 无            | 扫描器版本                                          | scanner version                      |
| type                         | string  | 是    | 无            | 扫描器类型，固定为standard                              | scanner type                         |
| description                  | string  | 是    | 无            | 扫描器描述                                          | scanner description                  |
| rootPath                     | string  | 是    | 无            | 扫描器工作根目录                                       | scanner work dir                     |
| cleanWorkDir                 | boolean | 否    | true         | 扫描结束后是否清理目录                                    | clean work dir after scan            |
| maxScanDurationPerMb         | number  | 否    | 6000         | 每MB文件最大允许的扫描时间                                 | max scan duration per mb             |
| supportFileNameExt           | array   | 否    | empty        | 支持扫描的文件名后缀                                     | support file name extensions         |
| supportPackageTypes          | array   | 否    | empty        | 支持扫描的包类型                                       | support package types                |
| supportScanTypes             | array   | 否    | empty        | 支持扫描的类型                                        | support scan types                   |
| supportDispatchers           | array   | 否    | empty        | 支持运行的扫描执行集群                                    | support execution cluster dispatcher |
| limitMem                     | number  | 否    | 34359738368  | 扫描容器limit mem                                  | limit mem                            |
| requestMem                   | number  | 否    | 17179869184  | 扫描容器request mem                                | request mem                          |
| requestStorage               | number  | 否    | 17179869184  | 扫描容器request ephemeralStorage                   | request ephemeral storage            |
| limitStorage                 | number  | 否    | 137438953472 | 扫描容器limit ephemeralStorage                     | limit ephemeral storage              |
| requestCpu                   | number  | 否    | 4.0          | 扫描容器request cpu                                | request cpu                          |
| limitCpu                     | number  | 否    | 16.0         | 扫描容器limit cpu                                  | limit cpu                            |
| unsupportedArtifactNameRegex | array   | 否    | empty        | 不支持的制品名称正则列表                                   | unsupported artifact name regex      |

- 响应体

```json
{
  "code": 0,
  "message": null,
  "data": {
    "name": "arrowhead",
    "image": "example.com/example/scanner:1.0",
    "dockerRegistryUsername": "xxx",
    "dockerRegistryPassword": "xxx",
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
    "supportScanTypes": ["SECURITY", "LICENSE"],
    "supportDispatchers": ["k8s-1", "k8s-2"],
    "limitMem": 34359738368,
    "requestMem": 17179869184,
    "requestStorage": 17179869184,
    "limitStorage": 137438953472,
    "requestCpu": 4.0,
    "limitCpu": 16.0,
    "unsupportedArtifactNameRegex": [".*\\.jar"]
  },
  "traceId": ""
}
 ```

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
    "dockerRegistryUsername": "xxx",
    "dockerRegistryPassword": "xxx",
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
    "supportScanTypes": ["SECURITY", "LICENSE"],
    "supportDispatchers": ["k8s-1", "k8s-2"],
    "limitMem": 34359738368,
    "requestMem": 17179869184,
    "requestStorage": 17179869184,
    "limitStorage": 137438953472,
    "requestCpu": 4.0,
    "limitCpu": 16.0,
    "unsupportedArtifactNameRegex": [".*\\.jar"]
  },
  "traceId": ""
}
 ```

- data字段说明
详情参考[创建扫描器请求体](./scanner.md?id=创建扫描器)

## 删除扫描器

- API: DELETE /analyst/api/scanners/{scannerName}
- API 名称: delete_scanner
- 功能说明：
    - 中文：删除扫描器
    - English：delete scanner
- 请求体 此接口请求体为空

- 请求字段说明

| 字段          | 类型     | 是否必须 | 默认值 | 说明    | Description  |
|-------------|--------|------|-----|-------|--------------|
| scannerName | string | 是    | 无   | 扫描器名称 | scanner name |

- 响应体

```json
{
  "code": 0,
  "message": null,
  "data": null,
  "traceId": null
}
 ```

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
        "dockerRegistryUsername": "xxx",
        "dockerRegistryPassword": "xxx",
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
        "supportScanTypes": ["SECURITY", "LICENSE"],
        "supportDispatchers": ["k8s-1", "k8s-2"],
        "limitMem": 34359738368,
        "requestMem": 17179869184,
        "requestStorage": 17179869184,
        "limitStorage": 137438953472,
        "requestCpu": 4.0,
        "limitCpu": 16.0,
        "unsupportedArtifactNameRegex": [".*\\.jar"]
      },
      "traceId": ""
    }
  ],
  "traceId": ""
}
```

- data字段说明

详情参考[创建扫描器请求体](./scanner.md?id=创建扫描器)
