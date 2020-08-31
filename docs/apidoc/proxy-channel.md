## ProxyChannel代理源相关接口

proxy channel接口使用统一接口协议，公共部分请参照[通用接口协议说明](./common.md)

### 查询公有源列表

- API: POST /repository/api/proxy-channel/list/public/{repoType}

- API 名称: list_public_proxy_channel

- 功能说明：

  - 中文：列表查询公有源
  - English：list puiblic proxy channel

- 请求体

  此接口无请求体

- 请求字段说明

  | 字段     | 类型   | 是否必须 | 默认值 | 说明             | Description |
  | -------- | ------ | -------- | ------ | ---------------- | ----------- |
  | repoType | string | 是       | 无     | 仓库类别，枚举值 | repo type   |

- 响应体

  ```json
  {
    "code" : 0,
    "message" : null,
    "data" : [ {
      "id" : "5f48b52fdf23460c0e2251e9",
      "public" : true,
      "name" : "maven-center",
      "url" : "http://http://center.maven.com",
      "repoType" : "MAVEN"
    } ],
    "traceId" : ""
  }
  ```

- data字段说明

  | 字段     | 类型    | 说明         | Description       |
  | -------- | ------- | ------------ | ----------------- |
  | id       | string  | 代理源id     | proxy channel id  |
  | public   | boolean | 是否为公有源 | is public channel |
  | name     | string  | 代理源名称   | repo name         |
  | url      | string  | 代理源url    | repo category     |
  | repoType | boolean | 仓库类型     | repo type         |

