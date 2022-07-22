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

package com.tencent.bkrepo.scanner.executor.util

import com.tencent.bkrepo.common.api.constant.CharPool
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.SystemErrorException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.search.NodeQueryBuilder
import com.tencent.bkrepo.scanner.executor.pojo.Layer
import com.tencent.bkrepo.scanner.executor.pojo.Manifest
import com.tencent.bkrepo.scanner.executor.pojo.ManifestV1
import com.tencent.bkrepo.scanner.executor.pojo.ManifestV2
import com.tencent.bkrepo.scanner.executor.pojo.ScanExecutorTask
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.FileOutputStream
import java.io.InputStream

class ImageScanHelper(
    private val nodeClient: NodeClient,
    private val storageService: StorageService
) {
    fun generateScanFile(fileOutputStream: FileOutputStream, task: ScanExecutorTask) {
        val manifest = task.inputStream.readJsonString<ManifestV2>()
        if (logger.isDebugEnabled) {
            logger.debug(CommonUtils.logMsg(task, manifest.toJsonString()))
        }
        TarArchiveOutputStream(fileOutputStream).use { tos ->
            // 打包config文件
            loadLayerTo(manifest.config, task, tos)
            val layers = ArrayList<String>()
            // 打包layers文件
            manifest.layers.forEach {
                layers.add(it.digest.replace(CharPool.COLON.toString(), "__"))
                loadLayerTo(it, task, tos)
            }
            // 打包manifest.json文件
            val configFileName = manifest.config.digest.replace(CharPool.COLON.toString(), "__")
            val manifestV1 = ManifestV1(listOf(Manifest(configFileName, emptyList(), layers)))
            val manifestV1Bytes = manifestV1.manifests.toJsonString().toByteArray()
            ByteArrayInputStream(manifestV1Bytes).use {
                putArchiveEntry(MANIFEST, manifestV1Bytes.size.toLong(), it, tos)
            }
        }
    }

    private fun loadLayerTo(layer: Layer, task: ScanExecutorTask, tos: TarArchiveOutputStream) {
        // 获取sha256和fileName
        val digestSplits = layer.digest.split(CharPool.COLON)
        if (digestSplits.size != 2 || digestSplits[0] != "sha256") {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, layer.digest)
        }
        val sha256 = digestSplits[1]
        val fileName = "${digestSplits[0]}__${digestSplits[1]}"
        logger.info(CommonUtils.logMsg(task, "load layer [$fileName]"))

        // 获取layer大小
        val nodes = nodeClient.search(
            NodeQueryBuilder()
                .projectId(task.projectId)
                .repoName(task.repoName)
                .sha256(sha256)
                .select(NodeDetail::size.name)
                .build()
        )
        if (nodes.isNotOk() || nodes.data!!.records.isEmpty()) {
            throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, sha256)
        }
        val size = (nodes.data!!.records[0][NodeDetail::size.name] as Number).toLong()

        // 加载layer
        storageService.load(sha256, Range.full(size), task.storageCredentials)
            ?.use { putArchiveEntry(fileName, size, it, tos) }
            ?: throw SystemErrorException(CommonMessageCode.RESOURCE_NOT_FOUND, "layer not found sha256[$sha256]")
    }

    private fun putArchiveEntry(name: String, size: Long, inputStream: InputStream, tos: TarArchiveOutputStream) {
        val entry = TarArchiveEntry(name)
        entry.size = size
        tos.putArchiveEntry(entry)
        inputStream.copyTo(tos)
        tos.closeArchiveEntry()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ImageScanHelper::class.java)

        /**
         * manifest.json文件名
         */
        private const val MANIFEST = "manifest.json"
    }
}
