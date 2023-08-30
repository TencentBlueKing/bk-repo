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

package com.tencent.bkrepo.ddc.utils

import com.tencent.bkrepo.common.api.constant.CharPool.SLASH
import com.tencent.bkrepo.ddc.pojo.Blob
import com.tencent.bkrepo.ddc.pojo.ContentHash
import com.tencent.bkrepo.ddc.pojo.RefId
import com.tencent.bkrepo.ddc.pojo.Reference
import com.tencent.bkrepo.ddc.pojo.ReferencedBy
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// ---reference---

const val NODE_METADATA_KEY_PREFIX = "ddc_"
const val NODE_METADATA_KEY_INLINE_BLOB = "${NODE_METADATA_KEY_PREFIX}inline_blob"
const val NODE_METADATA_KEY_FINALIZED = "${NODE_METADATA_KEY_PREFIX}finalized"

fun NodeDetail.inlineBlob() = metadata[NODE_METADATA_KEY_INLINE_BLOB] as ByteArray?

fun NodeDetail.inlineBlobId() = metadata[NODE_METADATA_KEY_BLOB_ID] as String?

fun NodeDetail.finalized() = metadata[NODE_METADATA_KEY_FINALIZED] as Boolean? ?: false

fun NodeDetail.toReference() = Reference(
    projectId = projectId,
    repoName = repoName,
    bucket = path.trim(SLASH),
    key = RefId.create(name),
    lastAccessDate = LocalDateTime.parse(lastAccessDate, DateTimeFormatter.ISO_DATE_TIME),
    blobId = inlineBlobId()?.let { ContentHash.fromHex(it) },
    finalized = finalized(),
//    inlineBlob = inlineBlob()
)

fun Reference.fullPath() = "/$bucket/$key"

// ---blob---

const val NODE_METADATA_KEY_BLOB_ID = "${NODE_METADATA_KEY_PREFIX}blob_id"
const val NODE_METADATA_KEY_CONTENT_ID = "${NODE_METADATA_KEY_PREFIX}content_id"
const val NODE_METADATA_KEY_REFERENCES = "${NODE_METADATA_KEY_PREFIX}references"
const val REFERENCE_KEY = "key"
const val REFERENCE_BUCKET = "bucket"
val NODE_TO_BLOB_SELECT = arrayOf(
    NodeDetail::projectId.name,
    NodeDetail::repoName.name,
    NodeDetail::sha256.name,
    NodeDetail::fullPath.name,
    NodeDetail::size.name,
    NodeDetail::metadata.name
)

fun Map<String, Any?>.toBlob(): Blob {
    val metadata = get(NodeDetail::metadata.name) as Map<String, Any>
    val references = (metadata[NODE_METADATA_KEY_REFERENCES] as Set<Map<String, String>>?)?.mapTo(HashSet()) {
        val refId = RefId(it[REFERENCE_KEY] as String)
        ReferencedBy(refId, it[REFERENCE_BUCKET] as String)
    }
    return Blob(
        projectId = get(NodeDetail::projectId.name) as String,
        repoName = get(NodeDetail::repoName.name) as String,
        sha256 = get(NodeDetail::sha256.name) as String,
        fullPath = get(NodeDetail::fullPath.name) as String,
        size = get(NodeDetail::size.name).toString().toLong(),
        blobId = ContentHash.fromHex(metadata[NODE_METADATA_KEY_BLOB_ID] as String),
        contentId = metadata[NODE_METADATA_KEY_CONTENT_ID]?.let { ContentHash.fromHex(it as String) },
        references = references
    )
}
