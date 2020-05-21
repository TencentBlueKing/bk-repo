package com.tencent.bkrepo.composer.util

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.lang.Exception

object JsonUtil {

    /**
     * get value with json-param, if string is json-format
     * @param param
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
        val searchObject = jsonObject.get("search").asString?.let {
            jsonObject.addProperty("search", "$host$it")
        }
        val providersUrlObject = jsonObject.get("providers-lazy-url").asString?.let {
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
