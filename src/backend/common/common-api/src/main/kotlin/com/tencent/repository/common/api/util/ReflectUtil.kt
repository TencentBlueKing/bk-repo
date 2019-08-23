package com.tencent.repository.common.api.util

object ReflectUtil {

    fun isNativeType(bean: Any): Boolean {
        return bean is Int ||
            bean is Long ||
            bean is Double ||
            bean is Float ||
            bean is Short ||
            bean is Byte
    }
}
