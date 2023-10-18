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

package com.tencent.bkrepo.ddc.pojo

import com.tencent.bkrepo.ddc.artifact.ReferenceArtifactInfo
import com.tencent.bkrepo.ddc.model.TDdcLegacyRef
import com.tencent.bkrepo.ddc.model.TDdcRef
import java.time.LocalDateTime

data class Reference(
    val projectId: String,
    val repoName: String,
    val bucket: String,
    val key: RefKey,
    val finalized: Boolean? = null,
    val lastAccessDate: LocalDateTime? = null,
    var blobId: ContentHash? = null,
    var inlineBlob: ByteArray? = null,
    var legacy: Boolean = false,
) {
    fun fullPath() = "/$bucket/$key"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Reference

        if (repoName != other.repoName) return false
        if (bucket != other.bucket) return false
        if (key != other.key) return false
        if (lastAccessDate != other.lastAccessDate) return false
        if (blobId != other.blobId) return false
        if (finalized != other.finalized) return false
        if (inlineBlob != null) {
            if (other.inlineBlob == null) return false
            if (!inlineBlob.contentEquals(other.inlineBlob)) return false
        } else if (other.inlineBlob != null) return false
        if (legacy != other.legacy) return false

        return true
    }

    override fun hashCode(): Int {
        var result = repoName.hashCode()
        result = 31 * result + bucket.hashCode()
        result = 31 * result + key.hashCode()
        result = 31 * result + lastAccessDate.hashCode()
        result = 31 * result + blobId.hashCode()
        result = 31 * result + finalized.hashCode()
        result = 31 * result + (inlineBlob?.contentHashCode() ?: 0)
        result = 31 * result + legacy.hashCode()
        return result
    }

    companion object {
        fun from(ref: TDdcRef) = Reference(
            projectId = ref.projectId,
            repoName = ref.repoName,
            bucket = ref.bucket,
            key = RefKey.create(ref.key),
            lastAccessDate = ref.lastAccessDate,
            blobId = ContentHash.fromHex(ref.blobId),
            finalized = ref.finalized,
            inlineBlob = ref.inlineBlob?.data
        )

        fun from(ref: TDdcLegacyRef) = Reference(
            projectId = ref.projectId,
            repoName = ref.repoName,
            bucket = ref.bucket,
            key = RefKey.create(ref.key, true),
            lastAccessDate = ref.lastAccessDate,
            blobId = ContentHash.fromHex(ref.contentHash),
            finalized = true,
            inlineBlob = null,
            legacy = true
        )

        fun from(artifactInfo: ReferenceArtifactInfo, payload: ByteArray) = with(artifactInfo) {
            Reference(
                projectId = projectId,
                repoName = repoName,
                bucket = bucket,
                key = refKey,
                finalized = false,
                blobId = ContentHash.fromHex(inlineBlobHash!!),
                inlineBlob = payload,
            )
        }
    }
}
