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

import com.tencent.bkrepo.analyst.utils.CompositeVersionRange
import org.springframework.util.ReflectionUtils

data class MergedFilterRuleData(
    var riskyPackageVersions: MutableMap<String, CompositeVersionRange>? = null,
    var riskyPackageKeys: MutableSet<String>? = null,
    var vulIds: MutableSet<String>? = null,
    var licenses: MutableSet<String>? = null
) {
    fun add(rule: FilterRule) {
        rule.vulIds?.let { this.vulIds = add(this.vulIds, rule, FilterRule::vulIds.name) }
        rule.licenseNames?.let { this.licenses = add(this.licenses, rule, FilterRule::licenseNames.name) }
        rule.riskyPackageKeys?.let {
            this.riskyPackageKeys = add(this.riskyPackageKeys, rule, FilterRule::riskyPackageKeys.name)
        }
        rule.riskyPackageVersions?.let {
            this.riskyPackageVersions = this.riskyPackageVersions ?: HashMap()
            it.forEach { (pkg, versions) ->
                this.riskyPackageVersions!![pkg] = CompositeVersionRange.build(versions)
            }
        }
    }

    fun isEmpty(): Boolean {
        return riskyPackageVersions == null && riskyPackageKeys == null && vulIds == null && licenses == null
    }

    private fun add(target: MutableSet<String>?, rule: FilterRule, ruleName: String): MutableSet<String> {
        val result = target ?: HashSet()
        val field = ReflectionUtils.findField(FilterRule::class.java, ruleName)
        field!!.isAccessible = true
        val rules = field.get(rule)
        if (rules is Set<*> && rules.isNotEmpty()) {
            result.addAll(rules as Set<String>)
        }
        return result
    }
}
