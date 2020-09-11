package com.tencent.bkrepo.repository.service.query

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.query.builder.MongoQueryInterpreter
import com.tencent.bkrepo.common.query.interceptor.QueryContext
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import org.springframework.data.mongodb.core.query.Query

class NodeQueryContext(
    override var queryModel: QueryModel,
    override val mongoQuery: Query,
    override val interpreter: MongoQueryInterpreter
): QueryContext(queryModel, mongoQuery, interpreter) {

    private var projectId: String? = null
    var selectMetadata: Boolean = false
    var selectStageTag: Boolean = false
    var repoList: List<RepositoryInfo>? = null

    fun findProjectId(): String {
        if (projectId == null) {
            val rule = queryModel.rule
            if (rule is Rule.NestedRule && rule.relation == Rule.NestedRule.RelationType.AND) {
                for (subRule in rule.rules) {
                    if (subRule is Rule.QueryRule && subRule.field == TNode::projectId.name) {
                        return subRule.value.toString().apply { projectId = this }
                    }
                }
            }
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "projectId")
        }
        return projectId!!
    }
}