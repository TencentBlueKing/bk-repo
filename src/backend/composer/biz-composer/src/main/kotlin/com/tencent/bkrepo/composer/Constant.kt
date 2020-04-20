package com.tencent.bkrepo.composer

const val JSON_FILE = "packages.json"
const val FAKE_UUID = "670e8156-af17-4f1a-b5fe-28d7b6f46c2a"
const val INIT_PACKAGES = "{\n" +
        "    \"packages\": [],\n" +
        "    \"providers-url\": \"/p/%package%.json\",\n" +
        "    \"metadata-url\": \"/p2/%package%.json\",\n" +
        "    \"search\": \"/search.json?q=%query%&type=%type%\",\n" +
        "\n" +
        "}"
const val COMPOSER_JSON = "composer.json"
const val COMPOSER_VERSION_INIT = "{\n" +
        "    \"packages\":{\n" +
        "        \"%s\":{\n" +
        "        }\n" +
        "    }\n" +
        "}"