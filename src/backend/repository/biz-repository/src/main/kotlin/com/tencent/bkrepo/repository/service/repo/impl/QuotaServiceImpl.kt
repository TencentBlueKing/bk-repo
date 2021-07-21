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

package com.tencent.bkrepo.repository.service.repo.impl

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.repository.dao.RepositoryDao
import com.tencent.bkrepo.repository.model.TRepository
import com.tencent.bkrepo.repository.pojo.repo.RepoQuotaInfo
import com.tencent.bkrepo.repository.service.repo.QuotaService
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Service

/**
 * 仓库配额服务实现类
 */
@Service
class QuotaServiceImpl(
    private val repositoryDao: RepositoryDao
) : QuotaService {

    companion object {
        private const val KB = 1024.0
        private const val MB = 1048576.0
        private const val GB = 1073741824.0
        private const val TB = 1099511627776.0
        private const val PB = 1125899906842624.0
    }

    override fun getRepoQuotaInfo(projectId: String, repoName: String): RepoQuotaInfo {
        val tRepository = checkRepository(projectId, repoName)
        with(tRepository) {
            return RepoQuotaInfo(quota, used)
        }
    }

    override fun checkRepoQuota(projectId: String, repoName: String, inc: Long, dec: Long) {
        val decVolume = if (dec > 0) -dec else dec
        val tRepository = checkRepository(projectId, repoName)
        with(tRepository) {
            quota?.let {
                if (used!! + decVolume < 0 || used!! + inc + decVolume > it) {
                    throw ErrorCodeException(
                        ArtifactMessageCode.REPOSITORY_OVER_QUOTA,
                        name,
                        byteCountToDisplaySize(quota!!)
                    )
                }
            }
        }
    }

    override fun increaseUsedVolume(projectId: String, repoName: String, inc: Long) {
        incUpdateRepoUsedVolume(projectId, repoName, inc)
    }

    override fun decreaseUsedVolume(projectId: String, repoName: String, dec: Long) {
        val decVolume = if (dec > 0) -dec else dec
        incUpdateRepoUsedVolume(projectId, repoName, decVolume)
    }

    private fun incUpdateRepoUsedVolume(projectId: String, repoName: String, num: Long) {
        val query = buildQuery(projectId, repoName)
        val tRepository = checkRepository(projectId, repoName)
        tRepository.quota?.let {
            val update = Update().inc(TRepository::used.name, num)
            repositoryDao.upsert(query, update)
        }
    }

    /**
     * 构造单一仓库查询条件
     */
    private fun buildQuery(projectId: String, repoName: String): Query {
        val criteria = where(TRepository::projectId).isEqualTo(projectId)
            .and(TRepository::name).isEqualTo(repoName)
        return Query(criteria)
    }

    /**
     * 检查仓库是否存在，不存在则抛异常
     */
    private fun checkRepository(projectId: String, repoName: String): TRepository {
        val query = buildQuery(projectId, repoName)
        return repositoryDao.findOne(query)
            ?: throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, repoName)
    }

    /**
     * 仓库配额由字节单位转换成高可读性的
     */
    private fun byteCountToDisplaySize(quota: Long): String {
        val kb = quota.div(KB)
        val mb = quota.div(MB)
        val gb = quota.div(GB)
        val tb = quota.div(TB)
        val pb = quota.div(PB)

        return when {
            pb > 1 -> {
                String.format("%.2f PB", pb)
            }
            tb > 1 -> {
                String.format("%.2f TB", tb)
            }
            gb > 1 -> {
                String.format("%.2f GB", gb)
            }
            mb > 1 -> {
                String.format("%.2f MB", mb)
            }
            kb > 1 -> {
                String.format("%.2f KB", kb)
            }
            else -> {
                String.format("%d Bytes", quota)
            }
        }
    }
}