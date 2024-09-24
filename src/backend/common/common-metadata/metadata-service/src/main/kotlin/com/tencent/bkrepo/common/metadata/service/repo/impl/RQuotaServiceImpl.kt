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

package com.tencent.bkrepo.common.metadata.service.repo.impl

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.metadata.condition.ReactiveCondition
import com.tencent.bkrepo.common.metadata.dao.repo.RRepositoryDao
import com.tencent.bkrepo.common.metadata.model.TRepository
import com.tencent.bkrepo.common.metadata.service.repo.RQuotaService
import com.tencent.bkrepo.common.metadata.util.QuotaHelper
import com.tencent.bkrepo.common.metadata.util.RepoQueryHelper
import com.tencent.bkrepo.repository.pojo.repo.RepoQuotaInfo
import org.springframework.context.annotation.Conditional
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service

/**
 * 仓库配额服务实现类
 */
@Service
@Conditional(ReactiveCondition::class)
class RQuotaServiceImpl(
    private val repositoryDao: RRepositoryDao,
) : RQuotaService {

    override suspend fun getRepoQuotaInfo(projectId: String, repoName: String): RepoQuotaInfo {
        val tRepository = checkRepository(projectId, repoName)
        with(tRepository) {
            return RepoQuotaInfo(quota, used)
        }
    }

    override suspend fun checkRepoQuota(projectId: String, repoName: String, change: Long) {
        val tRepository = checkRepository(projectId, repoName)
        QuotaHelper.checkQuota(tRepository, change)
    }

    override suspend fun increaseUsedVolume(projectId: String, repoName: String, inc: Long) {
        incUpdateRepoUsedVolume(projectId, repoName, inc)
    }

    override suspend fun decreaseUsedVolume(projectId: String, repoName: String, dec: Long) {
        val decVolume = if (dec > 0) -dec else dec
        incUpdateRepoUsedVolume(projectId, repoName, decVolume)
    }

    private suspend fun incUpdateRepoUsedVolume(projectId: String, repoName: String, num: Long) {
        val query = RepoQueryHelper.buildSingleQuery(projectId, repoName)
        val tRepository = checkRepository(projectId, repoName)
        tRepository.quota?.let {
            val update = Update().inc(TRepository::used.name, num)
            repositoryDao.upsert(query, update)
        }
    }

    /**
     * 检查仓库是否存在，不存在则抛异常
     */
    private suspend fun checkRepository(projectId: String, repoName: String): TRepository {
        val query = RepoQueryHelper.buildSingleQuery(projectId, repoName)
        return repositoryDao.findOne(query)
            ?: throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, repoName)
    }
}
