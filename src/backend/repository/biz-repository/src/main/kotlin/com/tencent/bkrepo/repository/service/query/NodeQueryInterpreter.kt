package com.tencent.bkrepo.repository.service.query

import com.tencent.bkrepo.common.query.builder.MongoQueryInterpreter
import com.tencent.bkrepo.common.query.interceptor.QueryContext
import com.tencent.bkrepo.common.query.model.QueryModel
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

@Component
class NodeQueryInterpreter : MongoQueryInterpreter() {

    @Autowired
    private lateinit var repoNameRuleInterceptor: RepoNameRuleInterceptor

    @Autowired
    private lateinit var repoTypeRuleInterceptor: RepoTypeRuleInterceptor

    @PostConstruct
    fun init() {
        addModelInterceptor(NodeQueryInterceptor())
        addModelInterceptor(NodeSelectInterceptor())
        addRuleInterceptor(repoTypeRuleInterceptor)
        addRuleInterceptor(repoNameRuleInterceptor)
        addRuleInterceptor(StageTagRuleInterceptor())
        addRuleInterceptor(MetadataRuleInterceptor())
    }

    override fun initContext(queryModel: QueryModel, mongoQuery: Query): QueryContext {
        return NodeQueryContext(queryModel, mongoQuery, this)
    }
}
