/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
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

import com.mongodb.client.result.UpdateResult
import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.model.TPackage
import com.tencent.bkrepo.common.metadata.util.PackageQueryHelper
import com.tencent.bkrepo.common.metadata.util.TagUtils
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import org.springframework.context.annotation.Conditional
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
@Conditional(SyncCondition::class)
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

    fun addTag(packageId: String, versionName: String, tag: String) {
        // tag中包含.$，不在mongodb允许的key值内需要转换
        val encodeTag = TagUtils.encodeTag(tag)
        val query = Query(Criteria.where(ID).isEqualTo(packageId))
        val update = Update().set("${TPackage::versionTag.name}.$encodeTag", versionName)
        this.updateFirst(query, update)
    }

    fun removeTag(packageId: String, tag: String) {
        val encodeTag = TagUtils.encodeTag(tag)
        val query = Query(Criteria.where(ID).isEqualTo(packageId))
        val update = Update().unset("${TPackage::versionTag.name}.$encodeTag")
        this.updateFirst(query, update)
    }

    /**
     * 向指定 package 的 historyVersion 追加一批版本名（去重语义，等价 `$addToSet.$each`）。
     *
     * 屏蔽 Spring Data MongoDB `AddToSetBuilder.each(Object...)` 的 vararg 陷阱：
     * Kotlin 侧必须用 spread(`*`) 展开成独立元素，否则整个 Collection 会被当作单个数组元素塞进
     * `$each`，生产端 historyVersion 会被写入错误结构。此处已在 DAO 内一次性处理，业务方无需感知。
     *
     * @return true 表示 mongo 侧发生了实际写入（modifiedCount > 0）
     */
    fun appendHistoryVersions(packageId: String, names: Collection<String>): Boolean {
        if (names.isEmpty()) return false
        val query = Query(Criteria.where(ID).isEqualTo(packageId))
        // toTypedArray() 产生独立数组副本，spread 展开为 vararg 独立元素，两个问题一次解决
        val update = Update().addToSet(TPackage::historyVersion.name).each(*names.toTypedArray())
        return this.updateFirst(query, update).modifiedCount > 0
    }

    /**
     * 从指定 package 的 historyVersion 移除一批版本名（等价 `$pullAll`）。
     *
     * @return true 表示 mongo 侧发生了实际写入（modifiedCount > 0）
     */
    fun removeHistoryVersions(packageId: String, names: Collection<String>): Boolean {
        if (names.isEmpty()) return false
        val query = Query(Criteria.where(ID).isEqualTo(packageId))
        val update = Update().pullAll(TPackage::historyVersion.name, names.toTypedArray())
        return this.updateFirst(query, update).modifiedCount > 0
    }

    companion object {
        private const val HISTORY_VERSION = "historyVersion"
    }
}
