package com.tencent.bkrepo.helm.constants

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

const val INDEX_YAML = "index.yaml"
const val INDEX_CACHE_YAML = "index-cache.yaml"

val INIT_MAP = mapOf("apiVersion" to "v1", "entries" to "{}", "generated" to LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME), "serverInfo" to "{}")

const val INIT_STR = "apiVersion: v1\nentries: {}\ngenerated: \"%s\"\nserverInfo: {}\n"

const val FULL_PATH = "_full_path"

// upload success map
val UPLOAD_SUCCESS_MAP = mapOf("saved" to true)
val UPLOAD_ERROR_MAP = mapOf("saved" to false)

// remove success map
val DELETE_SUCCESS_MAP = mapOf("deleted" to true)

// 定义返回代码片段
const val CHART_NOT_FOUND = "{\n    \"error\": \"chart not found\"\n}"
const val NO_CHART_NAME_FOUND = "{\n    \"error\": \"no chart name found\"\n}"
const val CHART_VERSION_NOT_FOUND = "{\n    \"error\": \"no chart version found for %s-%s\"\n}"
const val EMPTY_CHART_OR_VERSION = "{}"
const val ERROR_NOT_FOUND = "{\n    \"error\": \"not found\"\n}"
const val EMPTY_NAME_OR_VERSION = "[]"
const val ENTRIES = "entries"
