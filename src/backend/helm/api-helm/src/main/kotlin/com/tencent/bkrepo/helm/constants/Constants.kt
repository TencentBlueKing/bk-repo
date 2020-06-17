package com.tencent.bkrepo.helm.constants

const val INDEX_YAML = "index.yaml"
const val INDEX_CACHE_YAML = "index-cache.yaml"

const val INIT_STR = "apiVersion: v1\nentries: {}\ngenerated: \"%s\"\nserverInfo: {}\n"

const val FULL_PATH = "_full_path"

const val CHART_YAML = "Chart.yaml"
const val CHART = "chart"
const val PROV = "prov"
const val NAME = "name"
const val VERSION = "version"
const val CHART_PACKAGE_FILE_EXTENSION = "tgz"
const val PROVENANCE_FILE_EXTENSION = "tgz.prov"

// 统一时间格式
const val DATA_TIME_FORMATTER = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"

// 定义返回代码片段
const val CHART_NOT_FOUND = "{\n    \"error\": \"chart not found\"\n}"
const val NO_CHART_NAME_FOUND = "{\n    \"error\": \"no chart name found\"\n}"
const val CHART_VERSION_NOT_FOUND = "{\n    \"error\": \"no chart version found for %s-%s\"\n}"
const val EMPTY_CHART_OR_VERSION = "{}"
const val ERROR_NOT_FOUND = "{\n    \"error\": \"not found\"\n}"
const val EMPTY_NAME_OR_VERSION = "[]"
const val ENTRIES = "entries"
