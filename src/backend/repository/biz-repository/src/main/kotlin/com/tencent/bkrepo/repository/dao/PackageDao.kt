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
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository

/**
 * 包数据访问层
 */
@Repository
class PackageDao : SimpleMongoDao<TPackage>() {

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
}
