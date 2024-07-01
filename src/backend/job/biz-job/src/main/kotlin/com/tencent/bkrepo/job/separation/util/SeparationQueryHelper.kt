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

package com.tencent.bkrepo.job.separation.util

import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.job.SEPARATE
import com.tencent.bkrepo.job.separation.model.TSeparationFailedRecord
import com.tencent.bkrepo.job.separation.model.TSeparationNode
import com.tencent.bkrepo.job.separation.model.TSeparationPackage
import com.tencent.bkrepo.job.separation.model.TSeparationPackageVersion
import com.tencent.bkrepo.job.separation.service.impl.SeparationDataServiceImpl
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import java.time.LocalDateTime

/**
 * 查询条件构造工具
 */
object SeparationQueryHelper {

    fun packageIdQuery(packageId: String): Query {
        val criteria = Criteria.where(ID).isEqualTo(packageId)
        return Query(criteria)
    }

    fun packageQuery(projectId: String, repoName: String, key: String): Query {
        val criteria = where(TSeparationPackage::projectId).isEqualTo(projectId)
            .and(TSeparationPackage::repoName).isEqualTo(repoName)
            .and(TSeparationPackage::key).isEqualTo(key)
        return Query(criteria)
    }

    fun packageKeyQuery(projectId: String, repoName: String, packageKey: String?, packageKeyRegex: String?): Query {
        val criteria = Criteria.where(TSeparationPackage::projectId.name).isEqualTo(projectId)
            .and(TSeparationPackage::repoName.name).isEqualTo(repoName)
            .apply {
                packageKey?.let { and(TSeparationPackage::key.name).isEqualTo(packageKey) }
            }.apply {
                packageKeyRegex?.let { and(TSeparationPackage::key.name).regex(".*${packageKeyRegex}.*") }
            }
        return Query(criteria)
    }

    fun versionIdQuery(versionId: String, separationDate: LocalDateTime): Query {
        val (startOfDay, endOfDay) = SeparationUtils.findStartAndEndTimeOfDate(separationDate)
        val criteria = Criteria.where(ID).isEqualTo(versionId)
            .and(TSeparationPackageVersion::separationDate.name).gte(startOfDay).lt(endOfDay)
        return Query(criteria)
    }

    fun versionIdRemoveQuery(versionId: String, separationDate: LocalDateTime): Query {
        val criteria = Criteria.where(ID).isEqualTo(versionId)
            .and(TSeparationPackageVersion::separationDate.name).isEqualTo(separationDate)
        return Query(criteria)
    }

    fun versionQuery(packageId: String, name: String? = null, separationDate: LocalDateTime): Query {
        val (startOfDay, endOfDay) = SeparationUtils.findStartAndEndTimeOfDate(separationDate)
        val criteria = where(TSeparationPackageVersion::packageId).isEqualTo(packageId)
            .and(TSeparationPackageVersion::separationDate.name).gte(startOfDay).lt(endOfDay)
            .apply {
                name?.let { and(TSeparationPackageVersion::name).isEqualTo(name) }
            }
        return Query(criteria)
    }

    fun versionListQuery(
        packageId: String,
        separationDate: LocalDateTime,
        name: String? = null,
        metadata: List<MetadataModel>? = null,
        sortProperty: String? = null,
        direction: Sort.Direction = Sort.Direction.DESC
    ): Query {
        val (startOfDay, endOfDay) = SeparationUtils.findStartAndEndTimeOfDate(separationDate)
        val criteria = where(TSeparationPackageVersion::packageId).isEqualTo(packageId)
            .and(TSeparationPackageVersion::separationDate).gte(startOfDay).lt(endOfDay)
            .apply {
                name?.let { and(TSeparationPackageVersion::name).regex("^$it") }
            }.apply {
                val criteriaList = mutableListOf<Criteria>()
                metadata?.forEach {
                    criteriaList.add(
                        where(TSeparationPackageVersion::metadata).elemMatch(
                            where(MetadataModel::key).isEqualTo(it.key).and(MetadataModel::value).isEqualTo(it.value)
                        )
                    )
                }
                if (criteriaList.isNotEmpty()) {
                    andOperator(criteriaList)
                }
            }

        return Query(criteria).with(Sort.by(Sort.Order(direction, TSeparationPackageVersion::separationDate.name)))
            .apply {
                if (sortProperty.isNullOrBlank() || sortProperty == TSeparationPackageVersion::name.name) {
                    with(Sort.by(Sort.Order(direction, TSeparationPackageVersion::ordinal.name)))
                } else {
                    with(Sort.by(Sort.Order(direction, sortProperty)))
                }
            }
    }

    fun versionListQuery(
        packageId: String, separationDate: LocalDateTime,
        nameRegex: String? = null, versionList: List<String>? = null,
        excludeVersions: List<String>? = null
    ): Query {
        val (startOfDay, endOfDay) = SeparationUtils.findStartAndEndTimeOfDate(separationDate)
        val criteria = where(TSeparationPackageVersion::packageId).isEqualTo(packageId)
            .and(TSeparationPackageVersion::separationDate.name).gte(startOfDay).lt(endOfDay)
            .apply {
                versionList?.let { and(TSeparationPackageVersion::name).`in`(versionList) }
            }.apply {
                nameRegex?.let { and(TSeparationPackageVersion::name).regex(".*${nameRegex}.*") }
            }.apply {
                nameRegex?.let { and(TSeparationPackageVersion::name).nin(excludeVersions) }
            }
        return Query(criteria)
    }

    fun nodeIdQuery(nodeId: String, separationDate: LocalDateTime): Query {
        val (startOfDay, endOfDay) = SeparationUtils.findStartAndEndTimeOfDate(separationDate)
        val criteria = Criteria.where(ID).isEqualTo(nodeId)
            .and(TSeparationNode::separationDate.name).gte(startOfDay).lt(endOfDay)
        return Query(criteria)
    }

