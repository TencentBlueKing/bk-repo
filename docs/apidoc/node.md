## Node节点接口说明

Node接口使用统一接口协议，公共部分请参照[通用接口协议说明](./common.md)

### 查询节点详情

- API: GET /repository/api/node/detail/{projectId}/{repoName}/{fullPath}

- API 名称: query_node_detail

- 功能说明：
  - 中文：查询节点详情
  - English：query node detail

- 请求体
  此接口请求体为空

- 请求字段说明

  | 字段      | 类型   | 是否必须 | 默认值 | 说明     | Description  |
  | --------- | ------ | -------- | ------ | -------- | ------------ |
  | projectId | string | 是       | 无     | 项目名称 | project name |
  | repoName  | string | 是       | 无     | 仓库名称 | repo name    |
  | fullPath  | string | 是       | 无     | 完整路径 | full path    |

- 响应体

  ```json
  {
    "code": 0,
    "message": null,
    "data": {
      "projectId" : "test",
      "repoName" : "generic-local",
      "path" : "/",
      "name" : "test.json",
      "fullPath" : "/test.json",
      "folder" : false,
      "size" : 34,
      "sha256" : "6a7983009447ecc725d2bb73a60b55d0ef5886884df0ffe3199f84b6df919895",
      "md5" : "2947b3932900d4534175d73964ec22ef",
      "metadata": {
        "key": "value"
      },
      "createdBy" : "admin",
      "createdDate" : "2020-07-27T16:02:31.394",
      "lastModifiedBy" : "admin",
      "lastModifiedDate" : "2020-07-27T16:02:31.394"
    },
    "traceId": ""
  }
  ```

- data字段说明

  | 字段             | 类型   | 说明                        | Description          |
  | ---------------- | ------ | --------------------------- | -------------------- |
  | projectId        | string | 节点所属项目                | node project id      |
  | repoName         | string | 节点所属仓库                | node repository name |
  | path             | string | 节点目录                    | node path            |
  | name             | string | 节点名称                    | node name            |
  | fullPath         | string | 节点完整路径                | node full path       |
  | folder           | bool   | 是否为文件夹                | is folder            |
  | size             | long   | 节点大小                    | file size            |
  | sha256           | string | 节点sha256                  | file sha256          |
  | md5              | string | 节点md5                     | file md5 checksum    |
  | metadata         | object | 节点元数据，key-value键值对 | node metadata        |
  | createdBy        | string | 创建者                      | create user          |
  | createdDate      | string | 创建时间                    | create time          |
  | lastModifiedBy   | string | 上次修改者                  | last modify user     |
  | lastModifiedDate | string | 上次修改时间                | last modify time     |

### 分页查询节点

- API: GET /repository/api/node/page/{projectId}/{repoName}/{fullPath}?pageNumber=0&pageSize=20&includeFolder=true&deep=false

- API 名称: list_node_page

- 功能说明：
  - 中文：分页查询节点，返回的结果列表中目录在前，文件在后，并按照文件名称排序
  - English：list node page
  
- 请求体
  此接口请求体为空
  
- 请求字段说明

  | 字段          | 类型    | 是否必须 | 默认值 | 说明               | Description       |
  | ------------- | ------- | -------- | ------ | ------------------ | ----------------- |
  | projectId     | string  | 是       | 无     | 项目名称           | project name      |
  | repoName      | string  | 是       | 无     | 仓库名称           | repo name         |
  | fullPath      | string  | 是       | 无     | 完整路径           | full path         |
  | pageNumber    | int     | 否       | 0      | 当前页             | current page      |
  | pageSize      | int     | 否       | 20     | 分页大小           | page size         |
  | includeFolder | boolean | 否       | true   | 是否包含目录       | is include folder |
  | deep          | boolean | 否       | false  | 是否查询子目录节点 | deep query        |

- 响应体

  ```json
  {
    "code": 0,
    "message": null,
    "data": {
      "pageNumber": 0,
      "pageSize": 1,
      "totalRecords": 18,
      "totalPages": 18,
      "records": [
        {
          "projectId" : "test",
          "repoName" : "generic-local",
          "path" : "/",
          "name" : "test.json",
          "fullPath" : "/test.json",
          "folder" : false,
          "size" : 34,
          "sha256" : "6a7983009447ecc725d2bb73a60b55d0ef5886884df0ffe3199f84b6df919895",
          "md5" : "2947b3932900d4534175d73964ec22ef",
          "createdBy" : "admin",
          "createdDate" : "2020-07-27T16:02:31.394",
          "lastModifiedBy" : "admin",
          "lastModifiedDate" : "2020-07-27T16:02:31.394"
        }
      ]
    },
    "traceId": ""
  }
  ```

