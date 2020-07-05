package com.tencent.bkrepo.helm.constants

const val INDEX_YAML = "index.yaml"
const val INDEX_CACHE_YAML = "index-cache.yaml"

const val FULL_PATH = "_full_path"

const val V1 = "v1"
const val CHART_YAML = "Chart.yaml"
const val CHART = "chart"
const val PROV = "prov"
const val NAME = "name"
const val VERSION = "version"
const val URLS = "urls"
const val DIGEST = "digest"
const val CREATED = "created"
const val CHART_PACKAGE_FILE_EXTENSION = "tgz"
const val PROVENANCE_FILE_EXTENSION = "tgz.prov"

// 统一时间格式
const val DATA_TIME_FORMATTER = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"

// 定义返回代码片段
val CHART_NOT_FOUND = mapOf("error" to "chart not found")
val NO_CHART_NAME_FOUND = mapOf("error" to "no chart name found")
