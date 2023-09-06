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

package com.tencent.bkrepo.repository.dao

import com.mongodb.client.result.UpdateResult
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.repository.model.TPackage
import com.tencent.bkrepo.repository.util.PackageQueryHelper
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository

/**
 * 包数据访问层
 */
@Repository
class PackageDao : SimpleMongoDao<TPackage>() {

    /**
     * 当历史版本过多的情况下会导致拉取数据量过大，针对不需要历史版本字段的业务可选用该接口查询包信息
     */
    fun findByKeyExcludeHistoryVersion(projectId: String, repoName: String, key: String): TPackage? {
        if (key.isBlank()) {
            return null
        }
        val query = PackageQueryHelper.packageQuery(projectId, repoName, key)
        query.fields().exclude(HISTORY_VERSION)
        return this.findOne(query)
    }

    fun checkExist(projectId: String, repoName: String, key: String): Boolean {
        if (key.isBlank()) {
            return false
        }
        return this.exists(PackageQueryHelper.packageQuery(projectId, repoName, key))
    }

    fun findByKey(projectId: String, repoName: String, key: String): TPackage? {
        if (key.isBlank()) {
            return null
        }
        return this.findOne(PackageQueryHelper.packageQuery(projectId, repoName, key))
    }

    fun deleteByKey(projectId: String, repoName: String, key: String) {
        if (key.isNotBlank()) {
            this.remove(PackageQueryHelper.packageQuery(projectId, repoName, key))
        }
    }

    fun addClusterByKey(projectId: String, repoName: String, key: String, clusterName: String): UpdateResult? {
        return addClusterByKey(projectId, repoName, key, clusterName as Any)
    }

    fun addClusterByKey(projectId: String, repoName: String, key: String, clusterName: Set<String>): UpdateResult? {
        return addClusterByKey(projectId, repoName, key, clusterName as Any)
    }

    private fun addClusterByKey(projectId: String, repoName: String, key: String, clusterName: Any): UpdateResult? {
        if (key.isEmpty()) {
            return null
        }

        val query = PackageQueryHelper.packageQuery(projectId, repoName, key)
        val update = Update().addToSet(TPackage::clusterNames.name, clusterName)
        return this.updateFirst(query, update)
    }

    fun removeClusterByKey(projectId: String, repoName: String, key: String, clusterName: String): UpdateResult? {
        if (key.isEmpty()) {
            return null
        }

        val query = PackageQueryHelper.packageQuery(projectId, repoName, key)
        val update = Update().pull(TPackage::clusterNames.name, clusterName)
        return updateFirst(query, update)
    }

    fun decreaseVersions(packageId: String): TPackage? {
        val query = Query(Criteria.where(ID).isEqualTo(packageId))
        val update = Update().inc(TPackage::versions.name, -1)
        val options = FindAndModifyOptions()
        options.returnNew(true)
        return this.findAndModify(query, update, options, TPackage::class.java)
    }

    fun updateLatestVersion(packageId: String, latestVersion: String) {
        val query = Query(Criteria.where(ID).isEqualTo(packageId))
        val update = Update().set(TPackage::latest.name, latestVersion)
        this.updateFirst(query, update)
    }

    companion object {
        private const val HISTORY_VERSION = "historyVersion"
    }
}
