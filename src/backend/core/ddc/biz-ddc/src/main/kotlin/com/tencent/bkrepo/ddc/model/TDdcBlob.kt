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

package com.tencent.bkrepo.ddc.model

import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document("ddc_blob")
@CompoundIndexes(
    CompoundIndex(
        name = "projectId_repoName_blobId_idx",
        def = "{'projectId': 1, 'repoName': 1, 'blobId': 1}",
        unique = true,
        background = true
    ),
    CompoundIndex(
        name = "projectId_repoName_contentId_idx",
        def = "{'projectId': 1, 'repoName': 1, 'contentId': 1}",
        background = true
    ),
    CompoundIndex(
        name = "projectId_repoName_references_idx",
        def = "{'projectId': 1, 'repoName': 1, 'references': 1}",
        background = true
    )
)
data class TDdcBlob(
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,

    var projectId: String,
    var repoName: String,
    /**
     * blob blake3 hash
     */
    var blobId: String,
    /**
     * 压缩前的blob blake3 hash，如果blob未压缩则contentId与blobId相等
     */
    var contentId: String,
    /**
     * blob sha256
     */
    var sha256: String,
    /**
     * 仅再legacy ref引用的blob中存在值
     */
    var sha1: String? = null,
    /**
     * blob size
     */
    var size: Long,
    /**
     * 引用了该blob的ref或blob，ref的inline blob中直接或间接引用的所有blob都会关联到ref
     * ref类型引用 ref/{bucket}/{key}
     * blob类型引用 blob/{blobId}
     */
    var references: Set<String> = emptySet()
)
