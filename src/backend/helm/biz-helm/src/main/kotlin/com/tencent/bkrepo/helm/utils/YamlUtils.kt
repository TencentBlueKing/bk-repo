package com.tencent.bkrepo.helm.utils

import com.tencent.bkrepo.helm.pojo.ChartEntity
import com.tencent.bkrepo.helm.pojo.IndexEntity
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.nodes.Tag
import org.yaml.snakeyaml.representer.Representer
import java.io.File

object YamlUtils {

    fun getYaml(): Yaml {
        val dumperOptions = DumperOptions()
        dumperOptions.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        dumperOptions.defaultScalarStyle = DumperOptions.ScalarStyle.PLAIN
        dumperOptions.isPrettyFlow = false

        val representer = Representer()
        representer.addClassTag(IndexEntity::class.java, Tag.MAP)
        representer.addClassTag(ChartEntity::class.java, Tag.MAP)
        return Yaml(representer, dumperOptions)
    }

    @Throws(Exception::class)
    inline fun <reified T> getObject(file: File): T {
        return getYaml().loadAs(file.inputStream(), T::class.java)
    }

    fun <T> transEntity2File(v: T): String {
        return getYaml().dump(v)
    }
}
