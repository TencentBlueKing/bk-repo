package com.tencent.bkrepo.analyst.utils

/**
 * 版本区间组合
 */
class CompositeVersionRange(
    private val ranges: MutableList<VersionRange> = ArrayList(),
    val operator: Operator = Operator.OR
) : VersionRange {
    fun add(range: VersionRange) {
        ranges.add(range)
    }

    fun ranges(): List<VersionRange> {
        return ranges
    }

    /**
     * 只要在[ranges]中的r任意区间就符合要求
     */
    override fun contains(versionNumber: VersionNumber): Boolean {
        if (ranges.isEmpty()) {
            return false
        }

        return if (operator == Operator.AND) {
            ranges.all { it.contains(versionNumber) }
        } else {
            ranges.any { it.contains(versionNumber) }
        }
    }

    enum class Operator(val op: String) {
        AND("&&"), OR("||")
    }

    companion object {
        /**
         * 根据版本号范围构造[CompositeVersionRange]
         *
         * @param ranges 版本范围，示例 <=1.2.3;>=2.3.0,<2.5.1
         *
         * @return 版本范围组合
         */
        fun build(ranges: String): CompositeVersionRange {
            val orRanges = CompositeVersionRange()
            ranges.split(';').forEach { nestedRanges ->
                val andRanges = CompositeVersionRange(operator = Operator.AND)
                nestedRanges.split(',').forEach {
                    andRanges.add(DefaultVersionRange.build(it))
                }
                if (andRanges.ranges.isEmpty()) {
                    throw VersionRange.UnsupportedVersionRangeException(ranges)
                }
                orRanges.add(andRanges)
            }
            if (orRanges.ranges.isEmpty()) {
                throw VersionRange.UnsupportedVersionRangeException(ranges)
            }
            return orRanges
        }
    }
}
