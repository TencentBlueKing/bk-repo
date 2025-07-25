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

package com.tencent.bkrepo.common.metadata.service.metadata

import com.tencent.bkrepo.repository.pojo.metadata.packages.PackageMetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.packages.PackageMetadataSaveRequest

/**
 * 包元数据服务接口
 */
interface PackageMetadataService {

    /**
     * 根据请求[request]保存或者更新元数据
     *
     * 如果元数据`key`已经存在则更新，否则创建新的
     */
    fun saveMetadata(request: PackageMetadataSaveRequest)

    /**
     * 根据请求[request]删除元数据
     *
     * @param request 删除元数据请求
     * @param allowDeleteSystemMetadata 是否允许删除系统元数据
     */
    fun deleteMetadata(request: PackageMetadataDeleteRequest, allowDeleteSystemMetadata: Boolean = true)

    /**
     * 根据请求[request]保存或者更新禁用元数据
     *
     * 如果元数据`key`已经存在则更新，否则创建新的
     */
    fun addForbidMetadata(request: PackageMetadataSaveRequest)
}
