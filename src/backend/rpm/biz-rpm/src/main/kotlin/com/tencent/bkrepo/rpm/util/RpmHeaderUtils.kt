package com.tencent.bkrepo.rpm.util

import com.tencent.bkrepo.common.service.util.HeaderUtils

object RpmHeaderUtils {

    /**
     * 默认为true
     */
    fun HeaderUtils.getRpmBooleanHeader(name: String): Boolean {
        return getHeader(name)?.toBoolean() ?: true
    }

    fun HeaderUtils.calculatePackages(): Boolean {
        return getBooleanHeader("X-BKREPO-RPM-PACKAGES")
    }
}
