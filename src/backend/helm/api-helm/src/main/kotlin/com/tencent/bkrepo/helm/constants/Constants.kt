package com.tencent.bkrepo.helm.constants

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

const val INDEX_YAML = "index.yaml"
const val INDEX_CACHE_YAML = "index-cache.yaml"

val INIT_MAP = mapOf("apiVersion" to "v1", "entries" to "{}", "generated" to LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME), "serverInfo" to "{}")

const val INIT_STR="apiVersion: v1\nentries: {}\ngenerated: \"%s\"\nserverInfo: {}"

const val FULL_PATH = "full_path"

//upload success map
val UPLOAD_SUCCESS_MAP = mapOf("saved" to true)
