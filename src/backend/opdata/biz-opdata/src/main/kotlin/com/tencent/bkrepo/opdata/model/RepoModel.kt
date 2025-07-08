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

package com.tencent.bkrepo.opdata.model

import com.google.common.cache.CacheBuilder
import com.tencent.bkrepo.opdata.constant.OPDATA_PROJECT_ID
import com.tencent.bkrepo.opdata.constant.OPDATA_REPOSITORY
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class RepoModel @Autowired constructor(
    private var mongoTemplate: MongoTemplate
) {
    private val repoInfoCache = CacheBuilder.newBuilder()
        .maximumSize(DEFAULT_CACHE_SIZE)
        .expireAfterWrite(DEFAULT_CACHE_DURATION_SECONDS, TimeUnit.MINUTES)
        .build<String, RepoInfo>()

    fun getRepoListByProjectId(projectId: String): List<RepoInfo> {
        val query = Query(
            Criteria.where(OPDATA_PROJECT_ID).`is`(projectId)
        )
        return mongoTemplate.find(query, RepoInfo::class.java, OPDATA_REPOSITORY)
    }

    fun getRepoInfo(projectId: String, repoName: String): RepoInfo? {
        val cacheKey = "$projectId/$repoName"
        return repoInfoCache.getIfPresent(cacheKey) ?: run {
            val criteria = RepoInfo::projectId.isEqualTo(projectId)
                .and(RepoInfo::name.name).isEqualTo(repoName)
            mongoTemplate
                .findOne(Query(criteria), RepoInfo::class.java, OPDATA_REPOSITORY)
                ?.apply { repoInfoCache.put(cacheKey, this) }
        }
    }

    companion object {
        private const val DEFAULT_CACHE_SIZE = 1000L
        private const val DEFAULT_CACHE_DURATION_SECONDS = 5L
    }
}
