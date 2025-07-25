/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.archive.model

import com.tencent.bkrepo.archive.ArchiveStatus
import com.tencent.bkrepo.archive.constant.ArchiveStorageClass
import com.tencent.bkrepo.archive.model.TArchiveFile.Companion.ARCHIVE_KEY_SHA256_DEF
import com.tencent.bkrepo.archive.model.TArchiveFile.Companion.ARCHIVE_KEY_SHA256_IDX
import com.tencent.bkrepo.archive.model.TArchiveFile.Companion.SHA256_IDX
import com.tencent.bkrepo.archive.model.TArchiveFile.Companion.SHA256_IDX_DEF
import com.tencent.bkrepo.archive.model.TArchiveFile.Companion.STATUS_IDX
import com.tencent.bkrepo.archive.model.TArchiveFile.Companion.STATUS_IDX_DEF
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document("archive_file")
@CompoundIndexes(
    CompoundIndex(name = SHA256_IDX, def = SHA256_IDX_DEF, unique = true, background = true),
    CompoundIndex(name = STATUS_IDX, def = STATUS_IDX_DEF, background = true),
    CompoundIndex(name = ARCHIVE_KEY_SHA256_IDX, def = ARCHIVE_KEY_SHA256_DEF, background = true),
)
@Suppress("LongParameterList")
class TArchiveFile(
    id: String? = null,
    createdBy: String,
    createdDate: LocalDateTime,
    lastModifiedBy: String,
    lastModifiedDate: LocalDateTime,
    val sha256: String,
    val size: Long,
    var compressedSize: Long = -1,
    val storageCredentialsKey: String?,
    var status: ArchiveStatus,
    var archiver: String,
    var archiveCredentialsKey: String? = null,
    var storageClass: ArchiveStorageClass? = null,
) : AbstractEntity(
    id,
    createdBy,
    createdDate,
    lastModifiedBy,
    lastModifiedDate,
) {
    companion object {
        const val SHA256_IDX = "sha256_storageCredentialsKey_idx"
        const val SHA256_IDX_DEF = "{'sha256': 1,'storageCredentialsKey': 1}"
        const val ARCHIVE_KEY_SHA256_IDX = "archiveCredentialsKey_sha256_idx"
        const val ARCHIVE_KEY_SHA256_DEF = "{'archiveCredentialsKey': 1, 'sha256': 1}"
        const val STATUS_IDX = "status_idx"
        const val STATUS_IDX_DEF = "{'status': 1}"
    }
}
