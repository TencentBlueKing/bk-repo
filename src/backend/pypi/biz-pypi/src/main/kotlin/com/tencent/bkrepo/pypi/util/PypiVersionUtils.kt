package com.tencent.bkrepo.pypi.util

import com.tencent.bkrepo.pypi.pojo.PypiPackagePojo

object PypiVersionUtils {
    fun String.toPypiPackagePojo(): PypiPackagePojo {
        val pathList = this.removePrefix("/").split("/")
        val name = pathList[0]
        val version = pathList[1]
        return PypiPackagePojo(name, version)
    }
}
