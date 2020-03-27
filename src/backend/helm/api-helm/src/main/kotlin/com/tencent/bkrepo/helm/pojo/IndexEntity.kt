package com.tencent.bkrepo.helm.pojo

data class IndexEntity (
	var apiVersion: String? = null,
	var entries: Map<String,ChartEntity>? = null,
	var generated: String? = null,
	var serverInfo: Map<String,Any>? = null
)