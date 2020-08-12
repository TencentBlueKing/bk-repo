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
    private const val packages = "packages"
    private const val dist = "dist"
    private const val url = "url"
    private const val downloadRedirectUrl = "providers-lazy-url"
    private const val search = "search"

    init {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    /**
     * get value with json-param, if string is json-format
     * @param param json 属性
     */
    @Throws(Exception::class)
    infix fun String.jsonValue(param: String): String {
        val jsonObject = JsonParser.parseString(this).asJsonObject
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
        val jsonObject = JsonParser.parseString(this).asJsonObject
        val versions = jsonObject.get(packages).asJsonObject.get(packageName).asJsonObject
        for (it in versions.entrySet()) {
            val uri = it.value.asJsonObject.get(dist).asJsonObject.get(url).asString
            val downloadUrl = "$host/$uri"
            it.value.asJsonObject.get(dist).asJsonObject.addProperty(url, downloadUrl)
        }
        return GsonBuilder().create().toJson(jsonObject)
    }

    /**
     * 包装packages.json
     * @param host 服务器地址
     */
    @Throws(Exception::class)
    fun String.wrapperPackageJson(host: String): String {
        val jsonObject = JsonParser.parseString(this).asJsonObject
        jsonObject.get(search).asString?.let {
            jsonObject.addProperty(search, "$host$it")
        }
        jsonObject.get(downloadRedirectUrl).asString?.let {
            jsonObject.addProperty(downloadRedirectUrl, "$host$it")
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
        val jsonObject = JsonParser.parseString(versionJson).asJsonObject
        val nameParam = jsonObject.getAsJsonObject(packages).getAsJsonObject(name)
        // 覆盖重复版本信息
        nameParam.add(version, JsonParser.parseString(uploadFileJson))
        return jsonObject
    }
}
