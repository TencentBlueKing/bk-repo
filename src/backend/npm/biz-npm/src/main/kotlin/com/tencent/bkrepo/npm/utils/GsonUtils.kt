package com.tencent.bkrepo.npm.utils

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.InputStreamReader

object GsonUtils {
    val gson: Gson =
        GsonBuilder().setPrettyPrinting().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss.sss'Z'").create()

    fun gsonToString(obj: Any): String? {
        return gson.toJson(obj)
    }

    fun stringToArray(gsonString: String): JsonArray {
        return gson.fromJson(gsonString, JsonArray::class.java)
    }

    fun <T> gsonToMaps(gsonString: String): Map<String, T>? {
        return gson.fromJson(gsonString, object : TypeToken<Map<String, T>>() {}.type)
    }

    fun <T> gsonToMaps(gsonString: JsonElement): Map<String, T>? {
        return gson.fromJson(gsonString, object : TypeToken<Map<String, T>>() {}.type)
    }

    fun <T> gsonToList(gsonString: String): List<T> {
        return gson.fromJson(gsonString, object : TypeToken<List<T>>() {}.type)
    }

    fun <T> gsonToBean(gsonString: String, cls: Class<T>): T? {
        return gson.fromJson(gsonString, cls)
    }

    fun <T> mapToGson(map: Map<String, T>): JsonObject {
        return JsonParser().parse(gson.toJson(map)).asJsonObject
    }

    fun transferFileToJson(file: File): JsonObject {
        return gson.fromJson<JsonObject>(
            InputStreamReader(file.inputStream()),
            object : TypeToken<JsonObject>() {}.type
        )
    }
}
