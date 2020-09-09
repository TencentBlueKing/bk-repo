#docker_ext.md

-----
### 获取manifest文件

- API: GET /docker/api/manifest/{projectId}/{repoName}/{name}/{tag}
- API 名称: get_repo_manifest
- 功能说明：
	- 中文：获取repo对应的manifest文件
	- English：get manifest by repo and tag

- input body:


``` json

```


- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|projectId|string|是|无|项目id|the project id|
|repoName|string|是|无|仓库名称| name of repo|
|name|string|是|无|docker镜像名| name of docker image|
|tag|string|是|无|repo tag |tag of docker repo|



- output:

```
{
    "code":0,
    "message":null,
    "data":"{
   "schemaVersion": 2,
   "mediaType": "application/vnd.docker.distribution.manifest.v2+json",
   "config": {
      "mediaType": "application/vnd.docker.container.image.v1+json",
      "size": 9404,
      "digest": "sha256:6146596998118de36add541f9e17075a0e40be15cfc84a7fa8efe0bbe5bdc49a"
   },
   "layers": [
      {
         "mediaType": "application/vnd.docker.image.rootfs.diff.tar.gzip",
         "size": 50379708,
         "digest": "sha256:16ea0e8c887910fe167687a0169991b4c1fc165257aab6b116f6a5e61a64e7af"
      },
      {
         "mediaType": "application/vnd.docker.image.rootfs.diff.tar.gzip",
         "size": 7811508,
         "digest": "sha256:50024b0106d53dcbd29889c65bc040439b2bb8947dac16c8c670db894a2c5ba6"
      },
      {
         "mediaType": "application/vnd.docker.image.rootfs.diff.tar.gzip",
         "size": 9996013,
         "digest": "sha256:ff95660c69375e19e287b2ea87ca9b4be008cd036e95d541515262b86cc521d9"
      },
      {
         "mediaType": "application/vnd.docker.image.rootfs.diff.tar.gzip",
         "size": 51786970,
         "digest": "sha256:9c7d0e5c0bc204b3a36e3f8ff320741da0bd0225e0a67e224c6265c1e208f80a"
      },
      {
         "mediaType": "application/vnd.docker.image.rootfs.diff.tar.gzip",
         "size": 192044870,
         "digest": "sha256:29c4fb388fdfef16e8278fba2b06d46e48d152e1b40f4347c8828a04c8e2a87e"
      },
      {
         "mediaType": "application/vnd.docker.image.rootfs.diff.tar.gzip",
         "size": 11568,
         "digest": "sha256:f87845d1b3b4e4ead42cddc30dcdcf5cf06555785f8299d304cc118126bc0dd8"
      },
      {
         "mediaType": "application/vnd.docker.image.rootfs.diff.tar.gzip",
         "size": 98930042,
         "digest": "sha256:fd6d3a50a72f31eec9fe21bc3c65888df636dc32e6cb6c44b698d73258cef077"
      },
      {
         "mediaType": "application/vnd.docker.image.rootfs.diff.tar.gzip",
         "size": 11559,
         "digest": "sha256:dcfb948bad0edeb2d030e8dc230224877ffd7f5d4a93ea43d940aaceb6d95d91"
      },
      {
         "mediaType": "application/vnd.docker.image.rootfs.diff.tar.gzip",
         "size": 1943,
         "digest": "sha256:f018ac72b8e07233e71dfd9cd1d9447c5fc034d2d8f272d7324e7b276912f8db"
      }
   ]
}",
    "traceId":""
}

```

- output 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误 |0:success, other: failure|
|message|result message|错误消息 |the failure message |
|data | bool |the manifest data |the data of manifest|
|traceId|string|请求跟踪id|the trace id|


### 根据layerId下载layer文件

- API: GET /docker/api/layer/{projectId}/{repoName}/{name}/{Id}
- API 名称: 
- 功能说明：
	- 中文：根据layerId获取layer文件
	- English：download layer file by layerId

- input body:

``` json

```

- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|projectId|string|是|无|项目id|the project id|
|repoName|string|是|无|仓库名称| name of repo|
|name|string|是|无|docker镜像名| name of docker image|
|Id|string|是|无|layer id|the  id of layer|


- output:

```
文件流

```

- output 字段说明

### 获取指定projectId和repoName下的所有镜像

- API: GET /docker/api/repo/{projectId}/{repoName}?pageNumber=0&pageSize=10
- API 名称: get_image_by_project_repository
- 功能说明：
	- 中文：获取指定project和仓库下的所有docker镜像
	- English：get image by project and repository

- input body:


``` json

```


- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|projectId|string|是|无|项目id|the project id|
|repoName|string|是|无|仓库名称| name of repo|
|pageNumber|Int|是|无|页码数| number of page|
|pageSize|Int|是|无|每页大小| limit of page|



- output:

```
{
    "code":0,
    "message":null,
    "traceId":"",
    "data":[
        {
            "name":"mongo",
            "createdBy":"yangjian",
            "downloadCount":10,
            "createdDate":"2020-08-28 04:07:12.672Z"
        },
        {
            "name":"nginx",
            "createdBy":"kim",
            "downloadCount":12,
            "createdDate":"2020-08-28 04:07:12.672Z"
        }
    ]
}

```

