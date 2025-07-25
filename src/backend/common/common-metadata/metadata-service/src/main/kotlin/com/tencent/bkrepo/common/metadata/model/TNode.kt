/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.metadata.model

import com.tencent.bkrepo.common.api.mongo.ShardingDocument
import com.tencent.bkrepo.common.api.mongo.ShardingKey
import com.tencent.bkrepo.repository.constant.SHARDING_COUNT
import com.tencent.bkrepo.common.metadata.model.TNode.Companion.ARCHIVED_IDX
import com.tencent.bkrepo.common.metadata.model.TNode.Companion.ARCHIVED_IDX_DEF
import com.tencent.bkrepo.common.metadata.model.TNode.Companion.CLUSTER_NAMES_IDX
import com.tencent.bkrepo.common.metadata.model.TNode.Companion.CLUSTER_NAMES_IDX_DEF
import com.tencent.bkrepo.common.metadata.model.TNode.Companion.COMPRESSED_IDX
import com.tencent.bkrepo.common.metadata.model.TNode.Companion.COMPRESSED_IDX_DEF
import com.tencent.bkrepo.common.metadata.model.TNode.Companion.COPY_FROM_IDX
import com.tencent.bkrepo.common.metadata.model.TNode.Companion.COPY_FROM_IDX_DEF
import com.tencent.bkrepo.common.metadata.model.TNode.Companion.FOLDER_IDX
import com.tencent.bkrepo.common.metadata.model.TNode.Companion.FOLDER_IDX_DEF
import com.tencent.bkrepo.common.metadata.model.TNode.Companion.FULL_PATH_IDX
import com.tencent.bkrepo.common.metadata.model.TNode.Companion.FULL_PATH_IDX_DEF
import com.tencent.bkrepo.common.metadata.model.TNode.Companion.METADATA_IDX
import com.tencent.bkrepo.common.metadata.model.TNode.Companion.METADATA_IDX_DEF
import com.tencent.bkrepo.common.metadata.model.TNode.Companion.PATH_IDX
import com.tencent.bkrepo.common.metadata.model.TNode.Companion.PATH_IDX_DEF
import com.tencent.bkrepo.common.metadata.model.TNode.Companion.SHA256_IDX
import com.tencent.bkrepo.common.metadata.model.TNode.Companion.SHA256_IDX_DEF
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import java.time.LocalDateTime

/**
 * 资源模型
 */
@ShardingDocument("node")
@CompoundIndexes(
    CompoundIndex(name = FULL_PATH_IDX, def = FULL_PATH_IDX_DEF, unique = true, background = true),
    CompoundIndex(name = PATH_IDX, def = PATH_IDX_DEF, background = true),
    CompoundIndex(name = METADATA_IDX, def = METADATA_IDX_DEF, background = true),
    CompoundIndex(name = SHA256_IDX, def = SHA256_IDX_DEF, background = true),
    CompoundIndex(name = COPY_FROM_IDX, def = COPY_FROM_IDX_DEF, background = true),
    CompoundIndex(name = FOLDER_IDX, def = FOLDER_IDX_DEF, background = true),
    CompoundIndex(name = CLUSTER_NAMES_IDX, def = CLUSTER_NAMES_IDX_DEF, background = true),
    CompoundIndex(name = ARCHIVED_IDX, def = ARCHIVED_IDX_DEF, background = true),
    CompoundIndex(name = COMPRESSED_IDX, def = COMPRESSED_IDX_DEF, background = true),
)
data class TNode(
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,
    var lastAccessDate: LocalDateTime? = null,

    var folder: Boolean,
    var path: String,
    var name: String,
    var fullPath: String,
    var size: Long,
    var expireDate: LocalDateTime? = null,
    var sha256: String? = null,
    var md5: String? = null,
    var deleted: LocalDateTime? = null,
    var copyFromCredentialsKey: String? = null,
    var copyIntoCredentialsKey: String? = null,
    var metadata: MutableList<TMetadata>? = null,
    var clusterNames: Set<String>? = null,
    var nodeNum: Long? = null,
    var archived: Boolean? = null,
    var compressed: Boolean? = null,
    var federatedSource: String? = null,

    @ShardingKey(count = SHARDING_COUNT)
    var projectId: String,
    var repoName: String,
) {

    companion object {
        const val FULL_PATH_IDX = "projectId_repoName_fullPath_idx"
        const val PATH_IDX = "projectId_repoName_path_idx"
        const val METADATA_IDX = "metadata_idx"
        const val COMPRESSED_IDX = "compressed_idx"
        const val ARCHIVED_IDX = "archived_idx"
        const val SHA256_IDX = "sha256_idx"
        const val COPY_FROM_IDX = "copy_idx"
        const val FULL_PATH_IDX_DEF = "{'projectId': 1, 'repoName': 1, 'fullPath': 1, 'deleted': 1}"
        const val PATH_IDX_DEF = "{'projectId': 1, 'repoName': 1, 'path': 1, 'deleted': 1}"
        const val METADATA_IDX_DEF = "{'metadata.key': 1, 'metadata.value': 1}"
        const val SHA256_IDX_DEF = "{'sha256': 1}"
        const val COMPRESSED_IDX_DEF = "{'compressed': 1}"
        const val ARCHIVED_IDX_DEF = "{'archived': 1}"
        const val COPY_FROM_IDX_DEF = "{'copyFromCredentialsKey':1}"
        const val FOLDER_IDX = "folder_idx"
        const val FOLDER_IDX_DEF = "{'folder': 1}"
        const val CLUSTER_NAMES_IDX = "cluster_names_idx"
        const val CLUSTER_NAMES_IDX_DEF = "{'clusterNames': 1}"
    }
}
