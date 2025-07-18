/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.common.metadata.search.common

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.query.builder.MongoQueryInterpreter
import com.tencent.bkrepo.common.query.interceptor.QueryContext
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import org.springframework.data.mongodb.core.query.Query

open class CommonQueryContext(
    override var queryModel: QueryModel,
    override var permissionChecked: Boolean,
    override val mongoQuery: Query,
    override val interpreter: MongoQueryInterpreter
) : QueryContext(queryModel, permissionChecked, mongoQuery, interpreter) {

    private var projectId: String? = null
    var repoList: List<RepositoryInfo>? = null

    fun findProjectId(): String {
        if (projectId != null) {
            return projectId!!
        }
        val rule = queryModel.rule
        if (rule is Rule.NestedRule && rule.relation == Rule.NestedRule.RelationType.AND) {
            findProjectIdRule(rule.rules)?.let {
                return it.value.toString().apply { projectId = this }
            }
        }
        throw ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, "projectId")
    }

    private fun findProjectIdRule(rules: List<Rule>): Rule.QueryRule? {
        for (rule in rules) {
            if (rule is Rule.QueryRule && rule.field == TNode::projectId.name) {
                return rule
            }
        }
        return null
    }
}
