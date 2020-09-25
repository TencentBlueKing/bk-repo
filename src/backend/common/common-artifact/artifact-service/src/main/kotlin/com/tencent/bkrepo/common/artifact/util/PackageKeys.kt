package com.tencent.bkrepo.common.artifact.util

import com.tencent.bkrepo.common.api.constant.StringPool

/**
 * 包唯一id工具类
 */
object PackageKeys {

    fun ofGav(groupId: String, artifactId: String): String {
        return StringBuilder("gav://").append(groupId)
            .append(StringPool.COLON)
            .append(artifactId)
            .toString()
    }

    fun ofName(schema: String, name: String): String {
        return StringBuilder(schema).append("://").append(name).toString()
    }

}