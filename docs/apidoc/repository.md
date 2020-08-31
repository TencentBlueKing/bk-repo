## Repository仓库接口说明

repo仓库接口使用统一接口协议，公共部分请参照[通用接口协议说明](./common.md)

repo仓库枚举值请参考后文**仓库公共枚举值说明**部分

### 创建仓库

- API: POST /repository/api/repo/create

- API 名称: create_repo

- 功能说明：

  - 中文：创建仓库
    - English：create repo

- 请求体

  ```json
  {
    "projectId": "test",
    "name": "generic-local",
    "type": "GENERIC",
    "category": "COMPOSITE",
    "public": false,
    "description": "repo description",
    "configuration": null,
    "storageCredentialsKey": null
  }
  ```

- 请求字段说明

  | 字段                  | 类型    | 是否必须 | 默认值    | 说明               | Description             |
  | --------------------- | ------- | -------- | --------- | ------------------ | ----------------------- |
  | projectId             | string  | 是       | 无        | 项目名称           | project name            |
  | name                  | string  | 是       | 无        | 仓库名称           | repo name               |
  | type                  | enum    | 是       | 无        | 仓库类型，枚举值   | repo type               |
  | category              | enum    | 否       | COMPOSITE | 仓库类别，枚举值   | repo category           |
  | public                | boolean | 否       | false     | 是否公开           | is public repo          |
  | description           | string  | 否       | 无        | 仓库描述           | repo description        |
  | configuration         | object  | 否       | 无        | 仓库配置，参考后文 | repo configuration      |
  | storageCredentialsKey | string  | 否       | 无        | 存储凭证key        | storage credentials key |

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



### 分页查询仓库

- API: GET /repository/api/repo/page/{projectId}/{page}/{size}?name=local&type=GENERIC

- API 名称: list_repo_page

- 功能说明：

  - 中文：分页查询仓库
  - English：list repo page

- 请求体

  此接口无请求体

  

- 请求字段说明

  | 字段      | 类型   | 是否必须 | 默认值 | 说明                       | Description  |
  | --------- | ------ | -------- | ------ | -------------------------- | ------------ |
  | projectId | string | 是       | 无     | 项目名称                   | project name |
  | page      | int    | 是       | 无     | 当前页                     | current page |
  | size      | int    | 是       | 无     | 分页数量                   | page size    |
  | name      | string | 否       | 无     | 仓库名称，支持前缀模糊匹配 | repo name    |
  | type      | enum   | 否       | 无     | 仓库类型，枚举值           | repo type    |

- 响应体

  ```json
  {
    "code": 0,
    "message": null,
    "data": {
      "count": 18,
      "page": 0,
      "pageSize": 1,
      "totalPages": 2,
      "records": [
        {
          "projectId" : "test",
          "name" : "local",
          "type" : "GENERIC",
          "category" : "LOCAL",
          "public" : false,
          "description" : "",
          "createdBy" : "system",
          "createdDate" : "2020-03-16T12:13:03.371",
          "lastModifiedBy" : "system",
          "lastModifiedDate" : "2020-03-16T12:13:03.371"
        }
      ]
    },
    "traceId": ""
  }
  ```

- record字段说明

  | 字段             | 类型    | 说明         | Description      |
  | ---------------- | ------- | ------------ | ---------------- |
  | projectId        | string  | 项目id       | project id       |
  | name             | string  | 仓库名称     | repo name        |
  | type             | string  | 仓库类型     | repo type        |
  | category         | string  | 仓库类别     | repo category    |
  | public           | boolean | 是否公开项目 | is public repo   |
  | description      | string  | 仓库描述     | repo description |
  | createdBy        | string  | 创建者       | create user      |
  | createdDate      | string  | 创建时间     | create time      |
  | lastModifiedBy   | string  | 上次修改者   | last modify user |
  | lastModifiedDate | string  | 上次修改时间 | last modify time |



### 查询仓库信息

- API: GET /repository/api/repo/info/{projectId}/{repoName}/{type}

- API 名称: get_repo_info

- 功能说明：

  - 中文：查询仓库详情
  - English：get repo info

- 请求体

  此接口无请求体

- 请求字段说明

  | 字段      | 类型   | 是否必须 | 默认值 | 说明     | Description  |
  | --------- | ------ | -------- | ------ | -------- | ------------ |
  | projectId | string | 是       | 无     | 项目名称 | project name |
  | repoName  | string | 是       | 无     | 仓库名称 | repo name    |
  | type      | enum   | 否       | 无     | 仓库类型 | repo type    |

