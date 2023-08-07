# Repository仓库接口

[toc]


## 更新仓库信息（清理策略配置部分）

- API: POST /repository/api/repo/update/{projectId}/{repoName}

- API 名称: update_repo

- 功能说明：

  - 中文：更新仓库信息
  - English：update repo

- 请求体

  - 依赖源
  
  ```json
  {
      "configuration":{
          "proxy":{
              "channelList": []
          },
          "settings":{
              "system": true
          },
          "type":"composite",
          "webHook":{
              "webHookList": []
          },
          "description":"",
          "public":"false",
          "cleanStrategy":{
              "autoClean":"true",
              "reserveVersions":"5",
              "reserveDays":"1",
              "rule":{
                  "relation": "OR",
                  "rules":[
                      {"field": "metadata.groupId", "value":"com", "operation": "EQ"},
                      {"field": "metadata.groupId", "value":"comx", "operation": "EQ"}
                  ]
              }
          }
      }
  }
  ```
  
  - Generic
  
  ```
  {
      "configuration":{
          "proxy":{
              "channelList": []
          },
          "settings":{
              "system": true
          },
          "type":"composite",
          "webHook":{
              "webHookList": []
          },
          "description":"",
          "public":"false",
          "cleanStrategy":{
              "autoClean":"true",
              "rule":{
                  "relation": "AND",
                  "rules":[
                  	{"field" : "projectId","value" : "test","operation" : "EQ"}, 
                  	{"field" : "repoName","value" : "rrreert","operation" : "EQ"},
              		{
              			"rules" : [ 
              				{
                                  "rules": [
                                      {
                                          "field": "reserveDays",
                                          "value": 30,
                                          "operation": "LTE"
                                      },
                                      {
                                          "field": "path",
                                          "value": "/",
                                          "operation": "REGEX"
                                      },
                                      {
                                          "rules": [
                                              {
                                                  "field": "name",
                                                  "value": "bbbb",
                                                  "operation": "MATCH"
                                              }
                                          ],
                                          "relation": "OR"
                                      }
                                  ],
                                  "relation" : "AND"
                              },
              				{
                                  "rules": [
                                      {
                                          "field": "reserveDays",
                                          "value": 30,
                                          "operation": "LTE"
                                      },
                                      {
                                          "field": "path",
                                          "value": "/a",
                                          "operation": "REGEX"
                                      },
                                      {
                                          "rules": [
                                              {
                                                  "field": "metadata.pipilineId",
                                                  "value": "123",
                                                  "operation": "MATCH"
                                              },
                                              {
                                                  "field": "name",
                                                  "value": "product",
                                                  "operation": "EQ"
                                              }
                                          ],
                                          "relation": "OR"
                                      }
                                  ],
                                  "relation" : "AND"
                              }
              				
              		 	],
              			"relation" : "OR"
            			}
                  ]
              }
          }
      }
  }
  ```
  
  
  
- 请求字段说明

- **RepositoryCleanStrategy**

| 字段            | 类型    | 是否必须           | 默认值 | 说明             | Description            |
| --------------- | ------- | ------------------ | ------ | ---------------- | ---------------------- |
| autoClean       | boolean | 是                 | false  | 是否开启自动清理 | is auto clean repo     |
| reserveVersions | Long    | 开启自动清理，必填 | 20     | 保留版本数       | reserve version number |
| reserveDays     | Long    | 开启自动清理，必填 | 30     | 保留天数         | reserve day number     |
| rule            | Rule    | 否                 | 无     | 元数据保留规则   | metadata reverse rule  |

- **Rule**

| 字段     | 类型   | 是否必须 | 默认值 | 说明                                        | Description            |
| -------- | ------ | -------- | ------ | ------------------------------------------- | ---------------------- |
| relation | string | 否       | AND    | 规则之间的关系rule relation                 |                        |
| rules    | [Rule] | 是       | 无     | 规则列表，可以任意嵌套NestedRule和QueryRule | reserve version number |

- **条件规则QueryRule**

| 字段      | 类型   | 是否必须 | 默认值 | 说明                               | Description |
| --------- | ------ | -------- | ------ | ---------------------------------- | ----------- |
| field     | string | 是       | 无     | 查询字段，元数据：metadata.key     | filed       |
| value     | any    | 否       | 无     | 查询值。数据类型和查询操作类型相关 | value       |
| operation | enum   | 否       | EQ     | 查询操作类型。枚举类型见下文       | operation   |

- **OperationType查询操作类型**

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
| NIN      | list                       | 不包含于                                                     |
| PREFIX   | string                     | 以xxx为前缀                                                  |
| SUFFIX   | string                     | 以xxx为后缀                                                  |
| MATCH    | string                     | 通配符匹配，\*表示匹配任意字符。如\*test\*表示包含test的字符串 |
| NULL     | null                       | 匹配查询字段为空，filed == null                              |
| NOT_NULL | null                       | 匹配查询字段不为空，filed != null                            |
| CONTAIN  | string                     | 包含                                                         |

- 响应体

  ```json
  {
    "code": 0,
    "message": null,
    "data": null,
    "traceId": null
  }
  ```