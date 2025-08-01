package com.tencent.bkrepo.common.api.util

import org.springframework.scheduling.support.CronExpression
import org.springframework.util.StringUtils

object CronUtils {
    fun isValidExpression(expression: String): Boolean {
        val cronSplits = StringUtils.tokenizeToStringArray(expression, " ");
        var valid = false
        if (cronSplits.size == 6) {
            // 0 0 2 1 * ?
            valid = CronExpression.isValidExpression(expression)
        } else if (cronSplits.size == 7) {
            // 0 15 10 ? * 6L 2025-2030
            valid = CronExpression.isValidExpression(expression.substringBeforeLast(" "))
            try {
                checkValue(cronSplits[6], MIN_YEAR, MAX_YEAR)
            } catch (e: Exception) {
                valid = false
            }
        }

        return valid
    }

    private fun checkValue(value: String, min: Int, max: Int) {
        val fields = StringUtils.delimitedListToStringArray(value, ",")
        for (field in fields) {
            val slashPos = field.indexOf('/')
            if (slashPos == -1) {
                checkRange(field, min, max)
            } else {
                val rangeStr = field.substring(0, slashPos)
                checkRange(rangeStr, min, max)
                val deltaStr = field.substring(slashPos + 1)
                require(deltaStr.toInt() > 0)
            }
        }
    }

    private fun checkRange(value: String, min: Int, max: Int) {
        if (value != "*") {
            val range = min..max
            val hyphenPos = value.indexOf('-')
            if (hyphenPos == -1) {
                require(value.toInt() in range)
            } else {
                require(value.substring(0, hyphenPos).toInt() in range)
                require(value.substring(hyphenPos + 1).toInt() in range)
            }
        }
    }

    private const val MIN_YEAR = 1970
    private const val MAX_YEAR = 2099
}
