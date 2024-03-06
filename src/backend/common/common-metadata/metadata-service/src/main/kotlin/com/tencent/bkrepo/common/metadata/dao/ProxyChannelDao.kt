/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 *  A copy of the MIT License is included in this file.
 *
 *
 *  Terms of the MIT License:
 *  ---------------------------------------------------
 *  Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 *  documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 *  rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 *  permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 *  the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 *  LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 *  NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 *  WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 *  SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.metadata.dao

import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.common.metadata.model.TProxyChannel
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Repository

/**
 * 项目数据访问层
 */
@Repository
class ProxyChannelDao : SimpleMongoDao<TProxyChannel>() {
    /**
     * 查找代理
     */
    fun findByUniqueParams(
        projectId: String,
        repoName: String,
        repoType: RepositoryType,
        name: String
    ): TProxyChannel? {
        val query = buildSingleQuery(
            projectId = projectId,
            repoName = repoName,
            repoType = repoType.name,
            name = name
        )
        return this.findOne(query, TProxyChannel::class.java)
    }

    /**
     * 查找代理
     */
    fun findByProjectIdAndRepo(
        projectId: String,
        repoName: String,
        repoType: RepositoryType
    ): List<TProxyChannel> {
        val query = buildSingleQuery(
            projectId = projectId,
            repoName = repoName,
            repoType = repoType.name
        )
        return this.find(query)
    }

    /**
     * 根据类型查找项目
     */
    fun findByRepoType(repoType: RepositoryType): List<TProxyChannel> {
        val query = Query(TProxyChannel::repoType.isEqualTo(repoType))
        return this.find(query)
    }

    /**
     * 删除仓库
     */
    fun deleteByUnique(
        projectId: String,
        repoName: String,
        repoType: RepositoryType,
        name: String
    ) {
        val query = buildSingleQuery(
            projectId = projectId,
            repoName = repoName,
            repoType = repoType.name,
            name = name
        )
        this.remove(query)
    }

    /**
     * 构造查询条件
     */
    private fun buildSingleQuery(
        projectId: String,
        repoName: String,
        repoType: String,
        name: String? = null
    ): Query {
        val criteria = where(TProxyChannel::projectId).isEqualTo(projectId)
            .and(TProxyChannel::repoName).isEqualTo(repoName)
            .and(TProxyChannel::repoType).isEqualTo(repoType)
            .apply {
                name?.let {
                    this.and(TProxyChannel::name).isEqualTo(name)
                }
            }
        return Query(criteria)
    }
}
