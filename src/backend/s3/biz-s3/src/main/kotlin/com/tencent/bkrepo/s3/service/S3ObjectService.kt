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

package com.tencent.bkrepo.s3.service

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.S3ErrorTypes
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.S3NotFoundException
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.s3.artifact.S3LocalRepository
import com.tencent.bkrepo.s3.artifact.S3ObjectArtifactInfo
import com.tencent.bkrepo.s3.artifact.configuration.AutoIndexRepositorySettings
import com.tencent.bkrepo.s3.constant.S3MessageCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * S3对象服务类
 */
@Service
class S3ObjectService(
    private val localRepository: S3LocalRepository,
    private val repositoryClient: RepositoryClient,
) : ArtifactService() {

    fun getObject(artifactInfo: S3ObjectArtifactInfo) {
        with(artifactInfo) {
            val node = ArtifactContextHolder.getNodeDetail(artifactInfo) ?:
                throw S3NotFoundException(
                    HttpStatus.NOT_FOUND,
                    S3MessageCode.S3_NO_SUCH_KEY,
                    arrayOf(artifactInfo.getArtifactFullPath(),S3ErrorTypes.NO_SUCH_KEY)
                )
            val repo = repositoryClient.getRepoDetail(projectId, repoName).data
                ?: throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, repoName)
            val context = ArtifactDownloadContext(repo = repo, artifact = artifactInfo)
            //仓库未开启自动创建目录索引时不允许访问目录
            val autoIndexSettings = AutoIndexRepositorySettings.from(context.repositoryDetail.configuration)
            if (node.folder && autoIndexSettings?.enabled == false) {
                logger.warn("${artifactInfo.getArtifactFullPath()} is folder " +
                        "or repository is not enabled for automatic directory index creation")
                throw S3NotFoundException(
                    HttpStatus.NOT_FOUND,
                    S3MessageCode.S3_NO_SUCH_KEY,
                    arrayOf(artifactInfo.getArtifactFullPath(),S3ErrorTypes.NO_SUCH_KEY)
                )
            }
            localRepository.download(context)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(S3ObjectService::class.java)
    }
}
