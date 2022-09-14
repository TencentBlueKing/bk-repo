package com.tencent.bkrepo.common.api.util

object ServiceCommonUtils {

    private val SERVER_NAME_REGEX = Regex("""com\.tencent\.bkrepo\.(\w+)\..*""")

    fun <T> getServiceName(klass: Class<T>): String? {
        val declaringClassName = klass.name
        return SERVER_NAME_REGEX.find(declaringClassName)?.groupValues?.get(1)
    }
}
