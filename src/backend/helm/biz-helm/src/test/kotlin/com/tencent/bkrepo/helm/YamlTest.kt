package com.tencent.bkrepo.helm

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.tencent.bkrepo.helm.pojo.IndexEntity
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.TypeDescription
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import org.yaml.snakeyaml.introspector.BeanAccess
import org.yaml.snakeyaml.introspector.PropertyUtils
import org.yaml.snakeyaml.nodes.Tag
import org.yaml.snakeyaml.representer.Representer
import org.yaml.snakeyaml.resolver.Resolver
import java.util.LinkedHashMap

class YamlTest {
    fun getYaml(): Yaml {
        val dumperOptions = DumperOptions()
        dumperOptions.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        dumperOptions.defaultScalarStyle = DumperOptions.ScalarStyle.PLAIN
        dumperOptions.isPrettyFlow = false

        val representer = Representer()
        representer.addClassTag(IndexEntity::class.java, Tag.MAP)
        representer.addClassTag(Chart::class.java, Tag.MAP)

        val constructor = Constructor(Index::class.java)
        val indexTypeDescription = TypeDescription(Index::class.java)
        indexTypeDescription.addPropertyParameters("entries", LinkedHashMap::class.java, Chart::class.java)
        // constructor.addTypeDescription(TypeDescription(Chart::class.java))
        // val indexTypeDescription = constructor.addTypeDescription(TypeDescription(Index::class.java))
        // indexTypeDescription.addPropertyParameters("entries",Chart::class.java)

        val propertyUtils = PropertyUtils()
        propertyUtils.setBeanAccess(BeanAccess.FIELD)
        propertyUtils.isSkipMissingProperties = true
        propertyUtils.isAllowReadOnlyProperties = true
        constructor.propertyUtils = propertyUtils

        val resolver = Resolver()



        return Yaml(constructor, representer, dumperOptions, resolver)
    }

    @Test
    fun test() {
        val yaml = getYaml()
        val inputStream = this.javaClass.classLoader.getResourceAsStream("Index.yaml")
        val map = yaml.loadAs(inputStream, Map::class.java)
        val gson: Gson =
            GsonBuilder().setPrettyPrinting().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss.sss'Z'").create()
        val asJsonObject = JsonParser().parse(gson.toJson(map)).asJsonObject
        val fromJson = gson.fromJson(asJsonObject, Index::class.java)
        println(map)
        System.err.println(asJsonObject)
        System.err.println(fromJson)
    }
}