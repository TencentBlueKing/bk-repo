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

package com.tencent.bkrepo.rpm.job

import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.rpm.pojo.IndexType
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PrimaryJob(
    private val repositoryClient: RepositoryClient,
    private val jobService: JobService
) {

    // 每次任务间隔 ms
    @Scheduled(fixedDelay = 2000)
    @SchedulerLock(name = "PrimaryJob", lockAtMostFor = "PT10M")
    fun checkPrimaryXml() {
        val startMillis = System.currentTimeMillis()
        val repoList = repositoryClient.pageByType(0, 100, "RPM").data?.records

        repoList?.let {
            for (repo in repoList) {
                val rpmConfiguration = repo.configuration
                val repodataDepth = rpmConfiguration.getIntegerSetting("repodataDepth") ?: 0
                val targetSet = mutableSetOf<String>()
                jobService.findRepoDataByRepo(repo, "/", repodataDepth, targetSet)
                for (repoDataPath in targetSet) {
                    logger.info("sync primary (${repo.projectId}|${repo.name}|$repoDataPath) start")
                    jobService.syncIndex(repo, repoDataPath, IndexType.PRIMARY)
                    logger.info("sync primary (${repo.projectId}|${repo.name}|$repoDataPath) done, cost time: ${System.currentTimeMillis() - startMillis} ms")
                }
            }
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(PrimaryJob::class.java)
    }
}
