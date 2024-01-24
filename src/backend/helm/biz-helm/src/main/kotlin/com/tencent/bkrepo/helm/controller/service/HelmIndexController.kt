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

package com.tencent.bkrepo.helm.controller.service

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.constant.ARTIFACT_INFO_KEY
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.helm.api.HelmClient
import com.tencent.bkrepo.helm.listener.base.RemoteEventJobExecutor
import com.tencent.bkrepo.helm.pojo.artifact.HelmDeleteArtifactInfo
import com.tencent.bkrepo.helm.service.ChartManipulationService
import com.tencent.bkrepo.helm.utils.ObjectBuilderUtil
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import org.springframework.web.bind.annotation.RestController

@RestController
class HelmIndexController(
    private val remoteEventJobExecutor: RemoteEventJobExecutor,
    private val chartManipulationService: ChartManipulationService
    ) : HelmClient {

    /**
     * refresh index.yaml and package info for remote
     */
    override fun refreshIndexYamlAndPackage(projectId: String, repoName: String): Response<Void> {
        val refreshEvent = ObjectBuilderUtil.buildRefreshEvent(projectId, repoName, SecurityUtils.getUserId())
        remoteEventJobExecutor.execute(refreshEvent)
        return ResponseBuilder.success()
    }

    /**
     * init index.yaml and package info for remote
     */
    override fun initIndexAndPackage(projectId: String, repoName: String): Response<Void> {
        val createEvent = ObjectBuilderUtil.buildCreatedEvent(projectId, repoName, SecurityUtils.getUserId())
        remoteEventJobExecutor.execute(createEvent)
        return ResponseBuilder.success()
    }

    override fun deleteVersion(
        projectId: String, repoName: String,
        packageName: String, version: String
    ): Response<Void> {
        val artifactInfo = HelmDeleteArtifactInfo(
            projectId = projectId,
            repoName = repoName,
            packageName = PackageKeys.ofHelm(packageName),
            version = version
        )
        HttpContextHolder.getRequestOrNull()?.setAttribute(ARTIFACT_INFO_KEY, artifactInfo)
        chartManipulationService.deleteVersion(SYSTEM_USER, artifactInfo)
        return ResponseBuilder.success()
    }
}