- output 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误 |0:success, other: failure|
|message|result message|错误消息 |the failure message |
|data | string array |image 名称列表 |the list of image|
|traceId|string|请求跟踪id|the trace id|
|name|string|镜像ID|the id of image|
|createdBy|string|创建人|the creator of image|
|createdDate|time|创建时间|create date of image|

### 获取repo的所有tag

- API: GET /docker/api/tag/{projectId}/{repoName}/{name}
- API 名称: get_repo_tag_list
- 功能说明：
	- 中文：获取repo对应的manifest文件
	- English：get image tag by repo 

- input body:


``` json

```


- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|projectId|string|是|无|项目id|the project id|
|repoName|string|是|无|仓库名称| name of repo|
|name|string|是|无|docker 镜像名称| name of docker image|



- output:

```
{
    "code":0,
    "message":null,
    "data":[
        {
            "tag":"latest",
            "status":"@prerelease",
            "size":1024,
            "downloadCount":10,
            "lastModifiedBy":"owen",
            "lastModifiedDate":"2020-08-28 04:07:12.672Z"
        },
        {
            "tag":"v1",
            "status":"@prerelease",
            "size":2096,
            "downloadCount":10,
            "lastModifiedBy":"owen",
            "lastModifiedDate":"2020-08-28 04:07:12.672Z"
        }
    ],
    "traceId":""
}
```

- output 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误 |0:success, other: failure|
|message|result message|错误消息 |the failure message |
|data | string array | image的tag列表 |tag list of the image|
|traceId|string|请求跟踪id|the trace id|
|tag|string|tag名称|the name of tag|
|status|string|制品状态|the status of image|
|size|Int|镜像大小|the size of image|
|downloadCount|Int|下载次数|the download count of image|
|lastModifiedBy|date|更新人|the man upload it|
|lastModifiedDate|date|更新时间|the modified date|


### 删除指定projectId和repoName下的的镜像

- API: DELETE /docker/api/repo/{projectId}/{repoName}/{name}
- API 名称: delete_image_by_project_repository_name
- 功能说明：
	- 中文：删除指定project和仓库下name的镜像
	- English：delete image by project and repository and image name

- input body:


``` json

```


- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|projectId|string|是|无|项目id|the project id|
|repoName|string|是|无|仓库名称| name of repo|
|name|string|是|无|镜像名称| name of image|



- output:

```
{
    "code":0,
    "message":null,
    "traceId":"",
    "data":[
        {
            "name":"mongo",
            "createdBy":"yangjian",
            "downloadCount":10,
            "createdDate":"2020-08-28 04:07:12.672Z"
        },
        {
            "name":"nginx",
            "createdBy":"kim",
            "downloadCount":12,
            "createdDate":"2020-08-28 04:07:12.672Z"
        }
    ]
}

```

- output 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误 |0:success, other: failure|
|message|result message|错误消息 |the failure message |
|data | string array |image 名称列表 |the list of image|
|traceId|string|请求跟踪id|the trace id|

### 获取repo下指定tag的详情

- API: GET /docker/api/tag/detail/{projectId}/{repoName}/{name}
- API 名称: get_repo_tag_list
- 功能说明：
	- 中文：获取repo对应的manifest文件
	- English：get image tag by repo 

- input body:


``` json

```


- input 字段说明

|字段|类型|是否必须|默认值|说明|Description|
|---|---|---|---|---|---|
|projectId|string|是|无|项目id|the project id|
|repoName|string|是|无|仓库名称| name of repo|
|name|string|是|无|docker 镜像名称| name of docker image|



- output:

```
{
    "code":0,
    "message":null,
    "data":{
        "basic":{
            "size":1024,
            "sha256":"c7fea26579a7de970085f5f0d7f5fe28d055e642f7520a674614f9b428bdba05",
            "tag":"v1",
            "os":"linux",
            "lastModifiedBy":"owen",
            "lastModifiedDate":"2020-08-28 04:07:12.672Z",
            "downloadCount":1223
        },
        "history":[
            "DOCKER RUN"
        ],
        "metadata":{
            "user":"owen"
        },
        "layers":[
            {
                "id":"sha256__8dc0e42f1f5b6325f3998409ab78543dfce3cb36aff37e64b432ee0c7accd1dc",
                "size":123
            },
            {
                "id":"sha256__8dc0e42f1f5b6325f3998409ab78543dfce3cb36aff37e64b432ee0c7accd1dc",
                "size":1234
            }
        ]
    },
    "traceId":""
}
```

- output 字段说明

| 字段|类型|说明|Description|
|---|---|---|---|
|code|bool|错误编码。 0表示success，>0表示失败错误 |0:success, other: failure|
|message|result message|错误消息 |the failure message |
|data | object | tag详情数据 |tag list of the image|
|traceId|string|请求跟踪id|the trace id|
|basic|object|基础数据|the basic data|
|history|object array|镜像构建历史|the history of build|
|metadata|object|元数据信息|the metadata of image tag|
|layers|object array|层级信息|the layer info of image|


### 鉴权示例



