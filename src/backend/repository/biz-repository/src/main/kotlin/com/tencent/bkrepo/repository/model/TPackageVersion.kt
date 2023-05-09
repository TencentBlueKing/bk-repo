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

package com.tencent.bkrepo.repository.model

import com.tencent.bkrepo.repository.model.TPackageVersion.Companion.VERSION_METADATA_IDX
import com.tencent.bkrepo.repository.model.TPackageVersion.Companion.VERSION_METADATA_IDX_DEF
import com.tencent.bkrepo.repository.model.TPackageVersion.Companion.VERSION_NAME_IDX
import com.tencent.bkrepo.repository.model.TPackageVersion.Companion.VERSION_NAME_IDX_DEF
import com.tencent.bkrepo.repository.model.TPackageVersion.Companion.VERSION_TAGS_IDX
import com.tencent.bkrepo.repository.model.TPackageVersion.Companion.VERSION_TAGS_IDX_DEF
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document("package_version")
@CompoundIndexes(
    CompoundIndex(name = VERSION_NAME_IDX, def = VERSION_NAME_IDX_DEF, background = true, unique = true),
    CompoundIndex(name = VERSION_METADATA_IDX, def = VERSION_METADATA_IDX_DEF, background = true),
    CompoundIndex(name = VERSION_TAGS_IDX, def = VERSION_TAGS_IDX_DEF, background = true)
)
data class TPackageVersion(
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,

    var packageId: String,
    var name: String,
    var size: Long,
    var ordinal: Long,
    var downloads: Long,
    var manifestPath: String? = null,
    var artifactPath: String? = null,
    var stageTag: List<String>,
    var metadata: List<TMetadata>,
    var tags: List<String>? = null,
    var extension: Map<String, Any>? = null,
    /**
     * PackageVersion 所在区域
     * 由于比较版本间内容是否相似成本较高，不支持不同区域相同PackageVersion，所以目前clusterNames只会有一个值
     */
    var clusterNames: Set<String>? = null
): ClusterResource {
    override fun readClusterNames(): Set<String>? {
        return this.clusterNames
    }

    override fun writeClusterNames(clusterNames: Set<String>) {
        this.clusterNames = clusterNames
    }

    companion object {
        const val VERSION_NAME_IDX = "version_name_idx"
        const val VERSION_METADATA_IDX = "version_metadata_idx"
        const val VERSION_TAGS_IDX = "version_tags_idx"

        const val VERSION_NAME_IDX_DEF = "{'packageId': 1, 'name': 1}"
        const val VERSION_METADATA_IDX_DEF = "{'packageId': 1, 'metadata': 1}"
        const val VERSION_TAGS_IDX_DEF = "{'packageId': 1, 'tags': 1}"
    }
}
