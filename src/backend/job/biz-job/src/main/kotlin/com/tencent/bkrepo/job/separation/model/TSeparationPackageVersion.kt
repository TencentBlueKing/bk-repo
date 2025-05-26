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
import com.tencent.bkrepo.job.separation.model.TSeparationPackageVersion.Companion.SEPARATION_VERSION_NAME_IDX
import com.tencent.bkrepo.job.separation.model.TSeparationPackageVersion.Companion.SEPARATION_VERSION_NAME_IDX_DEF
import com.tencent.bkrepo.common.metadata.pojo.metadata.MetadataModel
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import java.time.LocalDateTime

/**
 * 冷数据package version表
 */
@ShardingDocument("separation_package_version")
@CompoundIndexes(
    CompoundIndex(name = SEPARATION_VERSION_NAME_IDX, def = SEPARATION_VERSION_NAME_IDX_DEF, background = true),
)
data class TSeparationPackageVersion(
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
    var metadata: List<MetadataModel>,
    var tags: List<String>? = null,
    var extension: Map<String, Any>? = null,
    var clusterNames: Set<String>? = null,
    @ShardingKey
    var separationDate: LocalDateTime,
) {
    companion object {
        const val SEPARATION_VERSION_NAME_IDX = "separation_version_name_idx"
        const val SEPARATION_VERSION_NAME_IDX_DEF = "{'packageId': 1, 'name': 1}"
    }
}
