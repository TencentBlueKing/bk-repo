package com.tencent.bkrepo.pypi.util

import com.tencent.bkrepo.pypi.util.pojo.PypiInfo
import java.util.Properties

object PropertiesUtil {
    fun String.propInfo(): PypiInfo {
        val prop = Properties()
        prop.load(this.byteInputStream())
        return PypiInfo(prop.getProperty("Name"), prop.getProperty("Version"), prop.getProperty("Summary"))
    }
}
