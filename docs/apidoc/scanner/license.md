# 许可证接口

[TOC]



## 许可证数据说明

```
{
	"name": "LaTeX Project Public License v1.2",
    "licenseId": "LPPL-1.2",
    "seeAlso": [
    "http://www.latex-project.org/lppl/lppl-1-2.txt"
    ],
    "reference": "https://spdx.org/licenses/LPPL-1.2.html",
    "isDeprecatedLicenseId": false,
    "isOsiApproved": false,
    "isFsfLibre": true,
    "detailsUrl": "https://spdx.org/licenses/LPPL-1.2.json",
    "isTrust": true,
    "risk": null
}
```

| 字段                  | 类型    | 是否必须 | 默认值 | 说明                                                         | DESCRIPTION             |
| --------------------- | ------- | -------- | ------ | ------------------------------------------------------------ | ----------------------- |
| name                  | String  | 是       | 无     | 许可证名称                                                   | license name            |
| licenseId             | String  | 是       | 无     | 许可证唯一标识                                               | license id              |
| seeAlso               | Array   | 是       | 无     | 许可证描述                                                   | license see also        |
| reference             | String  | 是       | 无     | 许可证描述                                                   | license reference       |
| isDeprecatedLicenseId | Boolean | 是       | 无     | 是否被弃用（是否推荐使用，true==不推荐使用，false==推荐使用） | license is deprecated   |
| isOsiApproved         | Boolean | 是       | 无     | 是否经过OSI认证                                              | license is OSI approved |
| isFsfLibre            | Boolean | 否       | null   | 是否FSF免费认证                                              | license is FSF libre    |
| detailsUrl            | String  | 是       | 无     | 许可证详细信息描述                                           | license detail url      |
| isTrust               | Boolean | 是       | true   | 是否可信（用户可编辑）                                       | license is trust        |
| risk                  | String  | 否       | null   | 风险等级                                                     | license risk level      |



## 导入许可证数据（页面功能使用）

- API：PUT /generic/public-global/vuldb-repo/spdx-license/{fileName}
- API 名称：import_license_data
- 功能说明：
  - 中文：导入许可证数据，调用二进制仓库上传接口，文件上传后在对文件做解析处理
  - English：import license data
- 请求体：[文件流]

- 请求字段说明：

| 字段     | 类型   | 是否必须 | 默认值       | 说明   | Description |
| -------- | ------ | -------- | ------------ | ------ | ----------- |
| fileName | string | 是       | 许可证文件名 | 文件名 | file name   |

- 请求头：
  - 不允许覆盖文件，即使用【X-BKREPO-OVERWRITE】默认值，可以不传参
  - 所有请求头参数都可不传参

| 字段                | 类型    | 是否必须 | 默认值 | 说明                                                         | Description          |
| ------------------- | ------- | -------- | ------ | ------------------------------------------------------------ | -------------------- |
| X-BKREPO-SHA256     | string  | 否       | 无     | 文件sha256                                                   | file sha256          |
| X-BKREPO-MD5        | string  | 否       | 无     | 文件md5                                                      | file md5             |
| X-BKREPO-OVERWRITE  | boolean | 否       | false  | 是否覆盖已存在文件                                           | overwrite exist file |
| X-BKREPO-EXPIRES    | long    | 否       | 0      | 过期时间，单位天(0代表永久保存)                              | file expired days    |
| X-BKREPO-META-{key} | string  | 否       | 无     | 文件元数据，{key}表示元数据key，可以添加多个。key大小写不敏感，按小写存储 | file metadata        |
| X-BKREPO-META       | string  | 否       | 无     | 文件元数据，格式为base64(key1=value1&key2=value2)。key大小写敏感。当`X-BKREPO-META-{key}`不能满足需求时可用此字段代替 | file metadata        |

- 响应体：

