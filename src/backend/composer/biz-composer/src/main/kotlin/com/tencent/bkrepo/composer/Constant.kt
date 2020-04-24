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
