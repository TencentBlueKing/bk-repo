/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLI
 */

package com.tencent.bkrepo.common.metadata.dao.repo

import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.metadata.condition.ReactiveCondition
import com.tencent.bkrepo.common.metadata.model.TProxyChannel
import com.tencent.bkrepo.common.metadata.util.ProxyChannelQueryHelper.buildSingleQuery
import com.tencent.bkrepo.common.mongo.reactive.dao.SimpleMongoReactiveDao
import org.springframework.context.annotation.Conditional
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component

@Component
@Conditional(ReactiveCondition::class)
class RProxyChannelDao : SimpleMongoReactiveDao<TProxyChannel>() {
    /**
     * 查找代理
     */
    suspend fun findByUniqueParams(
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
    suspend fun findByProjectIdAndRepo(
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
    suspend fun findByRepoType(repoType: RepositoryType): List<TProxyChannel> {
        val query = Query(TProxyChannel::repoType.isEqualTo(repoType))
        return this.find(query)
    }

    /**
     * 删除仓库
     */
    suspend fun deleteByUnique(
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
}