- record字段说明

  | 字段             | 类型   | 说明                        | Description          |
  | ---------------- | ------ | --------------------------- | -------------------- |
  | projectId        | string | 节点所属项目                | node project id      |
  | repoName         | string | 节点所属仓库                | node repository name |
  | path             | string | 节点目录                    | node path            |
  | name             | string | 节点名称                    | node name            |
  | fullPath         | string | 节点完整路径                | node full path       |
  | folder           | bool   | 是否为文件夹                | is folder            |
  | size             | long   | 节点大小                    | file size            |
  | sha256           | string | 节点sha256                  | file sha256          |
  | md5              | string | 节点md5                     | file md5 checksum    |
  | metadata         | object | 节点元数据，key-value键值对 | node metadata        |
  | createdBy        | string | 创建者                      | create user          |
  | createdDate      | string | 创建时间                    | create time          |
  | lastModifiedBy   | string | 上次修改者                  | last modify user     |
  | lastModifiedDate | string | 上次修改时间                | last modify time     |




### 创建目录

- API: POST /repository/api/node/mkdir/{projectId}/{repoName}/{path}

- API 名称: mkdir

- 功能说明：
  - 中文：创建目录节点
  - English：create directory node
  
- 请求体
  此接口请求体为空
  
- 请求字段说明

  | 字段      | 类型   | 是否必须 | 默认值 | 说明     | Description  |
  | --------- | ------ | -------- | ------ | -------- | ------------ |
  | projectId | string | 是       | 无     | 项目名称 | project name |
  | repoName  | string | 是       | 无     | 仓库名称 | repo name    |
  | path      | string | 是       | 无     | 完整路径 | full path    |

- 响应体

  ```json
  {
    "code": 0,
    "message": null,
    "data": null,
    "traceId": ""
  }
  ```

- data字段说明

  请求成功无返回数据

### 删除节点

- API: DELETE /repository/api/node/delete/{projectId}/{repoName}/{fullPath}

- API 名称: delete_node

- 功能说明：
  - 中文：删除节点，同时支持删除目录和文件节点
  - English：delete node
  
- 请求体
  此接口请求体为空
  
- 请求字段说明

  | 字段      | 类型   | 是否必须 | 默认值 | 说明     | Description  |
  | --------- | ------ | -------- | ------ | -------- | ------------ |
  | projectId | string | 是       | 无     | 项目名称 | project name |
  | repoName  | string | 是       | 无     | 仓库名称 | repo name    |
  | fullPath  | string | 是       | 无     | 完整路径 | full path    |

- 响应体

  ```json
  {
    "code": 0,
    "message": null,
    "data": null,
    "traceId": ""
  }
  ```

- data字段说明

  请求成功无返回数据

### 更新节点

- API: POST /repository/api/node/update/{projectId}/{repoName}/{fullPath}

- API 名称: update_node

- 功能说明：
  - 中文：更新节点信息，目前支持修改文件过期时间
  - English：update node info
  
- 请求体

  ```json
  {
    "expires": 0
  }
  ```

  

- 请求字段说明

  | 字段      | 类型   | 是否必须 | 默认值 | 说明                            | Description  |
  | --------- | ------ | -------- | ------ | ------------------------------- | ------------ |
  | projectId | string | 是       | 无     | 项目名称                        | project name |
  | repoName  | string | 是       | 无     | 仓库名称                        | repo name    |
  | fullPath  | string | 是       | 无     | 完整路径                        | full path    |
  | expires   | long   | 否       | 0      | 过期时间，单位天(0代表永久保存) | expires day  |

- 响应体

  ```json
  {
    "code": 0,
    "message": null,
    "data": null,
    "traceId": ""
  }
  ```

- data字段说明

  请求成功无返回数据

### 重命名节点

- API: POST /repository/api/node/rename/{projectId}/{repoName}/{fullPath}?newFullPath=/data/new_name.text

- API 名称: rename_node

- 功能说明：
  - 中文：重命名节点
  - English：rename node
  
- 请求体

  此接口请求体为空

- 请求字段说明

  | 字段        | 类型   | 是否必须 | 默认值 | 说明         | Description   |
  | ----------- | ------ | -------- | ------ | ------------ | ------------- |
  | projectId   | string | 是       | 无     | 项目名称     | project name  |
  | repoName    | string | 是       | 无     | 仓库名称     | repo name     |
  | fullPath    | string | 是       | 无     | 完整路径     | full path     |
  | newFullPath | string | 是       | 无     | 新的完整路径 | new full path |