    fun nodeIdRemoveQuery(nodeId: String, separationDate: LocalDateTime): Query {
        val criteria = Criteria.where(ID).isEqualTo(nodeId)
            .and(TSeparationNode::separationDate.name).isEqualTo(separationDate)
        return Query(criteria)
    }

    fun pathQuery(
        projectId: String, repoName: String, versionPath: String, separationDate: LocalDateTime
    ): Query {
        val (startOfDay, endOfDay) = SeparationUtils.findStartAndEndTimeOfDate(separationDate)
        val criteria = Criteria.where(TSeparationNode::projectId.name).isEqualTo(projectId)
            .and(TSeparationNode::repoName.name).isEqualTo(repoName)
            .and(TSeparationNode::fullPath.name).regex("^${PathUtils.escapeRegex(versionPath)}")
            .and(TSeparationNode::folder.name).isEqualTo(false)
            .and(TSeparationNode::separationDate.name).gte(startOfDay).lt(endOfDay)
        return Query(criteria).withHint(FULL_PATH_IDX)
    }

    fun fullPathQuery(projectId: String, repoName: String, fullPath: String, separationDate: LocalDateTime): Query {
        val (startOfDay, endOfDay) = SeparationUtils.findStartAndEndTimeOfDate(separationDate)
        val criteria = Criteria.where(TSeparationNode::projectId.name).isEqualTo(projectId)
            .and(TSeparationNode::repoName.name).isEqualTo(repoName)
            .and(TSeparationNode::fullPath.name).isEqualTo(fullPath)
            .and(TSeparationNode::separationDate.name).gte(startOfDay).lt(endOfDay)
        return Query(criteria)
    }

    fun pathQuery(
        projectId: String, repoName: String, separationDate: LocalDateTime,
        path: String? = null, pathRegex: String? = null, excludePath: List<String>? = null
    ): Query {
        val (startOfDay, endOfDay) = SeparationUtils.findStartAndEndTimeOfDate(separationDate)
        val criteria = Criteria.where(TSeparationNode::projectId.name).isEqualTo(projectId)
            .and(TSeparationNode::repoName.name).isEqualTo(repoName)
            .and(TSeparationNode::folder.name).isEqualTo(false)
            .and(TSeparationNode::separationDate.name).gte(startOfDay).lt(endOfDay)
            .apply {
                path?.let { and(TSeparationNode::fullPath.name).regex("^${PathUtils.escapeRegex(path)}") }
            }
            .apply {
                pathRegex?.let { and(TSeparationNode::fullPath.name).regex(".*${pathRegex}.*") }
            }.apply {
                excludePath?.let { and(TSeparationNode::fullPath.name).nin(excludePath) }
            }
        return Query(criteria).withHint(FULL_PATH_IDX)
    }

    fun nodeListQuery(
        projectId: String,
        repoName: String,
        path: String,
        option: NodeListOption,
        separationDate: LocalDateTime,
    ): Query {
        val (startOfDay, endOfDay) = SeparationUtils.findStartAndEndTimeOfDate(separationDate)
        val nodePath = PathUtils.toPath(path)
        val criteria = where(TSeparationNode::projectId).isEqualTo(projectId)
            .and(TSeparationNode::repoName).isEqualTo(repoName)
            .and(TSeparationNode::separationDate).gte(startOfDay).lt(endOfDay)
        if (option.deep) {
            criteria.and(TSeparationNode::fullPath).regex("^${PathUtils.escapeRegex(nodePath)}")
        } else {
            criteria.and(TSeparationNode::path).isEqualTo(nodePath)
        }
        if (!option.includeFolder) {
            criteria.and(TSeparationNode::folder).isEqualTo(false)
        }
        val query = Query(criteria).with(Sort.by(Sort.Direction.DESC, TSeparationNode::separationDate.name))
        if (option.sortProperty.isNotEmpty()) {
            option.direction.zip(option.sortProperty).forEach {
                query.with(Sort.by(Sort.Direction.valueOf(it.first), it.second))
            }
        }
        if (option.sort) {
            if (option.includeFolder) {
                query.with(Sort.by(Sort.Direction.DESC, TSeparationNode::folder.name))
            }
            query.with(Sort.by(Sort.Direction.ASC, TSeparationNode::fullPath.name))
        }
        if (!option.includeMetadata) {
            query.fields().exclude(TSeparationNode::metadata.name)
        }
        if (option.deep) {
            query.withHint(SeparationDataServiceImpl.FULL_PATH_IDX)
        } else {
            query.withHint(SeparationDataServiceImpl.PATH_IDX)
        }
        return query

    }

    fun failedRecordQuery(
        projectId: String,
        repoName: String,
        taskId: String,
        packageId: String? = null,
        versionId: String? = null,
        nodeId: String? = null,
        type: String = SEPARATE,
    ): Query {
        val criteria = Criteria().and(TSeparationFailedRecord::projectId.name).isEqualTo(projectId)
            .and(TSeparationFailedRecord::repoName.name).isEqualTo(repoName)
            .and(TSeparationFailedRecord::type.name).isEqualTo(type)
            .and(TSeparationFailedRecord::taskId.name).isEqualTo(taskId)
            .and(TSeparationFailedRecord::nodeId.name).isEqualTo(nodeId)
            .and(TSeparationFailedRecord::packageId.name).isEqualTo(packageId)
            .and(TSeparationFailedRecord::versionId.name).isEqualTo(versionId)
        return Query(criteria)
    }

    private const val FULL_PATH_IDX = "projectId_repoName_fullPath_idx"

}
