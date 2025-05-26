/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.metadata.util

import com.tencent.bkrepo.common.api.util.EscapeUtils
import com.tencent.bkrepo.common.metadata.model.TPackage
import com.tencent.bkrepo.common.metadata.model.TPackageVersion
import com.tencent.bkrepo.common.metadata.pojo.metadata.MetadataModel
import com.tencent.bkrepo.common.metadata.pojo.packages.PackageVersion
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.inValues
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import java.time.LocalDateTime

/**
 * 查询条件构造工具
 */
object PackageQueryHelper {


    fun packageQuery(packageId: String): Query {
        val criteria = where(TPackage::id).isEqualTo(packageId)
            .and(TPackage::deleted).isEqualTo(null)
        return Query(criteria)
    }

    // package
    fun packageQuery(projectId: String, repoName: String, key: String): Query {
        val criteria = where(TPackage::projectId).isEqualTo(projectId)
            .and(TPackage::repoName).isEqualTo(repoName)
            .and(TPackage::key).isEqualTo(key)
            .and(TPackage::deleted).isEqualTo(null)
        return Query(criteria)
    }

    fun packageListQuery(projectId: String, repoName: String, packageName: String?): Query {
        return Query(packageListCriteria(projectId, repoName, packageName))
    }

    // version
    fun versionQuery(packageId: String, name: String? = null, tag: String? = null): Query {
        val criteria = where(TPackageVersion::packageId).isEqualTo(packageId)
            .and(TPackageVersion::deleted).isEqualTo(null)
            .apply {
                name?.let { and(TPackageVersion::name).isEqualTo(name) }
                tag?.let { and(TPackageVersion::tags).inValues(tag) }
            }
        return Query(criteria)
    }

    fun clusterNameQuery(packageId: String, clusterName: String): Query {
        val criteria = where(TPackageVersion::packageId).isEqualTo(packageId)
            .and(TPackageVersion::clusterNames.name).inValues(clusterName)
            .and(TPackageVersion::deleted).isEqualTo(null)
        return Query(criteria)
    }

    fun versionListQuery(
        packageId: String,
        name: String? = null,
        stageTag: List<String>? = null,
        metadata: List<MetadataModel>? = null,
        sortProperty: String? = null,
        direction: Sort.Direction = Sort.Direction.DESC,
    ): Query {
        return Query(versionListCriteria(packageId, name, stageTag, metadata))
            .apply {
                if (sortProperty.isNullOrBlank() || sortProperty == PackageVersion::name.name) {
                    with(Sort.by(Sort.Order(direction, TPackageVersion::ordinal.name)))
                } else {
                    with(Sort.by(Sort.Order(direction, sortProperty)))
                }
            }
    }

    fun versionLatestQuery(packageId: String): Query {
        return versionListQuery(packageId).limit(1)
    }

    fun versionQuery(packageId: String, versionList: List<String>): Query {
        val criteria = where(TPackageVersion::packageId).isEqualTo(packageId)
            .and(TPackageVersion::deleted).isEqualTo(null)
        if (versionList.isNotEmpty()) {
            criteria.and(TPackageVersion::name).inValues(versionList)
        }
        return Query(criteria)
    }


    fun packageDeleteUpdate(
        operator: String, recycle: Boolean, deleteTime: LocalDateTime = LocalDateTime.now(),
    ): Update {
        return Update()
            .set(TPackage::deleted.name, deleteTime)
            .set(TPackage::lastModifiedBy.name, operator)
            .set(TPackage::lastModifiedDate.name, deleteTime)
            .apply {
                if (recycle) {
                    push(TPackage::metadata.name, MetadataUtils.buildRecycleBinMetadata())
                }
            }
    }


    fun packageVersionDeleteUpdate(
        operator: String, recycle: Boolean, deleteTime: LocalDateTime = LocalDateTime.now(),
    ): Update {
        return Update()
            .set(TPackageVersion::deleted.name, deleteTime)
            .set(TPackageVersion::lastModifiedBy.name, operator)
            .set(TPackageVersion::lastModifiedDate.name, deleteTime)
            .apply {
                if (recycle) {
                    push(TPackageVersion::metadata.name, MetadataUtils.buildRecycleBinMetadata())
                }
            }
    }


    fun packageDeletedPointQuery(
        projectId: String, repoName: String, key: String? = null, deleted: LocalDateTime? = null,
    ): Query {
        val criteria = where(TPackage::projectId).isEqualTo(projectId)
            .and(TPackage::repoName).isEqualTo(repoName)
        key?.let {
            criteria.and(TPackage::key).isEqualTo(key)
        }
        if (deleted != null) {
            criteria.and(TPackage::deleted).isEqualTo(deleted)
        } else {
            criteria.and(TPackage::deleted).ne(null)
        }
        return Query(criteria).with(Sort.by(Sort.Direction.DESC, TPackage::deleted.name))
    }

    fun versionDeletedPointQuery(
        packageId: String, name: String? = null, deleted: LocalDateTime? = null,
    ): Query {
        val criteria = where(TPackageVersion::packageId).isEqualTo(packageId)
        name?.let { criteria.and(TPackageVersion::name).isEqualTo(name) }
        if (deleted != null) {
            criteria.and(TPackageVersion::deleted).isEqualTo(deleted)
        } else {
            criteria.and(TPackageVersion::deleted).ne(null)
        }
        return Query(criteria).with(Sort.by(Sort.Direction.DESC, TPackageVersion::deleted.name))
    }


    private fun packageListCriteria(projectId: String, repoName: String, packageName: String?): Criteria {
        return where(TPackage::projectId).isEqualTo(projectId)
            .and(TPackage::repoName).isEqualTo(repoName)
            .and(TPackage::deleted).isEqualTo(null)
            .apply {
                packageName?.let { and(TPackage::name).regex("^${EscapeUtils.escapeRegex(packageName)}", "i") }
            }
    }

    private fun versionListCriteria(
        packageId: String,
        name: String? = null,
        stageTag: List<String>? = null,
        metadata: List<MetadataModel>? = null,
    ): Criteria {
        return where(TPackageVersion::packageId).isEqualTo(packageId)
            .and(TPackageVersion::deleted).isEqualTo(null)
            .apply {
                name?.let { and(TPackageVersion::name).regex("^$it") }
            }.apply {
                if (!stageTag.isNullOrEmpty()) {
                    and(TPackageVersion::stageTag).all(stageTag)
                }
            }.apply {
                val criteriaList = mutableListOf<Criteria>()
                metadata?.forEach {
                    criteriaList.add(
                        where(TPackageVersion::metadata).elemMatch(
                            where(MetadataModel::key).isEqualTo(it.key).and(MetadataModel::value).isEqualTo(it.value)
                        )
                    )
                }
                if (criteriaList.isNotEmpty()) {
                    andOperator(criteriaList)
                }
            }
    }
}
