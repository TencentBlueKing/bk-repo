/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.job.separation.model

import com.tencent.bkrepo.common.api.mongo.ShardingDocument
import com.tencent.bkrepo.common.api.mongo.ShardingKey
import com.tencent.bkrepo.job.separation.model.TSeparationNode.Companion.SEPARATION_FOLDER_IDX
import com.tencent.bkrepo.job.separation.model.TSeparationNode.Companion.SEPARATION_FOLDER_IDX_DEF
import com.tencent.bkrepo.job.separation.model.TSeparationNode.Companion.SEPARATION_FULL_PATH_IDX
import com.tencent.bkrepo.job.separation.model.TSeparationNode.Companion.SEPARATION_FULL_PATH_IDX_DEF
import com.tencent.bkrepo.job.separation.model.TSeparationNode.Companion.SEPARATION_PATH_IDX
import com.tencent.bkrepo.job.separation.model.TSeparationNode.Companion.SEPARATION_PATH_IDX_DEF
import com.tencent.bkrepo.job.separation.model.TSeparationNode.Companion.SEPARATION_SHA256_IDX
import com.tencent.bkrepo.job.separation.model.TSeparationNode.Companion.SEPARATION_SHA256_IDX_DEF
import com.tencent.bkrepo.common.metadata.pojo.metadata.MetadataModel
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import java.time.LocalDateTime

/**
 * 冷数据节点表
 */
@ShardingDocument("separation_node")
@CompoundIndexes(
    CompoundIndex(name = SEPARATION_FULL_PATH_IDX, def = SEPARATION_FULL_PATH_IDX_DEF, background = true),
    CompoundIndex(name = SEPARATION_PATH_IDX, def = SEPARATION_PATH_IDX_DEF, background = true),
    CompoundIndex(name = SEPARATION_FOLDER_IDX, def = SEPARATION_FOLDER_IDX_DEF, background = true),
    CompoundIndex(name = SEPARATION_SHA256_IDX, def = SEPARATION_SHA256_IDX_DEF, background = true),
)
data class TSeparationNode(
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
    var metadata: List<MetadataModel>? = null,
    var clusterNames: Set<String>? = null,
    var nodeNum: Long? = null,
    var archived: Boolean? = null,
    var compressed: Boolean? = null,

    var projectId: String,
    var repoName: String,
    @ShardingKey
    var separationDate: LocalDateTime,
) {
    companion object {
        const val SEPARATION_FULL_PATH_IDX = "separation_projectId_repoName_fullPath_idx"
        const val SEPARATION_PATH_IDX = "separation_projectId_repoName_path_idx"
        const val SEPARATION_FULL_PATH_IDX_DEF = "{'projectId': 1, 'repoName': 1, 'fullPath': 1}"
        const val SEPARATION_PATH_IDX_DEF = "{'projectId': 1, 'repoName': 1, 'path': 1}"
        const val SEPARATION_FOLDER_IDX = "separation_folder_idx"
        const val SEPARATION_FOLDER_IDX_DEF = "{'folder': 1}"
        const val SEPARATION_SHA256_IDX = "separation_sha256_idx"
        const val SEPARATION_SHA256_IDX_DEF = "{'sha256': 1}"
    }
}
