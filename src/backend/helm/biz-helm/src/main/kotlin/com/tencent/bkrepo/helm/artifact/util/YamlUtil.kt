package com.tencent.bkrepo.helm.artifact.util

import com.google.gson.Gson
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileInputStream
import java.io.IOException

object YamlUtil {
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
}

