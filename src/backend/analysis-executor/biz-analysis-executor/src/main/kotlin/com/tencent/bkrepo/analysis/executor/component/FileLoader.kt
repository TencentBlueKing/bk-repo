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

package com.tencent.bkrepo.analysis.executor.component

import com.tencent.bkrepo.analysis.executor.configuration.ScannerExecutorProperties
import com.tencent.bkrepo.analysis.executor.pojo.Layer
import com.tencent.bkrepo.analysis.executor.pojo.Manifest
import com.tencent.bkrepo.analysis.executor.pojo.ManifestV1
import com.tencent.bkrepo.analysis.executor.pojo.ManifestV2
import com.tencent.bkrepo.analyst.pojo.SubScanTask
import com.tencent.bkrepo.common.api.constant.CharPool
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.SystemErrorException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.search.NodeQueryBuilder
import org.apache.commons.codec.binary.Hex
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest

/**
 * 待分析制品加载工具类，会从制品库实际后端存储中加载待分析的制品，组装docker镜像layer为tar包
 */
@Component
class FileLoader(
    private val executorProperties: ScannerExecutorProperties,
    private val nodeClient: NodeClient,
    private val storageService: StorageService,
    private val storageCredentialsClient: StorageCredentialsClient,
) {
    /**
     * 加载[subtask]要扫描的制品
     */
    @Suppress("NestedBlockDepth")
    fun load(subtask: SubScanTask): Pair<File, String> {
        with(subtask) {
            val startTimestamp = System.currentTimeMillis()
            // 创建临时目录
            val tempDir = File(executorProperties.workDir, ".temp")
            if (!tempDir.exists()) {
                tempDir.mkdirs()
            }

            // 获取存储凭据
            val storageCredentials = credentialsKey?.let { storageCredentialsClient.findByKey(it).data!! }

            // 获取文件
            val file = File(tempDir, fileName(taskId, fileName(), repoType))
            val fos = DigestOutputStream(file.outputStream(), MessageDigest.getInstance("SHA-256"))
            storageService.load(sha256, Range.full(size), storageCredentials)?.use { artifactInputStream ->
                fos.use {
                    if (repoType == RepositoryType.DOCKER.name) {
                        // 加载镜像文件
                        loadImageTar(artifactInputStream, fos, subtask, storageCredentials)
                    } else {
                        // 加载文件
                        artifactInputStream.copyTo(fos)
                    }
                }
            }
            if (!file.exists()) {
                throw SystemErrorException(
                    CommonMessageCode.SYSTEM_ERROR,
                    "Load storage file failed: sha256[$sha256, credentials: [$credentialsKey]"
                )
            }
            logger.info("load file[$sha256] success, elapse ${System.currentTimeMillis() - startTimestamp}")
            return Pair(file, Hex.encodeHexString(fos.messageDigest.digest()))
        }
    }

    /**
     * 从[inputStream]中读取镜像的manifest信息，加载镜像layer到[outputStream]指定的tar文件中
     */
    private fun loadImageTar(
        inputStream: InputStream,
        outputStream: OutputStream,
        subtask: SubScanTask,
        storageCredentials: StorageCredentials?
    ) {
        val manifest = inputStream.readJsonString<ManifestV2>()
        TarArchiveOutputStream(outputStream).use { tos ->
            // 打包config文件
            val configFilePath = "${manifest.config.digest.substringAfter(CharPool.COLON)}.json"
            loadLayerTo(configFilePath, manifest.config, subtask, tos, storageCredentials)
            val layers = ArrayList<String>()
            // 打包layers文件
            manifest.layers.forEach {
                val layerDir = it.digest.substringAfter(CharPool.COLON)
                putArchiveEntry("$layerDir/", 0L, null, tos)
                val layerPath = "$layerDir/layer.tar"
                layers.add(layerPath)
                loadLayerTo(layerPath, it, subtask, tos, storageCredentials)
            }
            // 打包manifest.json文件
            val manifestV1 = ManifestV1(listOf(Manifest(configFilePath, emptyList(), layers)))
            val manifestV1Bytes = manifestV1.manifests.toJsonString().toByteArray()
            ByteArrayInputStream(manifestV1Bytes).use {
                putArchiveEntry(MANIFEST, manifestV1Bytes.size.toLong(), it, tos)
            }
        }
    }

    private fun loadLayerTo(
        filePath: String,
        layer: Layer,
        task: SubScanTask,
        tos: TarArchiveOutputStream,
        storageCredentials: StorageCredentials?
    ) {
        // 获取sha256和fileName
        val digestSplits = layer.digest.split(CharPool.COLON)
        if (digestSplits.size != 2 || digestSplits[0] != "sha256") {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, layer.digest)
        }
        val sha256 = digestSplits[1]
        logger.info("subtask[${task.taskId}] loading layer [$filePath]")

        // 加载layer
        val size = getNodeSize(task.projectId, task.repoName, sha256)
        storageService
            .load(sha256, Range.full(size), storageCredentials)
            ?.use { putArchiveEntry(filePath, size, it, tos) }
            ?: throw SystemErrorException(CommonMessageCode.RESOURCE_NOT_FOUND, "layer not found sha256[$sha256]")
    }

    private fun putArchiveEntry(name: String, size: Long, inputStream: InputStream?, tos: TarArchiveOutputStream) {
        val entry = TarArchiveEntry(name)
        entry.size = size
        tos.putArchiveEntry(entry)
        inputStream?.copyTo(tos)
        tos.closeArchiveEntry()
    }

    private fun getNodeSize(projectId: String, repoName: String, sha256: String): Long {
        val nodes = nodeClient.queryWithoutCount(
            NodeQueryBuilder()
                .projectId(projectId)
                .repoName(repoName)
                .sha256(sha256)
                .select(NodeDetail::size.name)
                .page(1, 1)
                .build()
        )
        if (nodes.isNotOk() || nodes.data!!.records.isEmpty()) {
            throw SystemErrorException(CommonMessageCode.RESOURCE_NOT_FOUND, sha256)
        }
        return (nodes.data!!.records[0][NodeDetail::size.name] as Number).toLong()
    }

    /**
     * 部分扫描器依赖文件名后缀，因此生成的文件名需要保留后缀
     */
    private fun fileName(subtaskId: String, rawFileName: String, repoType: String): String {
        val rawFileNameExt = rawFileName.substringAfterLast(CharPool.DOT, "")
        return if (repoType == RepositoryType.DOCKER.name) {
            "${subtaskId}.tar"
        } else if (rawFileNameExt.isNotEmpty()) {
            "${subtaskId}.$rawFileNameExt"
        } else {
            subtaskId
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FileLoader::class.java)

        /**
         * manifest.json文件名
         */
        private const val MANIFEST = "manifest.json"
    }
}