- 响应体

  ``` json
  {
    "code": 0,
    "message": null,
    "data": null,
    "traceId": ""
  }
  ```

- data字段说明

  请求成功无返回数据

### 移动节点

- API: POST /repository/api/node/move

- API 名称: move_node

- 功能说明：
  - 中文：移动节点
  - English：move node
  
- 请求体

  ``` json
  {
    "srcProjectId": "",
    "srcRepoName": "",
    "srcFullPath": "",
    "destProjectId": "",
    "destRepoName": "",
    "destFullPath": "",
    "overwrite": false
  }
  ```

- 请求字段说明

  | 字段          | 类型    | 是否必须 | 默认值 | 说明                           | Description          |
  | ------------- | ------- | -------- | ------ | ------------------------------ | -------------------- |
  | srcProjectId  | string  | 是       | 无     | 源项目名称                     | src project name     |
  | srcRepoName   | string  | 是       | 无     | 源仓库名称                     | src repo name        |
  | srcFullPath   | string  | 是       | 无     | 源完整路径                     | src full path        |
  | destProjectId | string  | 否       | null   | 目的项目名称。传null表示源项目 | dest project name    |
  | destRepoName  | string  | 否       | null   | 目的仓库名称。传null表示源仓库 | dest repo name       |
  | destFullPath  | string  | 是       | 无     | 目的完整路径                   | dest full path       |
  | overwrite     | boolean | 否       | false  | 同名文件是否覆盖               | overwrite  same node |

- 响应体

  ``` json
  {
    "code": 0,
    "message": null,
    "data": null,
    "traceId": ""
  }
  ```

- data字段说明

  请求成功无返回数据

### 拷贝节点

- API: POST /repository/api/node/copy

- API 名称: copy_node

- 功能说明：
  - 中文：拷贝节点
  - English：copy node
  
- 请求体

  ``` json
  {
    "srcProjectId": "",
    "srcRepoName": "",
    "srcFullPath": "",
    "destProjectId": "",
    "destRepoName": "",
    "destFullPath": "",
    "overwrite": false
  }
  ```

- 请求字段说明

  | 字段          | 类型    | 是否必须 | 默认值 | 说明                           | Description          |
  | ------------- | ------- | -------- | ------ | ------------------------------ | -------------------- |
  | srcProjectId  | string  | 是       | 无     | 源项目名称                     | src project name     |
  | srcRepoName   | string  | 是       | 无     | 源仓库名称                     | src repo name        |
  | srcFullPath   | string  | 是       | 无     | 源完整路径                     | src full path        |
  | destProjectId | string  | 否       | null   | 目的项目名称。传null表示源项目 | dest project name    |
  | destRepoName  | string  | 否       | null   | 目的仓库名称。传null表示源仓库 | dest repo name       |
  | destFullPath  | string  | 是       | 无     | 目的完整路径                   | dest full path       |
  | overwrite     | boolean | 否       | false  | 同名文件是否覆盖               | overwrite  same node |

- 响应体

  ``` json
  {
    "code": 0,
    "message": null,
    "data": null,
    "traceId": ""
  }
  ```

- data字段说明

  请求成功无返回数据

### 统计节点大小信息

- API: GET /repository/api/node/size/{projectId}/{repoName}/{fullPath}

- API 名称: compute_node_size

- 功能说明：
  - 中文：统计节点大小信息
  - English：compute node size

- 请求体
  此接口请求体为空

- 请求字段说明

  | 字段      | 类型   | 是否必须 | 默认值 | 说明     | Description  |
  | --------- | ------ | -------- | ------ | -------- | ------------ |
  | projectId | string | 是       | 无     | 项目名称 | project name |
  | repoName  | string | 是       | 无     | 仓库名称 | repo name    |
  | fullPath  | string | 是       | 无     | 完整路径 | full path    |

- 响应体

  ```json
  {
    "code": 0,
    "message": null,
    "data": {
      "subNodeCount": 32,
      "size": 443022203
    },
    "traceId": ""
  }
  ```

- data字段说明

  | 字段         | 类型 | 说明                   | Description         |
  | ------------ | ---- | ---------------------- | ------------------- |
  | subNodeCount | long | 子节点数量（包括目录） | sub node count      |
  | size         | long | 目录/文件大小          | directory/file size |

### 自定义查询

- API: POST /repository/api/node/query

- API 名称: node query

- 功能说明：

  - 中文：节点自定义查询。最外层的查询条件中必须包含projectId条件和repoName条件，且projectId查询操作必须为EQ，repoName必须为EQ或IN。
  - English：query node

