package com.tencent.bkrepo.job.batch.utils

object ValueConvertUtils {
    fun toLongOrNull(value: Any?): Long? {
        return when (value) {
            null -> null
            is Long -> value
            is Int -> value.toLong()
            is Number -> value.toLong()
            else -> value.toString().toLongOrNull()
        }
    }

    fun toLong(value: Any?, fieldName: String = "value"): Long {
        return toLongOrNull(value)
            ?: throw IllegalArgumentException("Unable to convert $fieldName[$value] to Long.")
    }
}
