package com.tencent.bkrepo.pypi.util

import com.google.gson.JsonParser

object JsonUtil {
    infix fun String.jsonValue(param: String): String {
        val jsonObject = JsonParser().parse(this).asJsonObject
        return jsonObject.get(param).asString
    }
}
