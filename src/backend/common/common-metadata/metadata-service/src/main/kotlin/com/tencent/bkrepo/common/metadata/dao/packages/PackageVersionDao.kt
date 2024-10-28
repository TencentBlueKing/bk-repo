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

package com.tencent.bkrepo.common.metadata.dao.packages

import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.common.metadata.model.TPackageVersion
import com.tencent.bkrepo.common.metadata.util.PackageQueryHelper
import org.springframework.context.annotation.Conditional
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository

/**
 * 包版本 DAO
 */
@Repository
@Conditional(SyncCondition::class)
class PackageVersionDao : SimpleMongoDao<TPackageVersion>() {

    fun listByPackageId(packageId: String): List<TPackageVersion> {
        return this.find(PackageQueryHelper.versionListQuery(packageId))
    }

    fun findByTag(packageId: String, tag: String): TPackageVersion? {
        return this.findOne(PackageQueryHelper.versionQuery(packageId, tag = tag))
    }

    fun findByName(packageId: String, name: String): TPackageVersion? {
        return this.findOne(PackageQueryHelper.versionQuery(packageId, name = name))
    }

    fun deleteByPackageId(packageId: String) {
        this.remove(PackageQueryHelper.versionQuery(packageId))
    }

    /**
     * PackageVersion的clusterName只有一个值时，可根据[packageId]与[clusterName]删除所有关联的PackageVersion
     */
    fun deleteByPackageIdAndClusterName(packageId: String, clusterName: String) {
        this.remove(PackageQueryHelper.clusterNameQuery(packageId, clusterName))
    }

    /**
     * 版本下的多个制品都删除时，再删除版本
     */
    fun deleteByNameAndPath(packageId: String, name: String, path: String?): Boolean {
        if (path == null) {
            this.remove(PackageQueryHelper.versionQuery(packageId, name = name))
            return true
        } else {
            val query = PackageQueryHelper.versionQuery(packageId, name = name)
            val update = Update().pull(TPackageVersion::artifactPaths.name, path)
            val option = FindAndModifyOptions()
            option.returnNew(true)
            val tPackageVersion = this.findAndModify(query, update, option, TPackageVersion::class.java) ?: return true
            if (tPackageVersion.artifactPaths.isNullOrEmpty()) {
                this.remove(query)
                return true
            }
            return false
        }

    }

    fun findLatest(packageId: String): TPackageVersion? {
        val query = PackageQueryHelper.versionLatestQuery(packageId)
        return this.findOne(query)
    }

    fun countVersion(packageId: String): Long {
        return this.count(PackageQueryHelper.versionQuery(packageId))
    }
}
