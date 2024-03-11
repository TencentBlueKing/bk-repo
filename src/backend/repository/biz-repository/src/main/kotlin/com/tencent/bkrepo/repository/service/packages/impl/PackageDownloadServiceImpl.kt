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

package com.tencent.bkrepo.repository.service.packages.impl

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.api.DefaultArtifactInfo
import com.tencent.bkrepo.common.artifact.constant.ARTIFACT_INFO_KEY
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.metadata.dao.PackageDao
import com.tencent.bkrepo.common.metadata.dao.PackageVersionDao
import com.tencent.bkrepo.common.metadata.service.packages.impl.PackageServiceImpl
import com.tencent.bkrepo.common.metadata.util.PackageEventFactory
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.repository.service.packages.PackageDownloadService
import org.springframework.stereotype.Service

@Service
class PackageDownloadServiceImpl(
    private val packageDao: PackageDao,
    private val packageVersionDao: PackageVersionDao
) : PackageDownloadService {
    override fun downloadVersion(
        projectId: String,
        repoName: String,
        packageKey: String,
        versionName: String,
        realIpAddress: String?
    ) {
        val tPackage = packageDao.findByKeyExcludeHistoryVersion(projectId, repoName, packageKey)
            ?: throw ErrorCodeException(ArtifactMessageCode.PACKAGE_NOT_FOUND, packageKey)
        val tPackageVersion = packageVersionDao.findByName(tPackage.id.orEmpty(), versionName)
            ?: throw ErrorCodeException(ArtifactMessageCode.VERSION_NOT_FOUND, versionName)
        if (tPackageVersion.artifactPath.isNullOrBlank()) {
            throw ErrorCodeException(CommonMessageCode.METHOD_NOT_ALLOWED, "artifactPath is null")
        }
        val artifactInfo = DefaultArtifactInfo(projectId, repoName, tPackageVersion.artifactPath!!)
        val context = ArtifactDownloadContext(artifact = artifactInfo, useDisposition = true)
        // 拦截package下载
        val packageVersion = PackageServiceImpl.Companion.convert(tPackageVersion)!!
        context.getPackageInterceptors().forEach { it.intercept(projectId, packageVersion) }
        // context 复制时会从request map中获取对应的artifactInfo， 而artifactInfo设置到map中是在接口url解析时
        HttpContextHolder.getRequestOrNull()?.setAttribute(ARTIFACT_INFO_KEY, artifactInfo)
        ArtifactContextHolder.getRepository().download(context)
        SpringContextUtils.publishEvent(
            PackageEventFactory.buildDownloadEvent(
                projectId = projectId,
                repoName = repoName,
                packageType = tPackage.type,
                packageKey = packageKey,
                packageName = tPackage.name,
                versionName = versionName,
                createdBy = SecurityUtils.getUserId(),
                realIpAddress = realIpAddress ?: HttpContextHolder.getClientAddress()
            )
        )
    }
}
