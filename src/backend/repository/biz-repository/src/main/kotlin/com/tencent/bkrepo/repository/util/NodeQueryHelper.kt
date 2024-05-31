/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.repository.util

import com.tencent.bkrepo.auth.api.ServicePermissionClient
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.path.PathUtils.escapeRegex
import com.tencent.bkrepo.common.artifact.path.PathUtils.toFullPath
import com.tencent.bkrepo.common.artifact.path.PathUtils.toPath
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.exists
import org.springframework.data.mongodb.core.query.inValues
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.regex
import org.springframework.data.mongodb.core.query.where
import java.time.LocalDateTime

/**
 * 查询条件构造工具
 */
object NodeQueryHelper {
    private val logger = LoggerFactory.getLogger(NodeQueryHelper::class.java)

    fun nodeQuery(projectId: String, repoName: String, fullPath: String? = null): Query {
        val criteria = where(TNode::projectId).isEqualTo(projectId)
            .and(TNode::repoName).isEqualTo(repoName)
            .and(TNode::deleted).isEqualTo(null)
            .apply { fullPath?.run { and(TNode::fullPath).isEqualTo(fullPath) } }
        return Query(criteria)
    }

    fun nodeQuery(projectId: String, repoName: String, fullPath: List<String>): Query {
        val criteria = where(TNode::projectId).isEqualTo(projectId)
            .and(TNode::repoName).isEqualTo(repoName)
            .and(TNode::fullPath).inValues(fullPath)
            .and(TNode::deleted).isEqualTo(null)
        return Query(criteria)
    }

    fun nodeFolderQuery(projectId: String, repoName: String, fullPath: String? = null): Query {
        val criteria = where(TNode::projectId).isEqualTo(projectId)
            .and(TNode::repoName).isEqualTo(repoName)
            .and(TNode::deleted).isEqualTo(null)
            .apply { fullPath?.run { and(TNode::fullPath).isEqualTo(fullPath) } }
            .and(TNode::folder).isEqualTo(true)
        return Query(criteria)
    }

    fun nodeListCriteria(projectId: String, repoName: String, path: String, option: NodeListOption): Criteria {
        val nodePath = toPath(path)
        val criteria = where(TNode::projectId).isEqualTo(projectId)
            .and(TNode::repoName).isEqualTo(repoName)
            .and(TNode::deleted).isEqualTo(null)
        if (option.deep) {
            criteria.and(TNode::fullPath).regex("^${escapeRegex(nodePath)}")
        } else {
            criteria.and(TNode::path).isEqualTo(nodePath)
        }
        if (!option.includeFolder) {
            criteria.and(TNode::folder).isEqualTo(false)
        }
        return if (option.hasPermissionPath?.isEmpty() == true) {
            // hasPermissionPath为empty时所有路径都无权限,构造一个永远不成立的条件使查询结果为空列表
            TNode::projectId.exists(false)
        } else if (option.hasPermissionPath?.isNotEmpty() == true) {
            Criteria().andOperator(
                criteria,
                Criteria().orOperator(buildHasPermissionPathCriteria(option.hasPermissionPath!!))
            )
        } else if (option.noPermissionPath.isNotEmpty()) {
            Criteria().andOperator(
                criteria,
                Criteria().norOperator(buildNoPermissionPathCriteria(option.noPermissionPath))
            )
        } else {
            criteria
        }
    }

    fun nodeListQuery(
        projectId: String,
        repoName: String,
        path: String,
        option: NodeListOption
    ): Query {
        val query = Query(nodeListCriteria(projectId, repoName, path, option))
        if (option.sortProperty.isNotEmpty()) {
            option.direction.zip(option.sortProperty).forEach {
                query.with(Sort.by(Sort.Direction.valueOf(it.first), it.second))
            }
        }
        if (option.sort) {
            if (option.includeFolder) {
                query.with(Sort.by(Sort.Direction.DESC, TNode::folder.name))
            }
            query.with(Sort.by(Sort.Direction.ASC, TNode::fullPath.name))
        }
        if (!option.includeMetadata) {
            query.fields().exclude(TNode::metadata.name)
        }
        if (option.deep) {
            query.withHint(TNode.FULL_PATH_IDX)
        } else {
            query.withHint(TNode.PATH_IDX)
        }
        return query
    }

    /**
     * 查询节点被删除的记录
     */
    fun nodeDeletedPointListQuery(projectId: String, repoName: String, fullPath: String): Query {
        val criteria = where(TNode::projectId).isEqualTo(projectId)
            .and(TNode::repoName).isEqualTo(repoName)
            .and(TNode::fullPath).isEqualTo(toFullPath(fullPath))
            .and(TNode::deleted).ne(null)
        return Query(criteria).with(Sort.by(Sort.Direction.DESC, TNode::deleted.name))
    }