- 响应体

  ```json
  {
    "code": 0,
    "message": null,
    "data": {
      "projectId" : "test",
      "name" : "local",
      "type" : "GENERIC",
      "category" : "LOCAL",
      "public" : false,
      "description" : "",
      "configuration": {},
      "createdBy" : "system",
      "createdDate" : "2020-03-16T12:13:03.371",
      "lastModifiedBy" : "system",
      "lastModifiedDate" : "2020-03-16T12:13:03.371"
    },
    "traceId": ""
  }
  ```

- data字段说明

  | 字段             | 类型     | 说明                       | Description        |
  | ---------------- | -------- | -------------------------- | ------------------ |
  | projectId        | string   | 项目id                     | project id         |
  | name             | string   | 仓库名称                   | repo name          |
  | type             | string   | 仓库类型                   | repo type          |
  | category         | string   | 仓库类别                   | repo category      |
  | public           | boolean  | 是否公开项目               | is public repo     |
  | description      | string   | 仓库描述                   | repo description   |
  | configuration    | [object] | 仓库配置，参考仓库配置介绍 | repo configuration |
  | createdBy        | string   | 创建者                     | create user        |
  | createdDate      | string   | 创建时间                   | create time        |
  | lastModifiedBy   | string   | 上次修改者                 | last modify user   |
  | lastModifiedDate | string   | 上次修改时间               | last modify time   |



### 更新仓库信息

- API: POST /repository/api/repo/update/{projectId}/{repoName}

- API 名称: update_repo

- 功能说明：

  - 中文：更新仓库信息
  - English：update repo

- 请求体

  ```json
  {
    "public": false,
    "description": "repo description",
    "configuration": null
  }
  ```

- 请求字段说明

  | 字段          | 类型    | 是否必须 | 默认值 | 说明                             | Description        |
  | ------------- | ------- | -------- | ------ | -------------------------------- | ------------------ |
  | public        | boolean | 否       | 无     | 是否公开。null则不修改           | is public repo     |
  | description   | string  | 否       | 无     | 仓库描述。null则不修改           | repo description   |
  | configuration | object  | 否       | 无     | 仓库配置，参考后文。null则不修改 | repo configuration |

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

### 仓库公共枚举值说明

#### 1. 仓库类型

> 用于标识仓库功能类型

| 枚举值   | 说明               |
| -------- | ------------------ |
| GENERIC  | 通用二进制文件仓库 |
| DOCKER   | Docker仓库         |
| MAVEN    | Maven仓库          |
| PYPI     | Pypi仓库           |
| NPM      | Npm仓库            |
| HELM     | Helm仓库           |
| COMPOSER | Composer仓库       |
| RPM      | Rpm仓库            |

#### 2. 仓库类别

> 用于标识仓库类别

| 枚举值    | 说明                                                         |
| --------- | ------------------------------------------------------------ |
| GENERIC   | 本地仓库。普通仓库，上传/下载构件都在本地进行。              |
| REMOTE    | 远程仓库。通过访问远程地址拉取构件，不支持上传               |
| VIRTUAL   | 虚拟仓库。可以组合多个本地仓库和远程仓库拉取构件，不支持上传 |
| COMPOSITE | 组合仓库。具有LOCAL的功能，同时也支持代理多个远程地址进行下载 |



### 仓库配置项

#### 1. 公共配置项

每一类配置都具有下列公共配置项

| 字段     | 类型   | 是否必须 | 默认值 | 说明                                                         | Description        |
| -------- | ------ | -------- | ------ | ------------------------------------------------------------ | ------------------ |
| type     | string | 是       | 无     | 不同类型仓库分别对应local、remote、virtual、composite(小写)，用于反序列化，创建和修改时需要提供该字段 | configuration type |
| settings | object | 否       | 无     | 不同类型仓库可以通过该字段进行差异化配置                     | repo settings      |

#### 2. local本地仓库配置项

| 字段    | 类型    | 是否必须 | 默认值 | 说明            | Description |
| :------ | ------- | -------- | ------ | --------------- | ----------- |
| webHook | WebHook | 否       | 无     | WebHook相关配置 | web hook    |
- ##### WebHook配置项

  | 字段        | 类型                 | 是否必须 | 默认值 | 说明         | Description   |
  | :---------- | -------------------- | -------- | ------ | ------------ | ------------- |
  | webHookList | list<WebHookSetting> | 否       | 无     | WebHook 列表 | web hook list |

- ##### WebHookSetting配置项

  | 字段    | 类型   | 是否必须 | 默认值 | 说明                       | Description         |
  | :------ | ------ | -------- | ------ | -------------------------- | ------------------- |
  | url     | string | 否       | 无     | 远程url地址                | remote web hook url |
  | headers | map    | 否       | 无     | 发起远程url的自定义headers | web hook headers    |

#### 3. remote远程仓库配置项

