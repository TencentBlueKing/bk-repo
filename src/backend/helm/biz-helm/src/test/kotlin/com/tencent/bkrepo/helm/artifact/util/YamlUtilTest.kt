package com.tencent.bkrepo.helm.artifact.util

import com.google.gson.Gson
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileInputStream
import java.io.IOException

internal class YamlUtilTest{

    fun yaml2Json(file: File): String {
        val yaml = Yaml()
        var loaded = mutableMapOf<String, Object>()
        try {
            val fis = FileInputStream(file)
            loaded = yaml.load(fis)
        } catch (ioe: IOException) {
        }
        return Gson().toJson(loaded)
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


}