    /**
     * 通过sha256查询被删除节点详情
     */
    fun nodeDeletedPointListQueryBySha256(projectId: String, repoName: String, sha256: String): Query {
        val criteria = where(TNode::projectId).isEqualTo(projectId)
            .and(TNode::repoName).isEqualTo(repoName)
            .and(TNode::sha256).isEqualTo(sha256)
            .and(TNode::deleted).ne(null)
        return Query(criteria).with(Sort.by(Sort.Direction.DESC, TNode::deleted.name))
    }

    /**
     * 查询单个被删除节点
     */
    fun nodeDeletedPointQuery(projectId: String, repoName: String, fullPath: String, deleted: LocalDateTime): Query {
        val criteria = where(TNode::projectId).isEqualTo(projectId)
            .and(TNode::repoName).isEqualTo(repoName)
            .and(TNode::fullPath).isEqualTo(toFullPath(fullPath))
            .and(TNode::deleted).isEqualTo(deleted)
        return Query(criteria)
    }

    /**
     * 查询被删除的目录以及子节点
     */
    fun nodeDeletedFolderQuery(
        projectId: String,
        repoName: String,
        path: String,
        deleted: LocalDateTime,
        deep: Boolean
    ): Query {
        val nodePath = toPath(path)
        val criteria = where(TNode::projectId).isEqualTo(projectId)
            .and(TNode::repoName).isEqualTo(repoName)
            .and(TNode::deleted).isEqualTo(deleted)
        if (deep) {
            criteria.and(TNode::fullPath).regex("^${escapeRegex(nodePath)}")
        } else {
            criteria.and(TNode::path).isEqualTo(nodePath)
        }
        return Query(criteria)
    }

    fun nodeRestoreUpdate(): Update {
        return Update().unset(TNode::deleted.name)
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
    fun nodeSetLength(newLength: Long, operator: String): Update {
        return update(operator).apply {
            set(TNode::size.name, newLength)
        }
    }

    fun nodeDeleteUpdate(operator: String, deleteTime: LocalDateTime = LocalDateTime.now()): Update {
        return Update()
            .set(TNode::lastModifiedBy.name, operator)
            .set(TNode::deleted.name, deleteTime)
    }

    /**
     * 查询有权限与无权限的路径
     *
     * @param userId 用户
     * @param projectId 项目
     * @param repoName 仓库
     *
     * @return first为有权限的路径，为空时表示所有路径均无权限，为null时表示未配置，second为无权限路径
     */
    fun ServicePermissionClient.listPermissionPaths(
        userId: String,
        projectId: String,
        repoName: String
    ): Pair<List<String>?, List<String>> {
        val result = listPermissionPath(userId, projectId, repoName).data!!
        if (result.status) {
            require(result.path.isNotEmpty())
            require(result.path.all { it.key == OperationType.IN } || result.path.all { it.key == OperationType.NIN })
            val opType = result.path.entries.first().key
            return listPermissionPaths(userId, projectId, repoName, opType, result.path.values.flatten())
        }
        return Pair(null, emptyList())
    }

    private fun listPermissionPaths(
        userId: String,
        projectId: String,
        repoName: String,
        operationType: OperationType,
        paths: List<String>,
    ): Pair<List<String>?, List<String>> {
        if (operationType == OperationType.NIN) {
            logger.info("user[$userId] does not have permission to $paths of [$projectId/$repoName]")
            return Pair(null, paths)
        }

        if (operationType == OperationType.IN) {
            logger.info("user[$userId] have permission to $paths of [$projectId/$repoName]")
            return Pair(paths, emptyList())
        }

        throw UnsupportedOperationException("Unsupported operation [$operationType].")
    }

    private fun buildNoPermissionPathCriteria(paths: List<String>) = paths.flatMap {
        listOf(TNode::fullPath.isEqualTo(it), TNode::fullPath.regex("^${escapeRegex(it)}"))
    }

    private fun buildHasPermissionPathCriteria(paths: List<String>) = paths.flatMap { path ->
        val parentFolders = PathUtils.resolveAncestorFolder(path)
        val criteriaList = ArrayList<Criteria>(parentFolders.size + 2)
        // 拥有文件权限时将自动拥有父目录查看权限，在前端或者bk-driver中才能查看子目录及文件
        parentFolders.forEach { criteriaList.add(TNode::fullPath.isEqualTo(it)) }
        criteriaList.add(TNode::fullPath.isEqualTo(path))
        criteriaList.add(TNode::fullPath.regex("^${escapeRegex(path)}"))
        criteriaList
    }

    private fun update(operator: String): Update {
        return Update()
            .set(TNode::lastModifiedDate.name, LocalDateTime.now())
            .set(TNode::lastModifiedBy.name, operator)
    }
}
