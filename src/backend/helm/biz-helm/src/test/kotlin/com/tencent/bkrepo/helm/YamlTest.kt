package com.tencent.bkrepo.helm

import com.tencent.bkrepo.helm.pojo.ChartEntity
import org.apache.commons.lang.StringUtils
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.yaml.snakeyaml.Yaml
import java.io.StringWriter

class YamlTest {
	val result = mutableMapOf<String, String>()

	private fun getYamlByFileName(): Map<String, String> {
		val inputStream = ClassPathResource("Chart.yaml").inputStream
		val props = Yaml()
		val map = props.load<Map<String, Any>>(inputStream)
		map.entries.forEach { (key, value) ->
			if (value is Map<*, *>) {
				forEachYaml(key, value as Map<String, Any>)
			} else {
				result[key] = value as String
			}
		}
		return result
	}

	/**
	 * 遍历yaml文件，获取map集合
	 */
	private fun forEachYaml(keyStr: String, map: Map<String, Any>) {
		map.entries.forEach { (key, value) ->
			var keyNew = if (StringUtils.isNotBlank(keyStr)) {
				"$keyStr.$key"
			} else {
				key
			}
			if (value is Map<*, *>) {
				forEachYaml(keyNew, value as Map<String, Any>)
			} else {
				result[keyNew] = value as String
			}
		}
	}

	private fun getApplicationName(): String {
		return getYamlByFileName()["type"] ?: error("")
	}


	@Test
	fun test() {
		val yaml = Yaml()
		val inputStream = this.javaClass.classLoader.getResourceAsStream("Chart.yaml")
//		val map = yaml.load<Map<String, Any>>(inputStream)
//		print(map)
		val chartEntity = yaml.loadAs(inputStream, ChartEntity::class.java)
		println(chartEntity.toString())

//		yaml.dumpAs(chartEntity, Tag.MAP,null)
//		val writer = StringWriter()
//		yaml.dump(chartEntity, writer)
//		val dumpAs = yaml.dumpAs(chartEntity, Tag.MAP, DumperOptions.FlowStyle.FLOW)
//		print(writer.toString())
//		println(dumpAs)

		val writer = StringWriter()
		val linkedMapOf = linkedMapOf<String, Any>("name" to "Silenthand Olleander", "race" to "Human", "traits" to arrayOf("ONE_HAND", "ONE_EYE"))
		yaml.dump(linkedMapOf,writer)
		println(writer.toString())
	}
}