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

package com.tencent.bkrepo.auth.util.scope

import com.tencent.bkrepo.auth.pojo.account.ScopeRule
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.query.enums.OperationType

object RuleUtil {

    fun check(scopeRule: ScopeRule, target: String, resourceType: ResourceType): Boolean {
        var targetVal = target
        if (resourceType == ResourceType.ENDPOINT) {
            targetVal = "/" + target.removePrefix("/").removeSuffix("/")
        }

        if (scopeRule.operation == OperationType.EQ) {
            if (targetVal == scopeRule.value) {
                return true
            }
        }

        if (scopeRule.operation == OperationType.IN) {
            val valueList = scopeRule.value as List<*>
            if (valueList.contains(targetVal)) {
                return true
            }
        }

        if (scopeRule.operation == OperationType.PREFIX) {
            val valuePrefix = scopeRule.value as String
            if (targetVal.startsWith(valuePrefix)) {
                return true
            }
        }

        if (scopeRule.operation == OperationType.SUFFIX) {
            val valuePrefix = scopeRule.value as String
            if (targetVal.endsWith(valuePrefix)) {
                return true
            }
        }
        return false
    }
}
