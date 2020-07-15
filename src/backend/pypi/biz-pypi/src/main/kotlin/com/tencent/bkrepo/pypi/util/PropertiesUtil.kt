package com.tencent.bkrepo.pypi.util

import com.tencent.bkrepo.pypi.util.pojo.PypiInfo
import java.util.Properties

object PropertiesUtil {

    // PKG-INFO 文件中属性名
    private const val name = "Name"
    private const val version = "Version"
    private const val summary = "Summary"

    fun String.propInfo(): PypiInfo {
        val prop = Properties()
        prop.load(this.byteInputStream())
        return PypiInfo(prop.getProperty(name), prop.getProperty(version), prop.getProperty(summary))
    }
}
