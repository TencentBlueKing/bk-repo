/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.helm.listener.operation

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.helm.pojo.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.service.impl.AbstractChartService
import com.tencent.bkrepo.helm.utils.ObjectBuilderUtil.buildFileAndNodeCreateRequest
import com.tencent.bkrepo.helm.utils.TimeFormatUtil
import com.tencent.bkrepo.common.api.constant.SYSTEM_USER
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.util.StopWatch

class IndexRefreshTask(
    private val projectId: String,
    private val repoName: String,
    private val chartService: AbstractChartService,
    private val lock: Any
): Runnable {

    override fun run() {

        try {
            val stopWatch = StopWatch(
                "Handling event for refreshing index.yaml in repo [$projectId/$repoName]"
            )
            stopWatch.start()
            val artifactInfo = HelmArtifactInfo(projectId, repoName, StringPool.EMPTY)
            val helmIndexYamlMetadata = chartService.regenerateHelmIndexYaml(artifactInfo)
            logger.info("index.yaml in repo [$projectId/$repoName] is ready to upload...")
            val (artifactFile, nodeCreateRequest) = buildFileAndNodeCreateRequest(
                helmIndexYamlMetadata, projectId, repoName, SYSTEM_USER
            )
            chartService.uploadIndexYamlMetadata(artifactFile, nodeCreateRequest)
            logger.info(
                "Index.yaml has been refreshed in repo [$projectId/$repoName] !"
            )
            chartService.helmChartEventRecordDao.updateIndexRefreshTimeByProjectIdAndRepoName(
                projectId, repoName, TimeFormatUtil.convertToLocalTime(helmIndexYamlMetadata.generated)
            )
            stopWatch.stop()
            logger.info(
                "Total cost for refreshing index.yaml" +
                    "in repo [$projectId/$repoName] is: ${stopWatch.totalTimeSeconds}s"
            )
        } catch (e: Exception) {
            logger.error(
                "Error [$e] occurred while refreshing index.yaml by" +
                    " in repo [$projectId/$repoName] !"
            )
            throw e
        } finally {
            chartService.unlock(projectId, repoName, lock)
        }

    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(IndexRefreshTask::class.java)
    }
}