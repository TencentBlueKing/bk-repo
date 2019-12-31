package com.tencent.bkrepo.common.query.util

/**
 *
 * @author: carrypan
 * @date: 2019/11/15
 */
object MongoEscapeUtils {

    private val keywordList = listOf("\\", "$", "(", ")", "*", "+", ".", "[", "]", "?", "^", "{", "}", "|", "?", "&")

    /**
     * 正则特殊符号转义
     */
    fun escapeRegex(input: String): String {
        var escapedString = input.trim()
        if (escapedString.isNotBlank()) {
            keywordList.forEach {
                if (escapedString.contains(it)) {
                    escapedString = escapedString.replace(it, "\\$it")
                }
            }
        }
        return escapedString
    }

    /**
     * 正则特殊符号转义, 但不转义通配符
     */
    fun escapeRegexExceptWildcard(input: String): String {
        var escapedString = input.trim()
        if (escapedString.isNotBlank()) {
            keywordList.filter { it != "*" }.forEach {
                if (escapedString.contains(it)) {
                    escapedString = escapedString.replace(it, "\\$it")
                }
            }
        }
        return escapedString
    }
}
