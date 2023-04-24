/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.analyst.pojo.response.filter

data class MergedFilterRule(
    val ignoreRule: MergedFilterRuleData = MergedFilterRuleData(),
    val includeRule: MergedFilterRuleData = MergedFilterRuleData(),
    var minSeverityLevel: Int? = null
) {
    /**
     * 不在包含列表或在忽略列表中的漏洞将被忽略
     */
    fun shouldIgnore(
        vulId: String,
        cveId: String? = null,
        riskyPackageKey: String? = null,
        severity: Int? = null
    ): Boolean {
        return if (ignoreByIncludeRule(includeRule.vulIds, cveId) && ignoreByIncludeRule(includeRule.vulIds, vulId)) {
            true
        } else if (ignoreByIgnoreRule(ignoreRule.vulIds, cveId) || ignoreByIgnoreRule(ignoreRule.vulIds, vulId)) {
            true
        } else if (ignoreByIncludeRule(includeRule.riskyPackageKeys, riskyPackageKey) ||
            ignoreByIgnoreRule(ignoreRule.riskyPackageKeys, riskyPackageKey)) {
            true
        } else {
            minSeverityLevel != null && severity != null && severity < minSeverityLevel!!
        }
    }

    /**
     * 不在包含列表或在忽略列表中的许可证将被忽略
     */
    fun shouldIgnore(licenseName: String): Boolean {
        return ignoreByIncludeRule(includeRule.licenses, licenseName) ||
            ignoreByIgnoreRule(ignoreRule.licenses, licenseName)
    }

    private fun ignoreByIgnoreRule(ignore: Set<String>?, result: String?): Boolean {
        return ignore != null && result in ignore || ignore?.isEmpty() == true
    }

    private fun ignoreByIncludeRule(include: Set<String>?, result: String?): Boolean {
        return !include.isNullOrEmpty() && result !in include
    }
}
