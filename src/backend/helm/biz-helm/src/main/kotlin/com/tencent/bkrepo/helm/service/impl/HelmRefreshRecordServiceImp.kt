/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 THL A29 Limited, a Tencent company.  All rights reserved.
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
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.helm.service.impl

import com.tencent.bkrepo.common.artifact.pojo.configuration.composite.CompositeConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.composite.ProxyChannelSetting
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.helm.constants.REPO_TYPE
import com.tencent.bkrepo.helm.dao.HelmRefreshRecordDao
import com.tencent.bkrepo.helm.pojo.record.THelmRefreshLog
import com.tencent.bkrepo.helm.service.HelmRefreshRecordService
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class HelmRefreshRecordServiceImp(
    private val helmRefreshRecordDao: HelmRefreshRecordDao,
    private val repositoryService: RepositoryService,
): HelmRefreshRecordService {

    override fun syncRecord(projectId: String, repoName: String) {
        val repoDetail = repositoryService.getRepoDetail(projectId, repoName, REPO_TYPE)
        val config = repoDetail?.configuration
        var proxyList: List<ProxyChannelSetting>? = null
        if (config is CompositeConfiguration) {
            proxyList = config.proxy.channelList
        }
        val list:List<THelmRefreshLog> = getList(projectId, repoName)
        for (record in list) {
            proxyList?.let {
                val hasValue = proxyList.any { it.name.equals(record.proxyChannelName)  }
                if (!hasValue) {
                    helmRefreshRecordDao.removeById(record.id!!)
                }
            } ?: run {
                helmRefreshRecordDao.removeById(record.id!!)
            }
        }

    }

    override fun add(repositoryDetail: RepositoryDetail, proxyChannelName: String, status: Boolean, errorMsg: String?) {
        with(repositoryDetail) {
            val query = Query()
            query.addCriteria(
                Criteria.where(THelmRefreshLog::projectId.name).`is`(projectId)
                    .and(THelmRefreshLog::repoName.name).`is`(name)
                    .and(THelmRefreshLog::proxyChannelName.name).`is`(proxyChannelName)
            )
            val update = Update().set(THelmRefreshLog::status.name, status)
                .set(THelmRefreshLog::createdAt.name, LocalDateTime.now())
                .set(THelmRefreshLog::errorMsg.name, errorMsg)
            helmRefreshRecordDao.upsert(query, update)
        }
    }


    override fun getByProjectIdAndRepoName(projectId: String, repoName: String): List<THelmRefreshLog> {
        return getList(projectId, repoName)
    }

    private fun getList(projectId: String, repoName: String):List<THelmRefreshLog> {
        val query = Query()
        query.addCriteria(
            Criteria.where(THelmRefreshLog::projectId.name).`is`(projectId)
                .and(THelmRefreshLog::repoName.name).`is`(repoName)
        )
        return helmRefreshRecordDao.find(query)
    }

}
