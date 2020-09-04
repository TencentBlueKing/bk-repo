package com.tencent.bkrepo.common.api.util

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import java.util.regex.Pattern

/**
 * 前置条件校验工具类
 */
object Preconditions {

    /**
     * 校验[expression]一定为true，[name]为提示字段名称
     */
    fun checkArgument(expression: Boolean?, name: String) {
        if (expression != true) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, name)
        }
    }

    /**
     * 校验[value]不能为空，[name]为提示字段名称
     */
    fun checkNotNull(value: Any?, name: String) {
        if (value == null) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, name)
        }
    }

    /**
     * 校验字符串[value]不能为空，[name]为提示字段名称
     */
    fun checkNotBlank(value: String?, name: String) {
        if (value.isNullOrBlank()) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, name)
        }
    }

    /**
     * 校验[value]符合正则表达式[pattern]，[name]为提示字段名称
     */
    fun matchPattern(value: String?, pattern: String, name: String) {
        if (!Pattern.matches(pattern, value)) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, name)
        }
    }
}
