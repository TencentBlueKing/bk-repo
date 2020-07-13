package com.tencent.bkrepo.composer.util

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.lang.Exception

object JsonUtil {

    val mapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule())

    init {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    /**
     * get value with json-param, if string is json-format
     * @param this json 字符串
     * @param param json 属性
     */
    @Throws(Exception::class)
    infix fun String.jsonValue(param: String): String {
        val jsonObject = JsonParser().parse(this).asJsonObject
        return jsonObject.get(param).asString
    }

    /**
     * 在composer包的json加入到%package%.json时添加"dist"属性
     * "dist"属性包含文件压缩格式，download地址
     * @param host 服务器地址
     * @param packageName 包名
     */
    @Throws(Exception::class)
    fun String.wrapperJson(host: String, packageName: String): String {
        val jsonObject = JsonParser().parse(this).asJsonObject
        val versions = jsonObject.get("packages").asJsonObject.get(packageName).asJsonObject
        for (it in versions.entrySet()) {
            val uri = it.value.asJsonObject.get("dist").asJsonObject.get("url").asString
            val downloadUrl = "$host/$uri"
            it.value.asJsonObject.get("dist").asJsonObject.addProperty("url", downloadUrl)
        }
        return GsonBuilder().create().toJson(jsonObject)
    }

    /**
     * 包装packages.json
     * @param host 服务器地址
     */
    @Throws(Exception::class)
    fun String.wrapperPackageJson(host: String): String {
        val jsonObject = JsonParser().parse(this).asJsonObject
        jsonObject.get("search").asString?.let {
            jsonObject.addProperty("search", "$host$it")
        }
        jsonObject.get("providers-lazy-url").asString?.let {
            jsonObject.addProperty("providers-lazy-url", "$host$it")
        }
        return GsonBuilder().create().toJson(jsonObject)
    }

    /**
     *  add new version to %package%.json
     * @param versionJson exists %package%.json
     * @param uploadFileJson new version json content
     * @param name
     * @param version
     */
    @Throws(Exception::class)
    fun addComposerVersion(versionJson: String, uploadFileJson: String, name: String, version: String): JsonObject {
        val jsonObject = JsonParser().parse(versionJson).asJsonObject
        val nameParam = jsonObject.getAsJsonObject("packages").getAsJsonObject(name)
        nameParam.add(version, JsonParser().parse(uploadFileJson))
        return jsonObject
    }
}
