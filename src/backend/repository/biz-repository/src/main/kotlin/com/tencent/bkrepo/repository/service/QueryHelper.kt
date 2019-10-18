package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.repository.pojo.node.NodeSearchRequest
import com.tencent.bkrepo.repository.util.NodeUtils
import java.time.LocalDateTime
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update

/**
 * 查询条件构造工具
 *
 * @author: carrypan
 * @date: 2019-10-18
 */
object QueryHelper {

    fun nodeQuery(projectId: String, repoName: String, fullPath: String? = null, withDetail: Boolean = false): Query {
        val criteria = Criteria.where("projectId").`is`(projectId)
                .and("repoName").`is`(repoName)
                .and("deleted").`is`(null)

        val query = Query(criteria)

        fullPath?.run { criteria.and("fullPath").`is`(fullPath) }
        takeUnless { withDetail }.run { query.fields().exclude("metadata").exclude("blockList") }

        return query
    }

    fun nodeSearchQuery(searchRequest: NodeSearchRequest): Query {
        return with(searchRequest) {
            val criteria = Criteria.where("projectId").`is`(projectId)
                    .and("repoName").`in`(repoNameList)
                    .and("deleted").`is`(null)

            // 路径匹配
            val criteriaList = pathPattern.map {
                val escapedPath = NodeUtils.escapeRegex(NodeUtils.formatPath(it))
                Criteria.where("fullPath").regex("^$escapedPath")
            }
            criteria.orOperator(*criteriaList.toTypedArray())
            // 元数据匹配
            metadataCondition.filterKeys { it.isNotBlank() }.forEach { (key, value) -> criteria.and("metadata.$key").`is`(value) }

            Query(criteria).with(PageRequest.of(page, size)).with(Sort.by("name"))
        }
    }

    fun nodeListCriteria(projectId: String, repoName: String, path: String, includeFolder: Boolean, deep: Boolean): Criteria {
        val formattedPath = NodeUtils.formatPath(path)
        val escapedPath = NodeUtils.escapeRegex(formattedPath)
        val criteria = Criteria.where("projectId").`is`(projectId)
                .and("repoName").`is`(repoName)
                .and("deleted").`is`(null)

        if (deep) criteria.and("fullPath").regex("^$escapedPath")
        else criteria.and("path").`is`(formattedPath)

        if (!includeFolder) { criteria.and("folder").`is`(false) }

        return criteria
    }

    fun nodeListQuery(projectId: String, repoName: String, path: String, includeFolder: Boolean, deep: Boolean): Query {
        return Query.query(nodeListCriteria(projectId, repoName, path, includeFolder, deep)).with(Sort.by("name"))
    }

    fun nodePageQuery(projectId: String, repoName: String, path: String, includeFolder: Boolean, deep: Boolean, page: Int, size: Int): Query {
        return nodeListQuery(projectId, repoName, path, includeFolder, deep).with(PageRequest.of(page, size))
    }

    fun nodePathUpdate(path: String, name: String, operator: String): Update {
        return update(operator)
                .set("path", path)
                .set("name", name)
                .set("fullPath", path + name)
    }

    fun nodeRepoUpdate(projectId: String, repoName: String, path: String, name: String, operator: String): Update {
        return update(operator)
                .set("projectId", projectId)
                .set("repoName", repoName)
                .set("path", path)
                .set("name", name)
                .set("fullPath", path + name)
    }

    fun nodeDeleteUpdate(operator: String): Update {
        return update(operator).set("deleted", LocalDateTime.now())
    }

    private fun update(operator: String): Update {
        return Update()
                .set("lastModifiedDate", LocalDateTime.now())
                .set("lastModifiedBy", operator)
    }
}
