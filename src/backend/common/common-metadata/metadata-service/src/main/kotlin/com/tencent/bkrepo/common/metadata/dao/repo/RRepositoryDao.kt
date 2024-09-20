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

package com.tencent.bkrepo.common.metadata.dao.repo

import com.tencent.bkrepo.common.metadata.condition.ReactiveCondition
import com.tencent.bkrepo.common.metadata.model.TRepository
import com.tencent.bkrepo.common.metadata.util.RepoQueryHelper.buildSingleQuery
import com.tencent.bkrepo.common.mongo.reactive.dao.SimpleMongoReactiveDao
import org.springframework.context.annotation.Conditional
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository

/**
 * 仓库数据访问层
 */
@Repository
@Conditional(ReactiveCondition::class)
class RRepositoryDao : SimpleMongoReactiveDao<TRepository>() {

    /**
     * 根据项目[projectId]、名称[name]和类型[type]查询仓库
     */
    suspend fun findByNameAndType(projectId: String, name: String, type: String? = null): TRepository? {
        val query = buildSingleQuery(projectId, name, type)
        return this.findOne(query, TRepository::class.java)
    }

    /**
     * 根据[id]删除仓库
     */
    suspend fun deleteById(id: String?) {
        if (id.isNullOrBlank()) {
            return
        }
        val query = Query(TRepository::id.isEqualTo(id))
        this.remove(query)
    }

    /**
     * 查询是否有使用指定存储凭证的仓库
     */
    suspend fun existsByCredentialsKey(credentialsKey: String): Boolean {
        val query = Query(TRepository::credentialsKey.isEqualTo(credentialsKey))
            .addCriteria(TRepository::deleted.isEqualTo(null))
        return this.exists(query)
    }



    suspend fun unsetOldCredentialsKey(projectId: String, repoName: String, repoType: String? = null) {
        val query = buildSingleQuery(projectId, repoName, repoType)
        val update = Update().unset(TRepository::oldCredentialsKey.name)
        updateFirst(query, update)
    }
}
