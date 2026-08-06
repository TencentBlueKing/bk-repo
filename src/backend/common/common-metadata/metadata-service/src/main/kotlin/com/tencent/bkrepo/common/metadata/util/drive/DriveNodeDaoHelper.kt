package com.tencent.bkrepo.common.metadata.util.drive

import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode
import com.tencent.bkrepo.common.metadata.pojo.drive.DriveMetadataQueryRule
import com.tencent.bkrepo.common.metadata.pojo.drive.DriveNameQueryRule
import com.tencent.bkrepo.common.metadata.search.common.MetadataRuleInterceptor
import com.tencent.bkrepo.common.query.builder.MongoQueryInterpreter
import com.tencent.bkrepo.common.query.interceptor.QueryContext
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.repository.constant.METADATA_PREFIX
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where

object DriveNodeDaoHelper {

    private val queryInterpreter = MongoQueryInterpreter().apply {
        addRuleInterceptor(MetadataRuleInterceptor())
    }

    fun currentParentNameCriteria(projectId: String, repoName: String, parent: Long, name: String): Criteria {
        return listChildrenCriteria(projectId, repoName, parent).and(TDriveNode::name.name).isEqualTo(name)
    }

    fun listChildrenCriteria(
        projectId: String,
        repoName: String,
        parent: Long? = null,
        snapSeq: Long? = null,
    ): Criteria {
        val criteria = where(TDriveNode::projectId).isEqualTo(projectId)
            .and(TDriveNode::repoName.name).isEqualTo(repoName)
        parent?.let { criteria.and(TDriveNode::parent.name).isEqualTo(it) }
        return if (snapSeq == null) {
            criteria.and(TDriveNode::deleteSnapSeq.name).isEqualTo(Long.MAX_VALUE)
                .and(TDriveNode::deleted.name).isEqualTo(null)
        } else {
            criteria.and(TDriveNode::snapSeq.name).lte(snapSeq)
                .and(TDriveNode::deleteSnapSeq.name).gt(snapSeq)
        }
    }

    /**
     * 整仓当前视图下的普通文件搜索条件（排除目录、软链、已删除）
     *
     * [name] 通过查询解释器解析，语义对齐节点搜索 name 规则
     * [metadata] 多条规则按 AND 组合；单条规则通过 [MetadataRuleInterceptor] 转为 metadata elemMatch
     */
    fun searchCriteria(
        projectId: String,
        repoName: String,
        name: DriveNameQueryRule? = null,
        metadata: List<DriveMetadataQueryRule> = emptyList(),
    ): Criteria {
        val criteria = where(TDriveNode::projectId).isEqualTo(projectId)
            .and(TDriveNode::repoName.name).isEqualTo(repoName)
            .and(TDriveNode::type.name).isEqualTo(TDriveNode.TYPE_FILE)
            .and(TDriveNode::deleteSnapSeq.name).isEqualTo(Long.MAX_VALUE)
            .and(TDriveNode::deleted.name).isEqualTo(null)
        val extraCriteria = mutableListOf<Criteria>()
        name?.let {
            extraCriteria.add(resolveRuleCriteria(Rule.QueryRule(TDriveNode::name.name, it.value, it.operation)))
        }
        metadata.forEach { rule ->
            extraCriteria.add(
                resolveRuleCriteria(Rule.QueryRule(METADATA_PREFIX + rule.key, rule.value, rule.operation)),
            )
        }
        if (extraCriteria.isNotEmpty()) {
            criteria.andOperator(*extraCriteria.toTypedArray())
        }
        return criteria
    }

    private fun resolveRuleCriteria(queryRule: Rule.QueryRule): Criteria {
        val queryModel = QueryModel(page = PageLimit(), sort = null, select = null, rule = queryRule)
        val context = QueryContext(queryModel, false, Query(), queryInterpreter)
        return queryInterpreter.resolveRule(queryRule, context)
    }
}
