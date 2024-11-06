/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.ddc.service

import com.tencent.bkrepo.common.api.exception.BadRequestException
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.ddc.artifact.ReferenceArtifactInfo
import org.springframework.stereotype.Service

@Service
class ReferenceArtifactService(
    private val referenceService: ReferenceService,
) : ArtifactService() {
    fun downloadRef(artifactInfo: ReferenceArtifactInfo) {
        repository.download(ArtifactDownloadContext())
    }

    fun createRef(artifactInfo: ReferenceArtifactInfo, file: ArtifactFile) {
        repository.upload(ArtifactUploadContext(file))
    }

    fun deleteRef(artifactInfo: ReferenceArtifactInfo) {
        with(artifactInfo) {
            referenceService.deleteReference(projectId, repoName, bucket, refKey.toString(), legacy)
        }
    }

    fun finalize(artifactInfo: ReferenceArtifactInfo) {
        with(artifactInfo) {
            val ref = referenceService.getReference(
                projectId, repoName, bucket, refKey.toString(), checkFinalized = false
            ) ?: throw BadRequestException(CommonMessageCode.PARAMETER_INVALID, "No blob when attempting to finalize")
            if (ref.blobId!!.toString() != artifactInfo.inlineBlobHash) {
                throw ErrorCodeException(ArtifactMessageCode.DIGEST_CHECK_FAILED, "blake3")
            }
            val res = referenceService.finalize(ref, ref.inlineBlob!!)
            HttpContextHolder.getResponse().writer.println(res.toJsonString())
        }
    }
}