| 字段    | 类型                       | 是否必须 | 默认值 | 说明     | Description                  |
| ------- | -------------------------- | -------- | ------ | -------- | ---------------------------- |
| proxy   | RemoteProxyConfiguration   | 否       | 无     | 代理配置 | remote proxy configuration   |
| network | RemoteNetworkConfiguration | 否       | -      | 网络配置 | remote network configuration |
| cache   | RemoteCacheConfiguration   | 否       | -      | 缓存配置 | remote cache configuration   |

- ##### RemoteProxyConfiguration

  | 字段     | 类型   | 是否必须 | 默认值 | 说明            | Description          |
  | -------- | ------ | -------- | ------ | --------------- | -------------------- |
  | url      | string | 是       | 无     | 远程仓库url     | remote repo url      |
  | username | string | 否       | 无     | 远程仓库 用户名 | remote repo username |
  | password | string | 否       | 无     | 远程仓库 密码   | remote repo password |

- ##### RemoteNetworkConfiguration

  | 字段           | 类型                      | 是否必须 | 默认值    | 说明                     | Description             |
  | -------------- | ------------------------- | -------- | --------- | ------------------------ | ----------------------- |
  | proxy          | NetworkProxyConfiguration | 是       | 无        | 网络代理配置             | network proxy           |
  | connectTimeout | long                      | 否       | 10 * 1000 | 网络连接超时时间(单位ms) | network connect timeout |
  | readTimeout    | long                      | 否       | 10 * 1000 | 网络读取超时时间(单位ms) | network read timeout    |

- ##### NetworkProxyConfiguration

  | 字段     | 类型   | 是否必须 | 默认值 | 说明           | Description    |
  | -------- | ------ | -------- | ------ | -------------- | -------------- |
  | host     | string | 是       | 无     | 网络代理主机   | proxy host     |
  | port     | int    | 是       | 无     | 网络代理端口   | proxy int      |
  | username | string | 否       | 无     | 网络代理用户名 | proxy username |
  | password | string | 否       | 无     | 网络代理密码   | proxy password |

- ##### RemoteCacheConfiguration

  | 字段       | 类型    | 是否必须 | 默认值 | 说明                                              | Description      |
  | ---------- | ------- | -------- | ------ | ------------------------------------------------- | ---------------- |
  | enabled    | boolean | 否       | true   | 是否开启缓存                                      | cache enabled    |
  | expiration | long    | 否       | -1     | 构件缓存过期时间（单位分钟，0或负数表示永久缓存） | cache expiration |

#### 4. virtual虚拟仓库配置项

| 字段           | 类型                     | 是否必须 | 默认值 | 说明     | Description |
| -------------- | ------------------------ | -------- | ------ | -------- | ----------- |
| repositoryList | list<RepositoryIdentify> | 否       | 无     | 仓库列表 | repo list   |

- ##### RepositoryIdentify

  | 字段      | 类型   | 是否必须 | 默认值 | 说明         | Description |
  | --------- | ------ | -------- | ------ | ------------ | ----------- |
  | projectId | string | 是       | 无     | 代理项目名称 | project id  |
  | name      | string | 是       | 无     | 代理仓库名称 | repo name   |

#### 5. composite组合仓库配置项

| 字段  | 类型               | 是否必须 | 默认值 | 说明         | Description              |
| ----- | ------------------ | -------- | ------ | ------------ | ------------------------ |
| proxy | ProxyConfiguration | 否       | 无     | 仓库代理配置 | repo proxy configuration |

- ##### ProxyConfiguration

  | 字段        | 类型                      | 是否必须 | 默认值 | 说明       | Description        |
  | ----------- | ------------------------- | -------- | ------ | ---------- | ------------------ |
  | channelList | list<ProxyChannelSetting> | 否       | 无     | 代理源列表 | proxy channel list |

- ##### ProxyChannelSetting

  | 字段          | 类型    | 是否必须 | 默认值 | 说明                             | Description                  |
  | ------------- | ------- | -------- | ------ | -------------------------------- | ---------------------------- |
  | public        | boolean | 是       | 无     | 是否为公有源                     | is public                    |
  | channelId     | string  | 否       | 无     | 公有源id, 公有源必须提供         | public channel id            |
  | name          | string  | 否       | 无     | 代理源名称，私有源必选参数       | private proxy channel name   |
  | url           | string  | 否       | 无     | 代理源地址，私有源必选参数       | private proxy channel url    |
  | credentialKey | string  | 否       | 无     | 鉴权凭据key，私有源可选参数      | private proxy credentials id |
  | username      | string  | 否       | 无     | 代理源认证用户名，私有源可选参数 | private channel username     |
  | password      | string  | 否       | 无     | 代理源认证密码，私有源可选参数   | private channel password     |

#### 6. composite组合仓库配置项

​	各个依赖源的差异化配置通过`settings`进行配置，每项配置的具体含义请参考依赖源文档。