package com.tencent.bkrepo.helm.artifact.util

import com.google.gson.Gson
import com.tencent.bkrepo.helm.NOT_FOUND_MES
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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

    fun searchYaml(file: File, name: String, version: String): String {
        val yaml = Yaml()
        var yamlContent = mutableMapOf<String, Object>()
        try {
            val fis = FileInputStream(file)
            yamlContent = yaml.load(fis)
            val entries = yamlContent["entries"] as Map<String, Object>
            try {
                val charts = entries[name] as List<Map<String, Object>>
                try {
                    for (chart in charts) {
                        if (chart["version"].toString() == version) {
                            return chart.toString()
                        }
                    }
                } catch (typeCastException: TypeCastException) {
                    logger.error("$name node is exist, but can not found version : $version")
                }
            } catch (typeCastException: TypeCastException) {
                logger.error("Can not found node named : $name")
            }
        } catch (ioe: IOException) {
            logger.error(ioe.message)
        }
        return NOT_FOUND_MES
    }

    fun searchYaml(file: File, name: String): String {
        val yaml = Yaml()
        var yamlContent = mutableMapOf<String, Object>()
        try {
            val fis = FileInputStream(file)
            yamlContent = yaml.load(fis)
            val entries = yamlContent["entries"] as Map<String, Object>
            try {
                return entries[name].toString()
            } catch (typeCastException: TypeCastException) {
                logger.error("can not found node named : $name")
            }
        } catch (ioe: IOException) {
            logger.error(ioe.message)
        }
        return NOT_FOUND_MES
    }

    private val logger: Logger = LoggerFactory.getLogger(YamlUtil::class.java)
}

