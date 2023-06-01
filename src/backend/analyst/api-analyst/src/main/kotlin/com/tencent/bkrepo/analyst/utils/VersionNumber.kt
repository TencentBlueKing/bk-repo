package com.tencent.bkrepo.analyst.utils

import java.util.regex.Pattern

/**
 * 组件版本号
 */
class VersionNumber(val version: String) {
    private val versionPattern = Pattern.compile(semverRegex)
    val versionCore: IntArray
    val preRelease: List<String>?
    val build: String?

    init {
        // golang包的版本通常v开头
        var trimVersion = version.trim()
        if (trimVersion.startsWith("v", ignoreCase = true)) {
            trimVersion = trimVersion.substring(1)
        }

        val m = versionPattern.matcher(trimVersion)
        if (!m.matches()) {
            throw UnsupportedVersionException(version)
        }

        // example 0.0.1.2-alpha.1+001
        // group0: 0.0.1.2-alpha.1+001
        // group1: 0.0.1.2
        // group2: alpha.1
        // group3: +001

        val parts = m.group(1).split('.')
        // parse version core
        versionCore = IntArray(parts.size) { parts[it].toInt() }

        // parse pre-release and build
        preRelease = m.group(2)?.split('.')
        @Suppress("MagicNumber")
        build = m.group(3)?.substring(1)
    }

    /**
     * 比较两个版本大小
     *
     * 因为版本间比较时不会比较[build]，但是版本号去重需要考虑[build]，所以不重写equals，
     * 为了保持一致不支持(==,>=,>,<,<=)运算符重载，因此也不实现[Comparable]接口
     *
     * 比较规则参考：https://semver.org/#spec-item-11
     *
     * 1.0.0-alpha < 1.0.0-alpha.1 < 1.0.0-alpha.beta < 1.0.0-beta < 1.0.0-beta.2 < 1.0.0-beta.11 < 1.0.0-rc.1 < 1.0.0
     *
     * @return >0的数表示此版本大于[other]， <0表示此版本小于[other]，否则两个版本相等
     */
    fun compareTo(other: VersionNumber): Int {
        // 比较 version-core
        var size = maxOf(versionCore.size, other.versionCore.size)
        for (i in 0 until size) {
            val coreNumber = versionCore.getOrElse(i) { 0 }
            val otherCoreNumber = other.versionCore.getOrElse(i) { 0 }
            if (coreNumber != otherCoreNumber) {
                return coreNumber - otherCoreNumber
            }
        }

        // version-core相等，比较 pre-release
        return if (preRelease == null && other.preRelease != null) {
            1
        } else if (preRelease != null && other.preRelease == null) {
            -1
        } else if (preRelease == null && other.preRelease == null) {
            0
        } else {
            size = maxOf(preRelease!!.size, other.preRelease!!.size)
            var result = 0
            for (i in 0 until size) {
                result = try {
                    // 数字按大小比较
                    preRelease.getOrElse(i) { "" }.toInt() - other.preRelease.getOrElse(i) { "" }.toInt()
                } catch (e: NumberFormatException) {
                    // 字符串按ascii码大小比较
                    preRelease.getOrElse(i) { "" }.compareTo(other.preRelease.getOrElse(i) { "" }, true)
                }

                if (result != 0) {
                    break
                }
            }

            if (result != 0) {
                result
            } else {
                preRelease.size - other.preRelease.size
            }
        }
    }

    fun lt(other: VersionNumber): Boolean {
        return compareTo(other) < 0
    }

    fun lte(other: VersionNumber): Boolean {
        return compareTo(other) <= 0
    }

    fun eq(other: VersionNumber): Boolean {
        return compareTo(other) == 0
    }

    fun gt(other: VersionNumber): Boolean {
        return compareTo(other) > 0
    }

    fun gte(other: VersionNumber): Boolean {
        return compareTo(other) >= 0
    }

    class UnsupportedVersionException(version: String) : RuntimeException("Unsupported version[$version]")

    companion object {
        // https://semver.org/
        // 在语义化版本的基础上支持了versionCore超过3个，pre-release与build部分支持'_'
        // 支持rpm包
        private const val semverRegex = "^((?:0|[1-9]\\d*)(?:\\.(?:0|[1-9]\\d*))*)(?:-" +
            "((?:0|[1-9]\\d*|\\d*[a-zA-Z-_][0-9a-zA-Z-_]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-_][0-9a-zA-Z_-]*))*))?" +
            "(\\+[0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*)?\$"
    }
}
