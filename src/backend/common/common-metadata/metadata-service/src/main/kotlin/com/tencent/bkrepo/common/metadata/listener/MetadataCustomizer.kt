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

package com.tencent.bkrepo.common.metadata.listener

import com.tencent.bkrepo.common.metadata.util.MetadataUtils
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionUpdateRequest

/**
 * 用于在node/package创建或更新时自定义元数据
 */
abstract class MetadataCustomizer {

    /**
     * 自定义元数据
     *
     * @param req node创建请求
     * @param extra 额外的元数据
     *
     * @return 自定义元数据
     */
    open fun customize(req: NodeCreateRequest, extra: List<MetadataModel>? = null): List<MetadataModel> {
        return merge(MetadataUtils.compatibleConvertToModel(req.metadata, req.nodeMetadata), extra)
    }

    /**
     * 自定义元数据
     *
     * @param req package version创建请求
     * @param extra 额外的元数据
     *
     * @return 自定义元数据
     */
    open fun customize(req: PackageVersionCreateRequest, extra: List<MetadataModel>? = null): List<MetadataModel> {
        return merge(MetadataUtils.compatibleConvertToModel(req.metadata, req.packageMetadata), extra)
    }

    /**
     * 自定义元数据，仅在元数据有更新时触发
     *
     * @param req package version更新请求
     * @param extra 额外的元数据
     *
     * @return 自定义元数据
     */
    open fun customize(
        req: PackageVersionUpdateRequest,
        oldMetadataModel: List<MetadataModel>,
        extra: List<MetadataModel>? = null
    ): List<MetadataModel> {
        return if (req.metadata == null && req.packageMetadata == null) {
            oldMetadataModel
        } else {
            merge(MetadataUtils.compatibleConvertToModel(req.metadata, req.packageMetadata), extra)
        }
    }

    protected fun merge(
        metadataModels: MutableList<MetadataModel>,
        extra: List<MetadataModel>? = null
    ): MutableList<MetadataModel> {
        extra?.let { metadataModels.addAll(it) }
        return metadataModels
    }
}
