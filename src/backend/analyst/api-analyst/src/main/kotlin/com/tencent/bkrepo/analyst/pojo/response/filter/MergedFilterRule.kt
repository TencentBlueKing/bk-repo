/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 Tencent.  All rights reserved.
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

import com.tencent.bkrepo.analyst.pojo.Constant
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
        return !includeRule.isEmpty() && !included(vulId, cveId, riskyPackageKey, riskyPackageVersions) ||
            ignored(vulId, cveId, riskyPackageKey, riskyPackageVersions, severity)
    }

    @Suppress("SwallowedException")
    private fun included(
        vulId: String,
        cveId: String? = null,
        riskyPackageKey: String? = null,
        riskyPackageVersions: Set<String>? = null,
    ): Boolean {
        var included = match(includeRule.vulIds, cveId) ||
            match(includeRule.vulIds, vulId) ||
            match(includeRule.riskyPackageKeys, riskyPackageKey) ||
            includeRule.riskyPackageVersions?.isEmpty() == true
        val versionRange = riskyPackageKey?.let { includeRule.riskyPackageVersions?.get(it) }
        if (!included && versionRange != null) {
            // 未扫描出组件版本且存在该组件的版本范围时直接包含在结果中
            // 只要有一个版本被包含，就包含该组件
            included = riskyPackageVersions.isNullOrEmpty() || riskyPackageVersions.any {
                try {
                    versionRange.contains(VersionNumber(it))
                } catch (e: VersionNumber.UnsupportedVersionException) {
                    // 不支持的版本格式不忽略，避免漏报
                    logger.warn("unsupported pkg[$riskyPackageKey] version[$it]")
                    true
                }
            }
        }
        return included
    }

    @Suppress("SwallowedException")
    private fun ignored(
        vulId: String,
        cveId: String? = null,
        riskyPackageKey: String? = null,
        riskyPackageVersions: Set<String>? = null,
        severity: Int? = null
    ): Boolean {
        var ignored = match(ignoreRule.vulIds, cveId) ||
            match(ignoreRule.vulIds, vulId) ||
            match(ignoreRule.riskyPackageKeys, riskyPackageKey) ||
            minSeverityLevel != null && severity != null && severity < minSeverityLevel!! ||
            ignoreRule.riskyPackageVersions?.isEmpty() == true

        val ignoreVersionRange = riskyPackageKey?.let { ignoreRule.riskyPackageVersions?.get(it) }

        // 未扫描出组件版本时不进行版本范围判断
        if (!ignored && ignoreVersionRange != null && !riskyPackageVersions.isNullOrEmpty()) {
            // 全部版本都被忽略时才忽略该组件
            ignored = riskyPackageVersions.all {
                try {
                    ignoreVersionRange.contains(VersionNumber(it))
                } catch (e: VersionNumber.UnsupportedVersionException) {
                    // 不支持的版本格式不忽略，避免漏报
                    logger.warn("unsupported pkg[$riskyPackageKey] version[$it]")
                    false
                }
            }
        }

        return ignored
    }

    fun add(rule: FilterRule) {
        if (rule.type == Constant.FILTER_RULE_TYPE_IGNORE) {
            val severity = rule.severity
            if (severity != null && (this.minSeverityLevel == null || severity > this.minSeverityLevel!!)) {
                this.minSeverityLevel = severity
            }
            this.ignoreRule.add(rule)
        } else if (rule.type == Constant.FILTER_RULE_TYPE_INCLUDE) {
            this.includeRule.add(rule)
        }
    }

    /**
     * 不在包含列表或在忽略列表中的许可证将被忽略
     */
    fun shouldIgnore(licenseName: String): Boolean {
        return !includeRule.isEmpty() && !match(includeRule.licenses, licenseName) ||
            match(ignoreRule.licenses, licenseName)
    }

    fun isEmpty(): Boolean {
        return includeRule.isEmpty() && ignoreRule.isEmpty() && minSeverityLevel == null
    }

    private fun match(set: Set<String>?, item: String?, ignoreCase: Boolean = true): Boolean {
        return set?.isEmpty() == true ||
                !set.isNullOrEmpty() && set.any { it.equals(item, ignoreCase = ignoreCase) }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MergedFilterRule::class.java)
    }
}
