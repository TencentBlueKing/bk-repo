## 通用接口协议说明

各个依赖源有单独的服务接口协议，除了这类接口，系统其余接口使用统一的接口协议，如repository、auth服务提供的接口。

此部分对系统统一接口协议作介绍。

### 公共请求头

| 字段          | 类型   | 是否必须 | 默认值 | 说明                                              | Description       |
| ------------- | ------ | -------- | ------ | ------------------------------------------------- | ----------------- |
| Authorization | string | 否       | 无     | Basic Auth认证头，Basic base64(username:password) | Basic Auth header |

### 统一响应格式

- 响应体格式

  application/json

- 响应体内容

  ```json
  {
    "code" : 0,
    "message" : null,
    "data" : [object],
    "traceId" : ""
  }
  ```

- 响应字段说明

  | 字段    | 类型   | 说明                                    | Description               |
  | ------- | ------ | --------------------------------------- | ------------------------- |
  | code    | bool   | 错误编码。 0表示success，>0表示失败错误 | 0:success, other: failure |
  | message | string | 错误提示消息，请求成功此字段为null      | the failure message       |
  | data    | object | 响应数据，允许为null                    | response data             |
  | traceId | string | 请求跟踪id                              | trace id                  |


**TIPS: 无特殊说明，后文的接口响应体说明只介绍data字段**

### 统一分页接口响应格式

| 字段       | 类型 | 说明         | Description  |
| ---------- | ---- | ------------ | ------------ |
| totalRecords | long | 节点信息     | total count  |
| pageNumber   | int  | 当前页       | page number |
| pageSize   | int  | 每页记录数   | page size    |
| totalPages | long  | 总页数       | total pages  |
| records    | list | 数据记录列表 | record list  |

### 统一时间响应格式

接口返回的时间，使用`ISO_DATETIME`格式，如：2011-12-03T10:15:30

### 通用概念

| 名称              | 解释                                                         |
| ----------------- | ------------------------------------------------------------ |
| project/projectId | 项目id，全局唯一                                             |
| repo/repoName     | 仓库名称，项目内唯一                                         |
| fullPath          | 节点完整路径                                                 |
| path              | 节点目录，在restful格式的url中也可以表示fullPath，后台会自动识别 |

### 统一错误码

| 错误码 | 错误原因     |
| ------ | ------------ |
| 250101 | 系统错误     |
| 250102 | 请求参数缺失 |
| 250103 | 请求参数非法 |
| 250104 | 请求内容非法 |
| ...    | 待补充       |