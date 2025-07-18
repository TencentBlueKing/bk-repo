# 通用接口协议

各个依赖源有单独的服务接口协议，除了这类接口，系统其余接口使用统一的通用接口协议。

此部分对系统的通用接口协议作介绍。

## 公共请求头

### 对接个人账号(通用)
|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|Authorization|string|是|无|Basic Auth认证头，Basic base64(username:password)|Basic Auth header|

### 对接合作系统，需要先申请bkrepo appId
|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|Authorization|string|是|无|自定义Auth认证头，Platform base64(accessKey:secretKey)|Platform Auth header|
|X-BKREPO-UID|string|是|无|实际操作用户，需要有对应资源的操作权限|operate user|

## 统一响应格式

- 响应体格式

  `application/json`

- 响应体内容

  ```json
  {
    "code" : 0,
    "message" : null,
    "data" : [object],
    "traceId" : null
  }
  ```

- 响应字段说明

  |字段|类型|说明|Description|
  |---|---|---|---|
  |code|bool|错误编码。 0表示success，>0表示失败错误|0:success, other: failure|
  |message|string|错误提示消息，请求成功此字段为null|the failure message|
  |data|object|响应数据，允许为null|response data|
  |traceId|string|请求跟踪id，允许为null|trace id|

  **TIPS: 无特殊说明，后文的接口响应体说明只介绍data字段**

## 统一分页接口响应格式

|字段|类型|说明|Description|
|---|---|---|---|
|totalRecords|long|节点信息|total count|
|pageNumber|int|当前页|page number|
|pageSize|int|每页记录数|page size|
|totalPages|long|总页数|total pages|
|records|list|数据记录列表|record list|

## 统一时间响应格式

接口返回的时间，使用`ISO_DATETIME`格式，如：`2011-12-03T10:15:30`

## 通用概念

|名称|解释|
|---|---|
|project/projectId|项目id，全局唯一|
|repo/repoName|仓库名称，项目内唯一|
|fullPath|节点完整路径|
|path|节点目录，在restful格式的url中也可以表示fullPath，后台会自动处理|

## 统一错误码

| 错误码   | 错误原因                                       |
| :------- | :------------------------------------------------------- |
| 0        | 成功                                                    |
| 250102   | 系统繁忙，请稍后再试                                    |
| 250103   | 参数缺失                                                |
| 250104   | 参数不能为空                                            |
| 250105   | 参数无效                                                |
| 250106   | 请求内容无效                                            |
| 250107   | 资源已存在                                              |
| 250108   | 资源不存在                                              |
| 250109   | 资源已过期                                              |
| 250110   | 资源已归档,恢复它大概需要12-24小时，请耐心等候          |
| 250111   | 资源已压缩,正在解压，请耐心等候                        |
| 250112   | 不支持的操作                                            |
| 250113   | 访问被拒绝                                              |
| 250114   | 认证失败                                                |
| 250115   | 内部依赖服务被熔断                                      |
| 250116   | 内部依赖服务调用异常                                    |
| 250117   | 服务认证失败                                            |
| 250118   | 请求头缺失                                              |
| 250119   | 不支持的Media Type                                      |
| 250120   | 请求Range格式无效                                       |
| 250121   | 修改密码失敗                                            |
| 250122   | 不允许跨地点操作                                        |
| 250123   | 不接受的Media Type                                      |
| 250124   | 请求过多                                                |
| 250125   | 流水线不是运行状态                                      |
| 250126   | 配置无效                                                |
| 250127   | 获取锁失败                                              |
| 250128   | 资源请求量超过限流值                                    |
|...| [详情](common/error-code.md)              |
