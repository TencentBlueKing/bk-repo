/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.ddc.model

import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/**
 * DDC Ref,用于兼容UE4.26-4.27
 */
@Document("ddc_legacy_ref")
@CompoundIndexes(
    CompoundIndex(
        name = "projectId_repoName_bucket_key_idx",
        def = "{'projectId': 1, 'repoName': 1, 'bucket': 1, 'key': 1}",
        unique = true,
        background = true
    )
)
class TDdcLegacyRef(
    createdBy: String,
    createdDate: LocalDateTime,
    lastModifiedBy: String,
    lastModifiedDate: LocalDateTime,
    lastAccessDate: LocalDateTime,
    projectId: String,
    repoName: String,
    bucket: String,
    key: String,

    /**
     * 实际存放缓存的blob的id
     */
    var contentHash: String,
): TDdcRefBase(
    createdBy = createdBy,
    createdDate = createdDate,
    lastModifiedBy = lastModifiedBy,
    lastModifiedDate = lastModifiedDate,
    lastAccessDate = lastAccessDate,
    projectId = projectId,
    repoName = repoName,
    bucket = bucket,
    key = key
)
