package com.tencent.bkrepo.helm.artifact.util

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tencent.bkrepo.helm.EMPTY_CHART_OR_VERSION
import org.apache.commons.lang.StringUtils
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

internal class YamlUtilTest{

    @Test
    fun appendContent2Yaml() {
        val file = File("/Users/weaving/Downloads/index.yaml")
        val yaml = Yaml()
        val map = mapOf<String, String>()
    }

    @Test
    fun yaml2Json() {
        val file = File("/Users/weaving/Downloads/index-null.yaml")
        val yaml = Yaml()
        var loaded = mutableMapOf<String, Object>()
        try {
            val fis = FileInputStream(file)
            loaded = yaml.load(fis)
        } catch (ioe: IOException) {
        }
        val jsonParser = JsonParser()
        // val result = jsonParser.parse(Gson().toJson(loaded)).asJsonObject
        //     .getAsJsonObject("entries")
        //     .getAsJsonArray("mychart")
        //     .filter{ it.toString().contains("version\":\"0.3.2") }

        val result01 = jsonParser.parse(Gson().toJson(loaded)).asJsonObject
            .getAsJsonObject("entries")
        println(StringUtils.equals(result01.toString(), EMPTY_CHART_OR_VERSION))

        // print(result.toString())
    }

    @Test
    fun test3() {
        val file = File("/Users/weaving/Downloads/index.yaml")
        val yaml = Yaml()
        var loaded = mutableMapOf<String, Object>()
        try {
            val fis = FileInputStream(file)
            loaded = yaml.load(fis)
        } catch (ioe: IOException) {
        }
        val m1 = loaded["entries"] as Map<String, Map<String, Object>>
        val m2 = m1["mychart"] as List<Map<String, Object>>
        for (chart in m2) {
            if (chart["version"].toString() == "0.3.2") {
                print(chart)
            }
        }
    }

    @Test
    fun test2() {
        val file = File("/Users/weaving/Downloads/index.yaml")
        val yaml = Yaml()
        var loaded = mutableMapOf<String, Object>()
        try {
            val fis = FileInputStream(file)
            loaded = yaml.load(fis)
        } catch (ioe: IOException) {
        }
        val m1 = loaded["entries"] as Map<String, Map<String, Object>>
        val m2 = m1["mychart"] as List<Map<String, Object>>
        for (chart in m2) {
            if (chart["version"].toString() == "0.3.2") {
                print(chart)
            }
        }
    }

    @Test
    fun timeUtil() {
        val timeStr = "%s+08:00"
        val currentTime = LocalDateTime.now().toInstant(ZoneOffset.of("+8"))
        val now = LocalDateTime.now(ZoneOffset.UTC)
        val milliseconds = now.atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
        val newNow = LocalDateTime.ofInstant(Instant.ofEpochMilli(milliseconds), ZoneOffset.UTC)
        println(LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));


    }

    @Test
    fun spiltTest() {
        val str1 = "/"
        val str2 = "/test"
        val str3 = "/test/3"
        val list1 = str1.removePrefix("/").split("/").filter {
            it.isNotBlank()
        }
        val list2 = str2.removePrefix("/").split("/").filter {
            it.isNotBlank()
        }
        val list3 = str3.removePrefix("/").split("/").filter {
            it.isNotBlank()
        }
        print(list1)
        println(list1.size)
        print(list2)
        println(list2.size)
        print(list3)
        println(list3.size)
    }


}
