package com.tencent.bkrepo.helm


data class ChartEntity(
		var apiVersion: String? = null,
		var name: String? = null,
		var description: String? = null,
		var type: String? = null,
		var version: String? = null,
		var appVersion: String? = null
) {
//	private val apiVersion: String = ""
//	private val name: String = ""
//	private val description: String  = ""
//	private val type: String = ""
//	private val version: String = ""
//	private val appVersion: String = ""

	override fun toString(): String {
		return "ChartEntity(apiVersion='$apiVersion', name='$name', description='$description', type='$type', version='$version', appVersion='$appVersion')"
	}
}