package com.tencent.bkrepo.helm.utils

import com.google.gson.Gson
import com.tencent.bkrepo.helm.pojo.IndexEntity
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import org.yaml.snakeyaml.introspector.BeanAccess
import org.yaml.snakeyaml.introspector.PropertyUtils
import org.yaml.snakeyaml.nodes.Tag
import org.yaml.snakeyaml.representer.Representer
import java.io.File
import java.io.FileInputStream
import java.io.IOException

object YamlUtils {

    fun getYaml(): Yaml {
        val dumperOptions = DumperOptions()
        dumperOptions.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        dumperOptions.defaultScalarStyle = DumperOptions.ScalarStyle.PLAIN
        dumperOptions.isPrettyFlow = false

        val representer = Representer()
        representer.addClassTag(IndexEntity::class.java, Tag.MAP)

        val constructor = Constructor()
        val propertyUtils = PropertyUtils()
        propertyUtils.setBeanAccess(BeanAccess.FIELD)
        propertyUtils.isSkipMissingProperties = true
        propertyUtils.isAllowReadOnlyProperties = true
        constructor.propertyUtils = propertyUtils

        return Yaml(constructor, representer, dumperOptions)
    }

    @Throws(Exception::class)
    inline fun <reified T> convertFileToEntity(file: File): T {
        return getYaml().loadAs(file.inputStream(), T::class.java)
    }

    @Throws(Exception::class)
    inline fun <reified T> convertStringToEntity(yaml: String): T {
        return getYaml().loadAs(yaml, T::class.java)
    }

    fun <T> transEntity2File(v: T): String {
        return getYaml().dump(v)
    }

    fun yaml2Json(file: File): String {
        var loaded = mutableMapOf<String, Any>()
        try {
            val fis = FileInputStream(file)
            loaded = getYaml().load(fis)
        } catch (ioe: IOException) {
        }
        return Gson().toJson(loaded)
    }
}
