package com.tencent.bkrepo.analyst.utils

interface VersionRange {
    /**
     * 判断[versionNumber]是否在当前版本范围内
     */
    fun contains(versionNumber: VersionNumber): Boolean

    class UnsupportedVersionRangeException(range: String): RuntimeException("Unsupported version range[$range]")
}
