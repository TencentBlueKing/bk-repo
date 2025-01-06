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

package com.tencent.bkrepo.preview.service.impl

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.preview.config.configuration.PreviewConfig
import com.tencent.bkrepo.preview.constant.PreviewMessageCode
import com.tencent.bkrepo.preview.exception.PreviewNotFoundException
import com.tencent.bkrepo.preview.exception.PreviewSystemException
import com.tencent.bkrepo.preview.pojo.DownloadResult
import com.tencent.bkrepo.preview.pojo.FileAttribute
import com.tencent.bkrepo.preview.pojo.cache.PreviewFileCacheCreateRequest
import com.tencent.bkrepo.preview.pojo.cache.PreviewFileCacheInfo
import com.tencent.bkrepo.preview.service.FilePreview
import com.tencent.bkrepo.preview.service.FileTransferService
import com.tencent.bkrepo.preview.service.cache.impl.PreviewFileCacheServiceImpl
import com.tencent.bkrepo.preview.utils.EncodingDetects
import com.tencent.bkrepo.preview.utils.FileUtils
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import org.apache.commons.codec.binary.Base64
import org.slf4j.LoggerFactory
import org.springframework.web.util.HtmlUtils
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * 处理office文件
 */
abstract class AbstractFilePreview(
    private val config: PreviewConfig,
    private val fileTransferService: FileTransferService,
    private val previewFileCacheService: PreviewFileCacheServiceImpl,
    private val nodeService: NodeService
) : FilePreview {

    override fun filePreviewHandle(fileAttribute: FileAttribute) {
        // 下载文件, 存在临时目录
        val downloadResult = downloadFile(fileAttribute)
                ?: throw PreviewNotFoundException(PreviewMessageCode.PREVIEW_FILE_NOT_FOUND, fileAttribute.fileName!!)

        // 从缓存获取最终文件并且判断节点是否存在
        var previewFileCacheInfo = if (config.cacheEnabled) getCacheAndCheckExist(fileAttribute) else null

        if (previewFileCacheInfo == null) {
            // 文件校验，比如是否超过最大预览限制
            checkFileConstraints(fileAttribute)

            // 转换文件
            processFileConversion(downloadResult, fileAttribute, fileAttribute.outFilePath)

            // 文件内容处理
            processFileContent(fileAttribute)

            // 上传目标文件到仓库
            val nodeDetail = upload(fileAttribute, fileAttribute.finalFilePath!!)

            // 缓存结果
            previewFileCacheInfo = PreviewFileCacheInfo(
                md5 = downloadResult.md5!!,
                projectId = nodeDetail.projectId,
                repoName = nodeDetail.repoName,
                fullPath = nodeDetail.fullPath
            )
            if (config.cacheEnabled) addCache(previewFileCacheInfo)
        }
        // 删除临时文件
        if (config.isDeleteTmpFile) deleteTmpFile(fileAttribute)

        // 把文件内容以response输出
        sendFileAsResponse(fileAttribute, previewFileCacheInfo)
    }

    /**
     * 删除下载、转换的临时文件
     */
    private fun deleteTmpFile(fileAttribute: FileAttribute) {
        fileAttribute.finalFilePath?.let {FileUtils.deleteFileAndParentDirectory(it)}
        fileAttribute.originFilePath?.let {FileUtils.deleteFileAndParentDirectory(it)}
    }

    /**
     * 输出文件内容
     */
    private fun sendFileAsResponse(fileAttribute: FileAttribute, previewFileCacheInfo: PreviewFileCacheInfo) {
        fileTransferService.sendFileAsResponse(fileAttribute, previewFileCacheInfo)
    }

    /**
     * 文件校验，不支持大文件预览
     */
    private fun checkFileConstraints(fileAttribute: FileAttribute) {
        val size = fileAttribute.size
        val maxSizeInBytes = config.maxFileSize * 1024 * 1024

        if (size > maxSizeInBytes) {
            logger.warn("File size(${size}) exceeds the maximum allowed size of ${config.maxFileSize} MB.")
            throw PreviewSystemException(PreviewMessageCode.PREVIEW_FILE_SIZE_LIMIT_ERROR, "${config.maxFileSize}M")
        }
    }

    /**
     * 添加预览文件缓存
     */
    private fun addCache(previewFileCacheInfo: PreviewFileCacheInfo) {
        previewFileCacheService.createCache(PreviewFileCacheCreateRequest(
            md5 = previewFileCacheInfo.md5,
            projectId = previewFileCacheInfo.projectId,
            repoName = previewFileCacheInfo.repoName,
            fullPath = previewFileCacheInfo.fullPath
        ))
    }

    /**
     * 获取预览文件缓存
     */
    private fun getCacheAndCheckExist(fileAttribute: FileAttribute): PreviewFileCacheInfo? {
        val projectId = if (fileAttribute.storageType == 0) fileAttribute.projectId else config.projectId
        val repoName = if (fileAttribute.storageType == 0) fileAttribute.repoName else config.repoName
        val md5 = fileAttribute.md5
        val filePreviewCacheInfo = previewFileCacheService.getCache(md5!!, projectId!!, repoName!!) ?: return null
        // 检查节点是否存在
        return if (nodeService.checkExist(
                ArtifactInfo(
                    filePreviewCacheInfo.projectId,
                    filePreviewCacheInfo.repoName,
                    filePreviewCacheInfo.fullPath
                )
            )
        ) {
            filePreviewCacheInfo
        } else {
            // 节点不存在，移除缓存
            previewFileCacheService.removeCache(md5, projectId, repoName)
            logger.warn("node does not exist, delete the cache information, key：$md5")
            null
        }
    }

    /**
     * 把最终文件保存到仓库
     */
    private fun upload(fileAttribute: FileAttribute, filePath: String) : NodeDetail {
        return fileTransferService.upload(fileAttribute, filePath)
    }

    private fun downloadFile(fileAttribute: FileAttribute): DownloadResult? {
        val result = fileTransferService.download(fileAttribute)
        if (result?.code == DownloadResult.CODE_FAIL) {
            logger.error("File download failed for file: ${fileAttribute.fileName}. Error message: ${result.msg}")
            return null
        }
        fileAttribute.md5 = result!!.md5
        fileAttribute.size = result.size
        return result
    }

    /**
     * 编码文件，code、text等需要
     */
    @Throws(IOException::class)
    protected fun correctAndBase64EncodeFile(filePath: String, fileName: String) {
        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) {
            return
        }
        if (FileUtils.isIllegalFileName(fileName)) {
            return
        }

        var charset = EncodingDetects.getJavaEncode(filePath)
        if (charset == "ASCII") {
            charset = StandardCharsets.US_ASCII.name()
        }

        val fileCharset = Charset.forName(charset)

        // 读取文件内容
        val fileContent = BufferedReader(InputStreamReader(file.inputStream(), fileCharset)).use { reader ->
            reader.readText()
        }

        // 将文件内容进行Base64编码，并写回文件
        var base64EncodedContent = HtmlUtils.htmlEscape(
            Base64.encodeBase64String(fileContent.toByteArray(fileCharset))
        )
        FileOutputStream(file).use { fos ->
            fos.write(base64EncodedContent.toByteArray())
        }
    }

    /**
     * 转换文件，比如docx转成pdf
     */
    open fun processFileConversion(downloadResult: DownloadResult,
                                       fileAttribute: FileAttribute,
                                       outFilePath: String?): String {
        fileAttribute.finalFilePath = downloadResult.filePath
        return fileAttribute.finalFilePath!!
    }

    /**
     * 对转换后的文件进行操作，比如改变编码方式
     */
    open fun processFileContent(fileAttribute: FileAttribute) {
        // Do nothing
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AbstractFilePreview::class.java)
    }
}