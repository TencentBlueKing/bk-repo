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

package com.tencent.bkrepo.analyst.utils

import com.tencent.bkrepo.common.artifact.constant.REPO_NAME
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.metadata.pojo.node.NodeInfo
import com.tencent.bkrepo.analyst.PROJECT_ID
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class RuleUtilTest {

    @Test
    fun testGetProject() {
        val rule = createNestedRule()
        Assertions.assertEquals(RuleUtil.getProjectIds(rule).first(), PROJECT_ID)
    }

    @Test
    fun testGetRepo() {
        val rule = createNestedRule()
        Assertions.assertEquals(RuleUtil.getRepoNames(rule).first(), REPO_NAME)
    }

    @Test
    fun testFieldValueFromRule() {
        val rule = Rule.FixedRule(Rule.QueryRule(NodeInfo::projectId.name, PROJECT_ID, OperationType.EQ))
        Assertions.assertEquals(RuleUtil.fieldValueFromRule(rule, NodeInfo::projectId.name).first(), PROJECT_ID)

        val rules = ArrayList<Rule>()
        rules.add(Rule.NestedRule(mutableListOf(rule)))
        val nestedRule = Rule.NestedRule(rules)
        Assertions.assertTrue(RuleUtil.fieldValueFromRule(nestedRule, NodeInfo::projectId.name).isEmpty())
    }

    private fun createNestedRule(): Rule {
        val rules = ArrayList<Rule>()
        rules.add(Rule.QueryRule(NodeInfo::projectId.name, PROJECT_ID, OperationType.EQ))
        rules.add(Rule.QueryRule(NodeInfo::repoName.name, REPO_NAME, OperationType.EQ))
        return Rule.NestedRule(rules, Rule.NestedRule.RelationType.AND)
    }
}
