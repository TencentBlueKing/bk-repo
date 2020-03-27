package com.tencent.bkrepo.helm.pojo

data class ChartEntity(
		var apiVersion: String? = null,
		var name: String? = null,
		var description: String? = null,
		var type: String? = null,
		var version: String? = null,
		var appVersion: String? = null
)