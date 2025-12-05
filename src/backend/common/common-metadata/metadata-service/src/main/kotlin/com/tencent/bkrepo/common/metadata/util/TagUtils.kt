package com.tencent.bkrepo.common.metadata.util

object TagUtils {

    /**
     * 将 tag 中的特殊字符转换为 Unicode 编码
     * - `.` -> `\u002e`
     * - `$` -> `\u0024`
     */
    fun encodeTag(tag: String): String {
        return tag.replace(".", "\\u002e")
            .replace("$", "\\u0024")
    }

    /**
     * 将 Unicode 编码的 tag 转换回原始字符
     * - `\u002e` -> `.`
     * - `\u0024` -> `$`
     */
    fun decodeTag(encodedTag: String): String {
        return encodedTag.replace("\\u0024", "$")
            .replace("\\u002e", ".")
    }
}