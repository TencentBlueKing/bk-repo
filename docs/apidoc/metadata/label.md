# 元数据标签接口
元数据标签，通过设置元数据标签，可以将节点元数据以标签的形式在前端页面展示。

## 新增元数据标签
- POST /repository/api/metadata/label/{projectId}
- API 名称: create_metadata_label
- 功能说明：
  - 中文：新增元数据标签
  - English：create metadata label
- 请求体
```json
  {
	"labelKey": "scanStatus",
	"labelColorMap": {
		"FAILED": "#FF0000",
		"SUCCESS": "#00EE00"
	},
	"display": true,
	"enumType": true,
	"category": "质量",
	"description": "元数据标签描述"
  }
```
- 响应体
```json
{
  "code": 0,
  "data": null,
  "traceId": null
}
```
- 请求字段说明

| 字段 | 类型 | 是否必需 | 说明 | 示例 | 
| ------ | ------ | ------ | ------ | ------ |
| projecctId | String |是 | 项目id | bkrepo |
| labelKey | String | 是 | 标签对应元数据的key | scanStatus |
| labelColorMap | Map | 是 | 元数据值和标签颜色的对应关系, 颜色为十六进制颜色码 | { "FAILED": "#FF0000", "SUCCESS": "#00EE00" } |
| display | Boolean | 否 | 标签是否在文件列表展示，默认值true | true |
| enumType | Boolean | 否 | 元数据为枚举类型，限制元数据值为枚举值，默认值false | false |
| category | String | 否 | 元数据标签分类 | 质量 | 
| description | String | 否 | 表数据标签描述 | |



## 更新元数据标签
- PUT /repository/api/metadata/label/{projectId}/{labelKey}
- API 名称: update_metadata_label
- 功能说明：
  - 中文：更新元数据标签
  - English：update metadata label
- 请求体
```json
{
	"labelColorMap": {
		"FAILED": "#FF0000",
		"SUCCESS": "#00EE00"
	},
	"display": true,
	"enumType": true,
	"category": "质量",
	"description" : "元数据标签描述"
  }
 ```
 - 响应体
 ```json
{
  "code": 0,
  "data": null,
  "traceId": null
}
```

## 批量保存元数据标签
- POST /repository/api/metadata/label/batch/{projectId}
- API 名称: batch_save_metadata_label
- 功能说明：
  - 中文：批量保存元数据标签
  - English：batch save metadata label
- 请求体
```json
  [{
	"labelKey": "scanStatus",
	"labelColorMap": {
		"FAILED": "#FF0000",
		"SUCCESS": "#00EE00"
	},
	"display": true,
	"enumType": true,
	"category": "质量",
	"description" : "元数据标签描述"
  }]
```
- 响应体
```json
{
  "code": 0,
  "data": null,
  "traceId": null
}
```

## 查询元数据标签列表
- GET /repository/api/metadata/label/{projectId}
- API 名称: list_metadata_label
- 功能说明：
  - 中文：查询项目元数据标签
  - English：list metadata label
- 响应体
```json
{
  "code": 0,
  "data": [
  	 {
	    "labelKey": "scanStatus",
	    "labelColorMap": {
		      "FAILED": "#FF0000",
		      "SUCCESS": "#00EE00"
	    },
	    "display": true,
		"enumType": true,
	    "category": "质量",
		"description" : "元数据标签描述",
	    "createdBy": "admin",
	    "createdDate": "2022-08-22T17:00:00.000",
	    "lastModifiedBy": "admin",
	    "lastModifiedDate": "2022-08-22T17:00:00.000"
	}
  ],
  "traceId": null
}
```

## 查询元数据标签详情
- GET /repository/api/metadata/label/{projectId}/{labelKey}
- API 名称: query_metadata_label_detail
- 功能说明：
  - 中文：查询元数据标签详情
  - English：query metadata label detail
- 响应体
```json
{
  "code": 0,
  "data": {
	    "labelKey": "scanStatus",
	    "labelColorMap": {
		  "FAILED": "#FF0000",
		  "SUCCESS": "#00EE00"
	    },
	    "display": true,
		"enumType": true,
	    "category": "质量",
		"description" : "元数据标签描述",
	    "createdBy": "admin",
	    "createdDate": "2022-08-22T17:00:00.000",
	    "lastModifiedBy": "admin",
	    "lastModifiedDate": "2022-08-22T17:00:00.000"
  },
  "traceId": null
}
```

## 删除元数据标签
- DELETE /repository/api/metadata/label/{projectId}/{labelKey}
- API 名称: delete_metadata_label
- 功能说明：
  - 中文：删除元数据标签
  - English：delete metadata label
- 响应体
```json
{
  "code": 0,
  "data": null,
  "traceId": null
}
```
