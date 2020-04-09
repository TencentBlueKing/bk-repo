package com.tencent.bkrepo.repository.service.util

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.pojo.node.service.NodeSearchRequest
import com.tencent.bkrepo.repository.util.NodeUtils
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import java.time.LocalDateTime

/**
 * 查询条件构造工具
 *
 * @author: carrypan
 * @date: 2019-10-18
 */
object QueryHelper {

    fun nodeQuery(projectId: String, repoName: String, fullPath: String? = null, withDetail: Boolean = false): Query {
        val criteria = Criteria.where(TNode::projectId.name).`is`(projectId)
                .and(TNode::repoName.name).`is`(repoName)
                .and(TNode::deleted.name).`is`(null)

        val query = Query(criteria)

        fullPath?.run { criteria.and(TNode::fullPath.name).`is`(fullPath) }
        if (!withDetail) { query.fields().exclude(TNode::metadata.name) }

        return query
    }

    fun nodeSearchQuery(searchRequest: NodeSearchRequest): Query {
        return with(searchRequest) {
            val criteria = Criteria.where(TNode::projectId.name).`is`(projectId)
                    .and(TNode::repoName.name).`in`(repoNameList)
                    .and(TNode::deleted.name).`is`(null)
                    .and(TNode::name.name).ne(StringPool.EMPTY)

            // 路径匹配
            val fullPathCriteriaList = pathPattern.map {
                val escapedPath = NodeUtils.escapeRegex(NodeUtils.formatPath(it))
                Criteria.where(TNode::fullPath.name).regex("^$escapedPath")
            }
            if (fullPathCriteriaList.isNotEmpty()) {
                criteria.orOperator(*fullPathCriteriaList.toTypedArray())
            }
            // 元数据匹配
            val metadataCriteriaList = metadataCondition.filter { it.key.isNotBlank() }.map { (key, value) ->
                Criteria.where("metadata.key").`is`(key)
                .and("metadata.value").`is`(value)
            }
            if (metadataCriteriaList.isNotEmpty()) {
                criteria.andOperator(*metadataCriteriaList.toTypedArray())
            }

            Query(criteria).with(PageRequest.of(page, size)).with(Sort.by(TNode::fullPath.name))
        }
    }

    fun nodeListCriteria(projectId: String, repoName: String, path: String, includeFolder: Boolean, deep: Boolean): Criteria {
        val formattedPath = NodeUtils.formatPath(path)
        val escapedPath = NodeUtils.escapeRegex(formattedPath)
        val criteria = Criteria.where(TNode::projectId.name).`is`(projectId)
                .and(TNode::repoName.name).`is`(repoName)
                .and(TNode::deleted.name).`is`(null)
                .and(TNode::name.name).ne(StringPool.EMPTY)

        if (deep) criteria.and(TNode::fullPath.name).regex("^$escapedPath")
        else criteria.and(TNode::path.name).`is`(formattedPath)

        if (!includeFolder) { criteria.and(TNode::folder.name).`is`(false) }

        return criteria
    }

    fun nodeListQuery(projectId: String, repoName: String, path: String, includeFolder: Boolean, deep: Boolean): Query {
        return Query.query(
            nodeListCriteria(
                projectId,
                repoName,
                path,
                includeFolder,
                deep
            )
        ).with(Sort.by(TNode::fullPath.name))
    }

    fun nodePageQuery(projectId: String, repoName: String, path: String, includeFolder: Boolean, deep: Boolean, page: Int, size: Int): Query {
        return nodeListQuery(
            projectId,
            repoName,
            path,
            includeFolder,
            deep
        ).with(PageRequest.of(page, size))
    }

    fun nodePathUpdate(path: String, name: String, operator: String): Update {
        return update(operator)
                .set(TNode::path.name, path)
                .set(TNode::name.name, name)
                .set(TNode::fullPath.name, path + name)
    }

    fun nodeExpireDateUpdate(expireDate: LocalDateTime?, operator: String): Update {
        return update(operator).apply {
            expireDate?.let { set(TNode::expireDate.name, expireDate) } ?: run { unset(TNode::expireDate.name) }
        }
    }

    fun nodeRepoUpdate(projectId: String, repoName: String, path: String, name: String, operator: String): Update {
        return update(operator)
                .set(TNode::projectId.name, projectId)
                .set(TNode::repoName.name, repoName)
                .set(TNode::path.name, path)
                .set(TNode::name.name, name)
                .set(TNode::fullPath.name, path + name)
    }

    fun nodeDeleteUpdate(operator: String): Update {
        return update(operator)
            .set(TNode::deleted.name, LocalDateTime.now())
    }

    fun nodeMetadataQuery(projectId: String, repoName: String, fullPath: String, key: String): Query {
        return Query(Criteria.where(TNode::projectId.name).`is`(projectId)
            .and(TNode::repoName.name).`is`(repoName)
            .and(TNode::fullPath.name).`is`(fullPath)
            .and(TNode::deleted.name).`is`(null)
            .and("metadata.key").`is`(key)
        )
    }

    private fun update(operator: String): Update {
        return Update()
                .set(TNode::lastModifiedDate.name, LocalDateTime.now())
                .set(TNode::lastModifiedBy.name, operator)
    }
}
