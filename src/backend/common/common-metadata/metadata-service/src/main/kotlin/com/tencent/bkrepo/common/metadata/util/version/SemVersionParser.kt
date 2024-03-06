/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 *  A copy of the MIT License is included in this file.
 *
 *
 *  Terms of the MIT License:
 *  ---------------------------------------------------
 *  Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 *  documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 *  rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 *  permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 *  the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 *  LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 *  NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 *  WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 *  SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.metadata.util.version

object SemVersionParser {

    /**
     * 语义化版本正则表达式
     */
    private const val PATTERN = "(?<major>0|[1-9]\\d*)?(?:\\.)?(?<minor>0|[1-9]\\d*)?(?:\\.)?" +
        "(?<patch>0|[1-9]\\d*)?(?:-(?<prerelease>[\\dA-z\\-]+(?:\\.[\\dA-z\\-]+)*))?" +
        "(?:\\+(?<buildmetadata>[\\dA-z\\-]+(?:\\.[\\dA-z\\-]+)*))?"

    private val regex = Regex(PATTERN)

    fun parse(input: String): SemVersion {
        val match = regex.find(input) ?: throw IllegalArgumentException("Invalid version string [$input]")
        val major = parseIntOrZero(match.groups["major"]?.value)
        val minor = parseIntOrZero(match.groups["minor"]?.value)
        val patch = parseIntOrZero(match.groups["patch"]?.value)
        val preRelease = match.groups["prerelease"]?.value
        val buildMetadata = match.groups["buildmetadata"]?.value
        return SemVersion(major, minor, patch, preRelease, buildMetadata)
    }

    private fun parseIntOrZero(input: String?): Int {
        return input?.toInt() ?: 0
    }
}