```
{
    "code": 0,
    "message": null,
    "data": {
        "nodeInfo": {
            "createdBy": "anonymous",
            "createdDate": "2022-06-14T09:53:01.045",
            "lastModifiedBy": "anonymous",
            "lastModifiedDate": "2022-06-14T09:53:01.045",
            "recentlyUseDate": null,
            "folder": false,
            "path": "/",
            "name": "license-3.29.json",
            "fullPath": "/license-3.29.json",
            "size": 218743,
            "sha256": "f5aad44c852fb63887c06b92e653999088715a28e0c18ef2185e625ad5b3efcc",
            "md5": "d6104b50d44513a3306edd9dadf116e9",
            "metadata": {},
            "systemMetadata": {},
            "projectId": "public-global",
            "repoName": "spdx-license",
            "copyFromCredentialsKey": null,
            "copyIntoCredentialsKey": null
        },
        "createdBy": "anonymous",
        "createdDate": "2022-06-14T09:53:01.045",
        "lastModifiedBy": "anonymous",
        "lastModifiedDate": "2022-06-14T09:53:01.045",
        "folder": false,
        "path": "/",
        "name": "license-3.29.json",
        "fullPath": "/license-3.29.json",
        "size": 218743,
        "sha256": "f5aad44c852fb63887c06b92e653999088715a28e0c18ef2185e625ad5b3efcc",
        "md5": "d6104b50d44513a3306edd9dadf116e9",
        "metadata": {},
        "systemMetadata": {},
        "projectId": "public-global",
        "repoName": "spdx-license"
    },
    "traceId": ""
}
```

- 响应字段说明：

| 字段             | 类型   | 说明         | Description          |
| ---------------- | ------ | ------------ | -------------------- |
| createdBy        | string | 创建者       | create user          |
| createdDate      | string | 创建时间     | create time          |
| lastModifiedBy   | string | 上次修改者   | last modify user     |
| lastModifiedDate | string | 上次修改时间 | last modify time     |
| folder           | bool   | 是否为文件夹 | is folder            |
| path             | string | 节点目录     | node path            |
| name             | string | 节点名称     | node name            |
| fullPath         | string | 节点完整路径 | node full path       |
| size             | long   | 文件大小     | file size            |
| sha256           | string | 文件sha256   | file sha256          |
| md5              | string | 文件md5      | file md5 checksum    |
| metadata         | object | 节点元数据   | node metadata        |
| projectId        | string | 节点所属项目 | node project id      |
| repoName         | string | 节点所属仓库 | node repository name |



## 导入许可证数据（页面功能不使用）

- API：GET /analyst/api/license/import
- API 名称：import_license_data
- 功能说明：
  - 中文：接口导入许可证数据
  - English：import license data
- 请求体：无

- 请求参数说明：

| 字段 | 类型   | 是否必须 | 默认值 | 说明               | DESCRIPTION |
| ---- | ------ | -------- | ------ | ------------------ | ----------- |
| path | String | 是       | 无     | 文件所在服务器路径 | page number |

- 响应体：

```json
{
    "code": 0,
    "message": null,
    "data": true,
    "traceId": ""
}
```

- 响应字段说明：data：true  导入成功

## 分页查询许可证信息

- API：GET /analyst/api/license/list
- API 名称：query_license_data
- 功能说明：
  - 中文：分页查询许可证数据
  - English：query license data
- 请求体：无

- 请求参数说明：

| 字段                  | 类型    | 是否必须 | 默认值 | 说明                         | DESCRIPTION       |
| --------------------- | ------- | -------- | ------ | ---------------------------- | ----------------- |
| pageNumber            | Number  | 否       | 1      | 分页页码                     | page number       |
| pageSize              | Number  | 否       | 20     | 分页大小                     | page size         |
| name                  | String  | 否       | 无     | 许可证全称或简称（筛选条件） | licenseId or name |
| isTrust               | Boolean | 否       | 无     | 是否可信（筛选条件）         | is trust          |
| isDeprecatedLicenseId | Boolean | 否       | 无     | 是否推荐使用（筛选条件）     | is deprecated     |

- 响应体：

