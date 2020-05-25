package com.tencent.bkrepo.pypi.util

import java.util.Properties

object PropertiesUtil {
    fun String.propInfo(): Map<String, String> {
        val prop = Properties()
        prop.load(this.byteInputStream())
        return mapOf("name" to prop.getProperty("Name"),
                "version" to prop.getProperty("Version"),
                "summary" to prop.getProperty("Summary"))
    }
}
