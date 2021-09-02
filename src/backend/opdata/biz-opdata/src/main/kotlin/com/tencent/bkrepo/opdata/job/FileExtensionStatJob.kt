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

package com.tencent.bkrepo.opdata.job

import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.opdata.model.NodeModel
import com.tencent.bkrepo.opdata.model.ProjectModel
import com.tencent.bkrepo.opdata.model.TFileExtensionMetrics
import com.tencent.bkrepo.opdata.repository.FileExtensionMetricsRepository
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class FileExtensionStatJob(
    val projectModel: ProjectModel,
    val nodeModel: NodeModel,
    val fileExtensionMetricsRepository: FileExtensionMetricsRepository
) {

    @Scheduled(cron = "00 45 00 * * ?")
    @SchedulerLock(name = "FileExtensionStatJob", lockAtMostFor = "PT1H")
    fun statFileExtension() {
        logger.info("start to stat file extension")
        val results = mutableListOf<TFileExtensionMetrics>()
        val projects = projectModel.getProjectList()
        projects.forEach { project ->
            val fileExtensions = nodeModel.getFileExtensions(project.name, null)
            fileExtensions.forEach {
                val fileExtensionStatInfo = nodeModel.getFileExtensionStat(project.name, it, null)
                val fileExtensionMetrics = with(fileExtensionStatInfo) {
                    TFileExtensionMetrics(projectId, repoName, extension, num, size)
                }
                results.add(fileExtensionMetrics)
            }
        }
        fileExtensionMetricsRepository.deleteAll()
        fileExtensionMetricsRepository.insert(results)
        logger.info("stat file extension done")
    }

    companion object {
        private val logger = LoggerHolder.jobLogger
    }
}
