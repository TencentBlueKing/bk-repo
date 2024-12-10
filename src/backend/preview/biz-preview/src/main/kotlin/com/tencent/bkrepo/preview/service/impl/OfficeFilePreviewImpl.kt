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

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.redis.RedisOperation
import com.tencent.bkrepo.preview.config.configuration.PreviewConfig
import com.tencent.bkrepo.preview.constant.PreviewMessageCode
import com.tencent.bkrepo.preview.exception.PreviewHandleException
import com.tencent.bkrepo.preview.exception.PreviewNotFoundException
import com.tencent.bkrepo.preview.pojo.DownloadResult
import com.tencent.bkrepo.preview.pojo.FileAttribute
import com.tencent.bkrepo.preview.pojo.PreviewInfo
import com.tencent.bkrepo.preview.service.FilePreview
import com.tencent.bkrepo.preview.service.FileTransferService
import com.tencent.bkrepo.preview.service.OfficeToPdfService
import org.jodconverter.core.office.OfficeException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.util.StringUtils

/**
 * 处理office文件
 */
@Service
class OfficeFilePreviewImpl(
    private val config: PreviewConfig,
    private val officeToPdfService: OfficeToPdfService,
    private val otherFilePreview: OtherFilePreviewImpl,
    private val fileTransferService: FileTransferService,
    private val redisOperation: RedisOperation
) : FilePreview {

    override fun filePreviewHandle(fileAttribute: FileAttribute, previewInfo: PreviewInfo): PreviewInfo {
        val outFilePath = fileAttribute.outFilePath
        //根据md5优先在缓存中匹配，如果缓存中没有，需要重新下载、转换文件
        var fileUri:String? = getFileUriFromCache(fileAttribute.md5)
        if (fileUri.isNullOrEmpty()) {

            val downloadResult = downloadFile(fileAttribute)
                ?: throw PreviewNotFoundException(PreviewMessageCode.PREVIEW_FILE_NOT_FOUND, fileAttribute.fileName!!)

            fileUri = getFileUriFromCache(downloadResult.md5)
                ?: processFileConversion(downloadResult, fileAttribute, outFilePath)
        }
        setPreviewInfo(previewInfo, fileUri, fileAttribute)
        return previewInfo
    }

    private fun processFileConversion(downloadResult: DownloadResult,
                                      fileAttribute: FileAttribute,
                                      outFilePath: String?): String {
        val filePath = downloadResult.filePath ?: return ""
        var fileUri: String
        try {
            fileUri = if (isNeedConvert(fileAttribute)) {
                officeToPdfService.openOfficeToPDF(filePath, outFilePath!!, fileAttribute)
                fileTransferService.upload(fileAttribute, outFilePath).fullPath
            } else {
                fileTransferService.upload(fileAttribute, filePath).fullPath
            }
            // 更新缓存
            setFileUriCache(downloadResult.md5!!, fileUri, config.fullPathCacheTime)
        } catch (e: OfficeException) {
            logger.error("Failed to convert office file", e)
            throw PreviewHandleException(PreviewMessageCode.PREVIEW_FIlE_HANDLE_ERROR, fileAttribute.fileName!!)
        }
        return fileUri
    }

    private fun downloadFile(fileAttribute: FileAttribute): DownloadResult? {
        val result = fileTransferService.download(fileAttribute)
        if (result?.code == DownloadResult.CODE_FAIL) {
            logger.error("File download failed for file: ${fileAttribute.fileName}. Error message: ${result.msg}")
            return null
        }
        return result
    }

    /**
     * 设置文件预览信息
     */
    private fun setPreviewInfo(previewInfo: PreviewInfo,
                               fileUri: String,
                               fileAttribute: FileAttribute) {
        when (fileAttribute.suffix!!.lowercase()) {
            "xlsx" -> {
                previewInfo.pdfUrl = buildPreviewUrl(fileUri, fileAttribute)
                previewInfo.fileTemplate = FilePreview.XLSX_FILE_PREVIEW_PAGE
                previewInfo.msg = "Previewing XLSX file"
            }
            "csv" -> {
                previewInfo.url = buildPreviewUrl(fileUri, fileAttribute)
                previewInfo.fileTemplate = FilePreview.CSV_FILE_PREVIEW_PAGE
                previewInfo.msg = "Previewing CSV file"
            }
            else -> {
                previewInfo.fileName = fileAttribute.fileName
                previewInfo.pdfUrl = buildPreviewUrl(fileUri, fileAttribute)
                previewInfo.fileTemplate = FilePreview.PDF_FILE_PREVIEW_PAGE
                previewInfo.msg = "Previewing PDF file"
            }
        }
    }

    /**
     * 设置缓存路径
     */
    private fun setFileUriCache(fileMd5: String, fileUri: String, expiredInSecond: Long?) {
        if (expiredInSecond != null && expiredInSecond > 0) {
            redisOperation.set("$KEY_PREVIEW_FILE_PATH_PREFIX${StringPool.COLON}$fileMd5", fileUri, expiredInSecond)
        }
    }

    /**
     * 获取缓存路径
     */
    private fun getFileUriFromCache(fileMd5: String?): String? {
        return if (StringUtils.hasText(fileMd5)) {
            redisOperation.get("$KEY_PREVIEW_FILE_PATH_PREFIX${StringPool.COLON}${fileMd5}")
        } else null
    }

    private fun isNeedConvert(fileAttribute: FileAttribute): Boolean {
        val suffix = fileAttribute.suffix
        return suffix!!.lowercase() !in listOf("xlsx", "csv")
    }

    private fun buildPreviewUrl(uri: String, fileAttribute: FileAttribute): String {
        val newUri = "${config.projectId}/${config.repoName}$uri"
        val baseUrl = config.genericDomain ?: fileAttribute.baseUrl
        val formattedBaseUrl = if (baseUrl?.endsWith("/") == true) baseUrl else "$baseUrl/"
        return "$formattedBaseUrl$newUri"
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OfficeFilePreviewImpl::class.java)
        private const val KEY_PREVIEW_FILE_PATH_PREFIX = "preview:file:fullpath"
    }
}