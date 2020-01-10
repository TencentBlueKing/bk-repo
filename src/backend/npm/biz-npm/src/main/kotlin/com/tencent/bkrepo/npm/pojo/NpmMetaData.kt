package com.tencent.bkrepo.npm.pojo

import com.google.gson.JsonArray

data class NpmMetaData(
    val license: String,
    val description: String,
    val keywords: JsonArray,
    val name: String,
    val version: String,
    val deprecated: String?
)
