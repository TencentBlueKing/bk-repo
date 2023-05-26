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

import com.tencent.bkrepo.analyst.utils.VersionNumber
import org.slf4j.LoggerFactory

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
        riskyPackageVersions: Set<String>? = null,
        severity: Int? = null
    ): Boolean {
        return if (ignoreByIncludeRule(includeRule.vulIds, cveId) && ignoreByIncludeRule(includeRule.vulIds, vulId)) {
            true
        } else if (ignoreByIgnoreRule(ignoreRule.vulIds, cveId) || ignoreByIgnoreRule(ignoreRule.vulIds, vulId)) {
            true
        } else if (ignoreByIncludeRule(includeRule.riskyPackageKeys, riskyPackageKey) ||
            ignoreByIgnoreRule(ignoreRule.riskyPackageKeys, riskyPackageKey)) {
            true
        } else if (ignoreByVersionRange(riskyPackageKey, riskyPackageVersions)) {
            true
        } else {
            minSeverityLevel != null && severity != null && severity < minSeverityLevel!!
        }
    }

    @Suppress("SwallowedException")
    private fun ignoreByVersionRange(
        riskyPackageKey: String?,
        riskyPackageVersions: Set<String>?
    ): Boolean {
        val includeVersionRange = includeRule.riskyPackageVersions?.get(riskyPackageKey)
        var ignoreByIncludeRule = false

        if (includeVersionRange == null && !includeRule.riskyPackageVersions.isNullOrEmpty()) {
            ignoreByIncludeRule = true
        } else if (includeVersionRange != null && !riskyPackageVersions.isNullOrEmpty()) {
            // riskyPackageVersions为空时不忽略风险组件，避免漏报
            ignoreByIncludeRule = riskyPackageVersions.none {
                try {
                    includeVersionRange.contains(VersionNumber(it))
                } catch (e: VersionNumber.UnsupportedVersionException) {
                    // 不支持的版本格式不忽略，避免漏报
                    logger.warn("unsupported pkg[$riskyPackageKey] version[$it]")
                    true
                }
            }
        }

        if (ignoreByIncludeRule) {
            return true
        }

        val ignoreVersionRange = ignoreRule.riskyPackageVersions?.get(riskyPackageKey)
        var ignoreByIgnoreRule = false

        // riskyPackageVersions为空列表时将忽略该组件的所有漏洞
        if (ignoreRule.riskyPackageVersions?.isEmpty() == true) {
            ignoreByIgnoreRule = true
        } else if (ignoreVersionRange != null && !riskyPackageVersions.isNullOrEmpty()) {
            ignoreByIgnoreRule = riskyPackageVersions.any {
                try {
                    ignoreVersionRange.contains(VersionNumber(it))
                } catch (e: VersionNumber.UnsupportedVersionException) {
                    // 不支持的版本格式不忽略，避免漏报
                    logger.warn("unsupported pkg[$riskyPackageKey] version[$it]")
                    false
                }
            }
        }

        return ignoreByIgnoreRule
    }

    fun containsRiskyPackageVersionsRule(): Boolean {
        return !ignoreRule.riskyPackageVersions.isNullOrEmpty() || !includeRule.riskyPackageVersions.isNullOrEmpty()
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

    companion object {
        private val logger = LoggerFactory.getLogger(MergedFilterRule::class.java)
    }
}