```json
{
    "code": 0,
    "message": null,
    "data": {
        "pageNumber": 1,
        "pageSize": 20,
        "totalRecords": 5,
        "totalPages": 1,
        "records": [
            {
                "createdBy": "anonymous",
                "createdDate": "2022-06-08T18:05:17.732",
                "lastModifiedBy": "anonymous",
                "lastModifiedDate": "2022-06-08T18:05:17.738",
                "name": "LaTeX Project Public License v1.2",
                "licenseId": "LPPL-1.2",
                "seeAlso": [
                    "http://www.latex-project.org/lppl/lppl-1-2.txt"
                ],
                "reference": "https://spdx.org/licenses/LPPL-1.2.html",
                "isDeprecatedLicenseId": false,
                "isOsiApproved": false,
                "isFsfLibre": true,
                "detailsUrl": "https://spdx.org/licenses/LPPL-1.2.json",
                "isTrust": true,
                "risk": null
            }
        ],
        "page": 1,
        "count": 5
    },
    "traceId": ""
}
```

- 响应字段说明：见文件开头所述【许可证数据说明】



## 查询所有许可信息（页面功能不使用）

- API：GET /analyst/api/license/all
- API 名称：query_all_license_data
- 功能说明：
  - 中文：查询所有许可证数据
  - English：query all license data
- 请求体：无

- 请求参数说明：无

- 响应体：

```
{
    "code": 0,
    "message": null,
    "data": [
        {
            "createdBy": "anonymous",
            "createdDate": "2022-06-08T18:04:49.209",
            "lastModifiedBy": "anonymous",
            "lastModifiedDate": "2022-06-08T18:04:49.217",
            "name": "Creative Commons Attribution No Derivatives 2.0 Generic",
            "licenseId": "CC-BY-NC-ND-2.0",
            "seeAlso": [
                "https://creativecommons.org/licenses/by-nc-nd/2.0/legalcode"
            ],
            "reference": "https://spdx.org/licenses/CC-BY-NC-ND-2.0.html",
            "isDeprecatedLicenseId": false,
            "isOsiApproved": false,
            "isFsfLibre": null,
            "detailsUrl": "https://spdx.org/licenses/CC-BY-NC-ND-2.0.json",
            "isTrust": true,
            "risk": null
        }
    ]
}
```

- 响应字段说明：见文件开头所述【许可证数据说明】



## 查询许可证详细信息

- API：GET /analyst/api/license/info
- API 名称：query_license_info
- 功能说明：
  - 中文：查询许可证详细信息
  - English：query all license data
- 请求体：无

- 请求参数说明：

| 字段      | 类型   | 是否必须 | 默认值 | 说明           | DESCRIPTION |
| --------- | ------ | -------- | ------ | -------------- | ----------- |
| licenseId | String | 是       | 无     | 许可证唯一标识 | license id  |

- 响应体：

```
{
    "code": 0,
    "message": null,
    "data": {
        "createdBy": "anonymous",
        "createdDate": "2022-06-08T18:04:49.209",
        "lastModifiedBy": "anonymous",
        "lastModifiedDate": "2022-06-08T18:04:49.217",
        "name": "Creative Commons Attribution Non Commercial No Derivatives 2.0 Generic",
        "licenseId": "CC-BY-NC-ND-2.0",
        "seeAlso": [
            "https://creativecommons.org/licenses/by-nc-nd/2.0/legalcode"
        ],
        "reference": "https://spdx.org/licenses/CC-BY-NC-ND-2.0.html",
        "isDeprecatedLicenseId": false,
        "isOsiApproved": false,
        "isFsfLibre": null,
        "detailsUrl": "https://spdx.org/licenses/CC-BY-NC-ND-2.0.json",
        "isTrust": true,
        "risk": null
    },
    "traceId": ""
}
```

- 响应字段说明：见文件开头所述【许可证数据说明】



## 更新许可证信息(切换【合规】/【不合规】)

- API：POST /analyst/api/license/{licenseId}
- API 名称：update_license_info
- 功能说明：
  - 中文：更新许可证信息
  - English：update license info
- 请求参数说明：

| 字段      | 类型   | 是否必须 | 默认值 | 说明                       | DESCRIPTION |
| --------- | ------ | -------- | ------ | -------------------------- | ----------- |
| licenseId | String | 是       | 无     | 许可证唯一标识（路径参数） | license id  |

- 响应体：

```
{
    "code": 0,
    "message": null,
    "data": null,
    "traceId": ""
}
```



