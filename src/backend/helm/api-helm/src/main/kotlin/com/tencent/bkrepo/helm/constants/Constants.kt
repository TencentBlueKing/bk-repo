package com.tencent.bkrepo.helm.constants

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

const val INDEX_YAML = "index.yaml"
const val INDEX_CACHE_YAML = "index-cache.yaml"

val INIT_MAP = mapOf("apiVersion" to "v1", "entries" to "{}", "generated" to LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME), "serverInfo" to "{}")

const val INIT_STR="apiVersion: v1\nentries: {}\ngenerated: \"%s\"\nserverInfo: {}"

const val TEST_MES = "apiVersion: v1\n" +
    "entries:\n" +
    "  mychart:\n" +
    "  - apiVersion: v1\n" +
    "    created: \"2020-03-24T11:51:40.661210307+08:00\"\n" +
    "    digest: 724874130fbef995f6b707aa43fe66aa85a2ebb3e2f744f2a89f4f209e80561b\n" +
    "    name: mychart\n" +
    "    urls:\n" +
    "    - charts/mychart-0.3.2.tgz\n" +
    "    version: 0.3.2\n" +
    "  - apiVersion: v1\n" +
    "    created: \"2020-03-24T15:32:50.841894035+08:00\"\n" +
    "    digest: 86d76bb0f229bf397504ea923ce280922e6b7c9dfdbdcfd2fc65be9698ad83c7\n" +
    "    name: mychart\n" +
    "    urls:\n" +
    "    - charts/mychart-0.0.1.tgz\n" +
    "    version: 0.0.1\n" +
    "  test:\n" +
    "  - apiVersion: v1\n" +
    "    created: \"2020-03-24T15:57:04.939788207+08:00\"\n" +
    "    digest: b2958252687766aa189d55bb05fae9ed2a76bc804dc782a34532ccc123b68ad1\n" +
    "    name: test\n" +
    "    urls:\n" +
    "    - charts/test-0.0.1.tgz\n" +
    "    version: 0.0.1\n" +
    "generated: \"2020-03-24T15:57:23+08:00\"\n" +
    "serverInfo: {}\n"

const val FULL_PATH = "full_path"

//upload success map
val UPLOAD_SUCCESS_MAP = mapOf("saved" to true)
