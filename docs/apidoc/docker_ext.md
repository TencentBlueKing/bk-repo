#docker_ext.md

-----
### 获取manifest文件

- API: GET /api/docker/user/manifest/{projectId}/{repoName}/{repo}/{tag}
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
|repo|string|是|无|docker镜像名| name of docker image|
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

- API: GET /api/docker/user/layer/{projectId}/{repoName}/{repo}/{Id}
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
|repo|string|是|无|docker镜像名| name of docker image|
|Id|string|是|无|layer id|the  id of layer|


- output:

```
文件流

```

- output 字段说明

### 获取指定projectId和repoName下的所有repo

- API: GET /api/docker/user/repo/{projectId}/{repoName}
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



- output:

```
{
    "code":0,
    "message":null,
    "traceId":"",
    "data":[
        "nginx",
        "tomcat",
        "linux"
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

### 获取repo的所有tag

- API: GET /api/docker/user/tag/{projectId}/{repoName}/{repo}
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
|repo|string|是|无|docker 镜像名称| name of docker image|



- output:

```
{
    "code":0,
    "message":null,
    "data":{
        "v1":"owen"
    },
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

### 鉴权示例

```bash
curl -X GET http://dev.bkrepo.oa.com/docker/user/repo/bk-extension/docker-local/  -H 'Authorization: Platform Y2I0NzVmMGItMGIyZS00YjliLTlhYjItOTc3Mzg1ZDM3ZTQ1OjZ1UzE5Yjg3WUlVNnZXTkhpTW9lQ2xhNngzdHN4OA==' -H 'X-BKREPO-UID: owenlxu'
```
Y2I0NzVmMGItMGIyZS00YjliLTlhYjItOTc3Mzg1ZDM3ZTQ1OjZ1UzE5Yjg3WUlVNnZXTkhpTW9lQ2xhNngzdHN4OA== 为 $AK:$SK base64编码之后的值

