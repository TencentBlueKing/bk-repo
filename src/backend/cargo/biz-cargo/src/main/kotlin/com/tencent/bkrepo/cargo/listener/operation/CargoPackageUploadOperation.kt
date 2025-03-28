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

package com.tencent.bkrepo.cargo.listener.operation

import com.tencent.bkrepo.cargo.pojo.event.CargoOperationRequest
import com.tencent.bkrepo.cargo.pojo.event.CargoPackageUploadRequest
import com.tencent.bkrepo.cargo.pojo.index.CrateIndex
import com.tencent.bkrepo.cargo.service.impl.CommonService
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.api.util.jsonCompress
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class CargoPackageUploadOperation(
    private val request: CargoOperationRequest,
    commonService: CommonService
) : AbstractCargoOperation(request, commonService) {

    override fun handleEvent(indexInputStream: InputStream?, storageCredentials: StorageCredentials?): ArtifactFile {
        with(request as CargoPackageUploadRequest) {
            indexInputStream.use {
                return addCrateVersion(it, crateIndex, storageCredentials)
            }
        }
    }

    private fun addCrateVersion(
        inputStream: InputStream?,
        crateIndex: CrateIndex,
        storageCredentials: StorageCredentials?
    ): ArtifactFile {
        try {
            // 读取 InputStream 的内容
            val lines = inputStream?.bufferedReader()?.readLines() ?: emptyList()
            // 解析为对象，并去重
            var versions = lines.asSequence()
                .mapNotNull { line ->
                    try {
                        JsonUtils.objectMapper.readValue(line, CrateIndex::class.java)
                    } catch (e: Exception) {
                        // 记录日志或忽略无效行
                        null
                    }
                }
                .toMutableList()
            // 添加新版本
            versions.add(crateIndex)
            // 按 version 排序
            versions = versions.distinct().sortedBy { it.vers }.toMutableList()
            val updatedLines = versions.joinToString("\n") { crateIndex ->
                // 将对象序列化为紧凑的 JSON 字符串
                JsonUtils.objectMapper.writeValueAsString(crateIndex).jsonCompress()
            }
            return ArtifactFileFactory.build(updatedLines.byteInputStream(), storageCredentials = storageCredentials)
        } catch (e: Exception) {
            logger.error("Failed to add version ${crateIndex.vers} for crate ${crateIndex.name}: ${e.message}")
            // TODO 如果失败了如何处理？？
            throw  e
        }
    }
}
