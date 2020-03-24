package com.tencent.bkrepo.helm.artifact.util

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLFactoryBuilder
import com.google.gson.Gson
import com.tencent.bkrepo.helm.pojo.ChartInfo
import com.tencent.bkrepo.helm.pojo.ChartInfoList
import jdk.internal.org.objectweb.asm.TypeReference
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileInputStream
import java.io.FileReader
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

    fun yaml2Object(file: File): ChartInfoList? {
        val str =
            "apiVersion: v1\n" +
            "entries:\n" +
            "  mychart:\n" +
            "  - apiVersion: v1\n" +
            "    created: \"2020-03-24T11:51:40.661210307+08:00\"\n" +
            "    digest: 724874130fbef995f6b707aa43fe66aa85a2ebb3e2f744f2a89f4f209e80561b\n" +
            "    name: mychart\n" +
            "    urls:\n" +
            "    - charts/mychart-0.3.2.tgz\n" +
            "    version: 0.3.2\n" +
            "  - apiVersion: v1\n" +
            "    created: \"2020-03-24T15:32:50.841894035+08:00\"\n" +
            "    digest: 86d76bb0f229bf397504ea923ce280922e6b7c9dfdbdcfd2fc65be9698ad83c7\n" +
            "    name: mychart\n" +
            "    urls:\n" +
            "    - charts/mychart-0.0.1.tgz\n" +
            "    version: 0.0.1\n" +
            "  test:\n" +
            "  - apiVersion: v1\n" +
            "    created: \"2020-03-24T15:57:04.939788207+08:00\"\n" +
            "    digest: b2958252687766aa189d55bb05fae9ed2a76bc804dc782a34532ccc123b68ad1\n" +
            "    name: test\n" +
            "    urls:\n" +
            "    - charts/test-0.0.1.tgz\n" +
            "    version: 0.0.1\n" +
            "generated: \"2020-03-24T15:57:23+08:00\"\n" +
            "serverInfo: {}\n"
        val mapper: ObjectMapper = ObjectMapper(YAMLFactory())
        val ss= "ddd"
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        val content = mapper.readValue(file, ChartInfoList::class.java)
        return content

    }

    @Test
    fun test() {
        val file = File("/Users/weaving/Downloads/index.yaml")
        // print(yaml2Json(file))
        val result = yaml2Object(file)
        print(result?.apiVersion)
        print(result?.map)
        print(result?.generated)
        print(result?.serverInfo)
    }

}
