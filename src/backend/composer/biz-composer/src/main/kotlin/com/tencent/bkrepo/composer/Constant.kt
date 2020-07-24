package com.tencent.bkrepo.composer

const val INIT_PACKAGES = "{\n" +
    "    \"packages\": {},\n" +
    "    \"providers-lazy-url\": \"/p/%package%.json\",\n" +
    "    \"search\": \"/search.json?q=%query%&type=%type%\"\n" +
    "\n" +
    "}"
const val COMPOSER_JSON = "composer.json"
const val COMPOSER_VERSION_INIT = "{\n" +
    "    \"packages\":{\n" +
    "        \"%s\":{\n" +
    "        }\n" +
    "    }\n" +
    "}"
const val DELIMITER = "/"
// 对 "/p/%package%.json" 请求的前缀标识
const val PACKAGE_JSON_PREFIX = "/p/"
// 对 "%package%.json" 请求的后缀标识
const val PACKAGE_JSON_SUFFIX = ".json"
// 构件下载前缀标识
const val ARTIFACT_DIRECT_DOWNLOAD_PREFIX = "direct-dists"