- 请求体

  ``` json
  # 分页查询在项目test下, 仓库为generic-local1或generic-local2，文件名以.tgz结尾的文件，并按照文件名和大小排序，查询结果包含name、fullPath、size、sha256、md5、metadata字段，
  {
    "select": ["name", "fullPath", "size", "sha256", "md5", "metadata"],
    "page": {
      "pageNumber": 0,
      "pageSize": 20
    },
    "sort": {
      "properties": ["name", "size"],
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
          "field": "name",
          "value": ".tgz",
          "operation": "SUFFIX"
        },
        {
          "field": "folder",
          "value": "false",
          "operation": "EQ"
        },
      ],
      "relation": "AND"
    }
  }
  ```

- 请求字段说明

  | 字段   | 类型      | 是否必须 | 默认值               | 说明                                                       | Description        |
  | ------ | --------- | -------- | -------------------- | ---------------------------------------------------------- | ------------------ |
  | select | list      | 否       | 所有字段             | 筛选字段列表。支持筛选字段参照`查询节点详情`接口响应字段。 | select fields list |
  | page   | PageLimit | 否       | pageNumber=1, pageSize=20。 | 分页参数                                                   | page limit         |
  | sort   | Sort      | 否       | 无                   | 排序规则                                                   | sort rule          |
  | rule   | Rule      | 是       | 无                   | 自定义查询规则                                             | custom query rule  |

  - PageLimit

    | 字段    | 类型 | 是否必须 | 默认值 | 说明              | Description  |
    | ------- | ---- | -------- | ------ | ----------------- | ------------ |
    | pageNumber | int  | 否       | 0      | 当前页(第1页开始) | page number |
    | pageSize   | int  | 否       | 20     | 每页数量          | page size  |

  - Sort

    | 字段       | 类型     | 是否必须 | 默认值 | 说明                            | Description     |
    | ---------- | -------- | -------- | ------ | ------------------------------- | --------------- |
    | properties | [string] | 是       | 无     | 排序字段                        | sort properties |
    | direction  | enum     | 否       | ASC    | 排序方向。支持ASC升序和DESC降序 | sort direction  |

  - Rule

    > Rule包含两种格式，一种用于表示嵌套规则NestedRule，另一种用于表示条件规则QueryRule

    - 嵌套规则NestedRule

      | 字段     | 类型   | 是否必须 | 默认值 | 说明                                        | Description   |
      | -------- | ------ | -------- | ------ | ------------------------------------------- | ------------- |
      | rules    | [Rule] | 是       | 无     | 规则列表，可以任意嵌套NestedRule和QueryRule | rule list     |
      | relation | enum   | 否       | AND    | 规则列表rules的关系。支持AND、OR、NOR       | rule relation |

    - 条件规则QueryRule

      | 字段      | 类型   | 是否必须 | 默认值 | 说明                               | Description |
      | --------- | ------ | -------- | ------ | ---------------------------------- | ----------- |
      | field     | string | 是       | 无     | 查询字段                           | filed       |
      | value     | any    | 是       | 无     | 查询值。数据类型和查询操作类型相关 | value       |
      | operation | enum   | 否       | EQ     | 查询操作类型。枚举类型见下文       | operation   |

    - OperationType查询操作类型

      | 枚举值   | 对应查询值类型             | Description                                                  |
      | -------- | -------------------------- | ------------------------------------------------------------ |
      | EQ       | string/boolean/number/date | 等于                                                         |
      | NE       | number/date                | 不等于                                                       |
      | LTE      | number/date                | 小于或者等于                                                 |
      | LT       | number/date                | 小于                                                         |
      | GTE      | number/date                | 大于或者等于                                                 |
      | GT       | number/date                | 大于                                                         |
      | BEFORE   | date                       | 在某个时间之间，不包含等于                                   |
      | AFTER    | date                       | 在某个时间之后，不包含等于                                   |
      | IN       | list                       | 包含于                                                       |
      | PREFIX   | string                     | 以xxx为前缀                                                  |
      | SUFFIX   | string                     | 以xxx为后缀                                                  |
      | MATCH    | string                     | 通配符匹配，\*表示匹配任意字符。如\*test\*表示包含test的字符串 |
      | NULL     | null                       | 匹配查询字段为空，filed == null                              |
      | NOT_NULL | null                       | 匹配查询字段不为空，filed != null                            |

- 响应体

  ``` json
  {
    "code": 0,
    "message": null,
    "data": [
      {}
    ],
    "traceId": ""
  }
  ```

- 列表record字段说明

  由`select`筛选字段条件决定
