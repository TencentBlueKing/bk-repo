package com.tencent.bkrepo.helm.utils

import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.nodes.Tag
import java.io.File

object YamlUtils {
	val yaml = Yaml()

	@Throws(Exception::class)
	inline fun <reified T> getObject(file: File): T {
		return yaml.loadAs(file.inputStream(), T::class.java)
	}

	fun <T> transEntity2File(v: T): String {
		return yaml.dumpAs(v, Tag.MAP, null)
	}
}