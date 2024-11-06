/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.s3.artifact

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.metrics.ArtifactMetrics
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.s3.artifact.utils.ContextUtil
import com.tencent.bkrepo.s3.constant.INTERNAL_ERROR
import com.tencent.bkrepo.s3.constant.NO_SUCH_KEY
import com.tencent.bkrepo.s3.constant.S3HttpHeaders
import com.tencent.bkrepo.s3.constant.S3MessageCode
import com.tencent.bkrepo.s3.exception.S3InternalException
import com.tencent.bkrepo.s3.exception.S3NotFoundException
import org.springframework.stereotype.Component

@Component
class S3LocalRepository: LocalRepository() {

    /**
     * 读取文件内容
     */
    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        val resource = super.onDownload(context)
        if (resource == null) {
            throw S3NotFoundException(
                HttpStatus.NOT_FOUND,
                S3MessageCode.S3_NO_SUCH_KEY,
                params = arrayOf(NO_SUCH_KEY, context.artifactInfo.getArtifactFullPath())
            )
        }
        return resource
    }

    override fun onDownloadFailed(context: ArtifactDownloadContext, exception: Exception) {
        ArtifactMetrics.getDownloadFailedCounter().increment()
        if (exception is S3NotFoundException) {
            throw exception
        } else {
            throw S3InternalException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                CommonMessageCode.SYSTEM_ERROR,
                params = arrayOf(INTERNAL_ERROR, context.artifactInfo.getArtifactFullPath())
            )
        }
    }

    override fun onUpload(context: ArtifactUploadContext) {
        with(context) {
            val nodeCreateRequest = buildNodeCreateRequest(this).copy(overwrite = true)
            storageManager.storeArtifactFile(nodeCreateRequest, getArtifactFile(), storageCredentials)
        }
    }

    override fun onUploadFinished(context: ArtifactUploadContext) {
        super.onUploadFinished(context)
        val response = HttpContextHolder.getResponse()
        response.setHeader(S3HttpHeaders.X_AMZ_REQUEST_ID, ContextUtil.getTraceId())
        response.setHeader(S3HttpHeaders.X_AMZ_TRACE_ID, ContextUtil.getTraceId())
    }

}
