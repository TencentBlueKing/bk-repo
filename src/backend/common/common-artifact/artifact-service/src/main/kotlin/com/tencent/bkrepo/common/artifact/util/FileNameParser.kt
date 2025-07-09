/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.artifact.util

import com.tencent.bkrepo.common.api.constant.CharPool
import com.tencent.bkrepo.common.api.constant.StringPool
import java.util.regex.Pattern

/**
 * 从文件名中解析出版本以及包名
 */
object FileNameParser {
    private const val VERSION_REGEX = "v?([0-9]+)(\\.[0-9]+)?(\\.[0-9]+)?" +
        "(-([0-9A-Za-z\\-]+(\\.[0-9A-Za-z\\-]+)*))?" +
        "(\\+([0-9A-Za-z\\-]+(\\.[0-9A-Za-z\\-]+)*))?"
    /**
     * 解析包名以及版本名
     * 例如： /bcs-api-gateway-1.26.0-alpha.6.tgz:
     * 包名：bcs-api-gateway  版本：1.26.0-alpha.6
     */
    fun parseNameAndVersion(fullPath: String): Map<String, Any> {
        val substring = fullPath.trimStart('/').substring(0, fullPath.lastIndexOf(".tgz") - 1)
        val parts = substring.split('-')
        val lastIndex = parts.size - 1
        var name = parts[0]
        var version = StringPool.EMPTY
        for (i in lastIndex downTo 0 step 1) {
            val first = parts[i][0]
            var num = parts[i][0]
            // if first char is v
            if (first == 'v' && parts[i].length > 1) {
                num = parts[i][1]
            }
            // see if this part looks like a version (starts with int)
            if (num in '0'..'9') {
                version = buildString(parts.subList(i, parts.size))
                name = buildString(parts.subList(0, i))
                break
            }
        }
        // no parts looked like a real version, just take everything after last hyphen
        if (version.isBlank()) {
            name = buildString(parts.subList(0, lastIndex))
            version = parts[lastIndex]
        }
        return mapOf("name" to name, "version" to version)
    }

    /**
     * helm version check: see isValidSemver in https://github.com/helm/helm/blob/v3.9.0/pkg/chart/metadata.go
     * https://github.com/Masterminds/semver/blob/master/version.go
     * char version regex is `v?([0-9]+)(\.[0-9]+)?(\.[0-9]+)?` +
     *  `(-([0-9A-Za-z\-]+(\.[0-9A-Za-z\-]+)*))?` +
     *  `(\+([0-9A-Za-z\-]+(\.[0-9A-Za-z\-]+)*))?`
     */
    fun parseNameAndVersionWithRegex(fullPath: String): Map<String, Any> {
        val substring = fullPath.trimStart('/').substring(0, fullPath.lastIndexOf(".tgz") - 1)
        val pattern = Pattern.compile(VERSION_REGEX)
        val matcher = pattern.matcher(substring)
        var version = StringPool.EMPTY
        while (matcher.find()) {
            version = matcher.group()
        }
        val name = substring.substring(0, substring.lastIndexOf(CharPool.DASH + version))
        return mapOf("name" to name, "version" to version)
    }

    private fun buildString(list: List<String>): String {
        return list.joinToString(CharPool.DASH.toString())
    }
}
