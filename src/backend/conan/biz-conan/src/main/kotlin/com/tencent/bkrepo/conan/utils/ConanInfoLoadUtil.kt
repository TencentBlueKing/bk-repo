/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.conan.utils

import com.tencent.bkrepo.common.api.constant.CharPool
import com.tencent.bkrepo.conan.exception.ConanException
import com.tencent.bkrepo.conan.pojo.ConanInfo
import org.apache.commons.io.FileUtils
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * conaninfo.txt内容解析工具
 */
object ConanInfoLoadUtil {

    private val allowFields = listOf(
        "settings", "full_settings", "options", "full_options",
        "requires", "full_requires", "scope", "recipe_hash", "env"
    )

    fun load(file: File): ConanInfo {
        val parser = configParser(file)
        return ConanInfo(
            settings = valueLoad(parser["settings"].orEmpty()),
            options = valueLoad(parser["options"].orEmpty()),
            requires = parser["requires"].orEmpty(),
            recipeHash = parser["recipe_hash"]?.first().orEmpty()
        )
    }

    private fun configParser(file: File, allowFields: List<String> = listOf()): Map<String, List<String>> {
        val lines = FileUtils.readLines(file, StandardCharsets.UTF_8)
        val pattern = Regex("^\\[([a-z_]{2,50})]")
        var currentLines: MutableList<String>? = null
        val result = mutableMapOf<String, List<String>>()
        for (line in lines) {
            if (line.isEmpty() || line[0] == CharPool.HASH_TAG) {
                continue
            }
            var field: String? = null
            if (line[0] == CharPool.LEFT_SQUARE_BRACKET) {
                val m = pattern.matchEntire(line) ?: throw ConanException("Bad syntax $line")
                field = m.groups[1]?.value
            }
            if (field != null) {
                if (allowFields.isEmpty() || allowFields.contains(field)) {
                    currentLines = mutableListOf()
                    result[field] = currentLines
                } else {
                    throw ConanException("Unrecognized field $field")
                }
            } else {
                if (currentLines == null) {
                    throw ConanException("Unexpected line $line")
                } else {
                    currentLines.add(line.trim())
                }
            }
        }
        return result
    }

    private fun valueLoad(lines: List<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (line in lines) {
            if (line.isEmpty()) {
                continue
            }
            val values = line.split(Regex(CharPool.EQUAL), 2)
            result[values[0]] = values[1]
        }
        return result
    }
}
