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

package com.tencent.bkrepo.preview.service.impl

import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.preview.config.configuration.PreviewConfig
import com.tencent.bkrepo.preview.constant.PreviewMessageCode
import com.tencent.bkrepo.preview.exception.PreviewSystemException
import com.tencent.bkrepo.preview.pojo.DownloadResult
import com.tencent.bkrepo.preview.pojo.FileAttribute
import com.tencent.bkrepo.preview.service.FileTransferService
import com.tencent.bkrepo.preview.service.OfficeToPdfService
import com.tencent.bkrepo.preview.service.cache.impl.PreviewFileCacheServiceImpl
import com.tencent.bkrepo.preview.utils.EncodingDetects
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import org.jodconverter.core.office.OfficeException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 处理office文件
 */
@Service
class OfficeFilePreviewImpl(
    private val config: PreviewConfig,
    private val officeToPdfService: OfficeToPdfService,
    private val fileTransferService: FileTransferService,
    private val previewFileCacheService: PreviewFileCacheServiceImpl,
    private val nodeService: NodeService
) : AbstractFilePreview(
    config,
    fileTransferService,
    previewFileCacheService,
    nodeService
) {
    /**
     * 转换文件，比如docx转成pdf
     */
    override fun processFileConversion(downloadResult: DownloadResult,
                                       fileAttribute: FileAttribute,
                                       outFilePath: String?): String {
        val filePath = downloadResult.filePath!!
        var finalFilePath: String
        try {
            finalFilePath = if (isNeedConvert(fileAttribute)) {
                officeToPdfService.openOfficeToPDF(filePath, outFilePath!!, fileAttribute)
                outFilePath
            } else {
                filePath
            }
        } catch (e: OfficeException) {
            logger.error("Failed to convert office file", e)
            throw PreviewSystemException(PreviewMessageCode.PREVIEW_FIlE_CONVERT_ERROR, fileAttribute.fileName!!)
        }
        fileAttribute.finalFilePath = finalFilePath
        return finalFilePath
    }

    /**
     * 对转换后的文件进行操作(改变编码方式)
     */
    override fun processFileContent(fileAttribute: FileAttribute) {
        if (fileAttribute.isHtmlView && !fileAttribute.suffix.equals("csv")) {
            changeFileEncoding(fileAttribute.finalFilePath!!)
        }
    }

    private fun isNeedConvert(fileAttribute: FileAttribute): Boolean {
        val suffix = fileAttribute.suffix
        return suffix!!.lowercase() !in listOf("xlsx", "csv")
    }

    /**
     * 把文件改成utf8编码
     */
    private fun changeFileEncoding(outFilePath: String) {
        try {
            // 获取文件的编码方式
            val charset = EncodingDetects.getJavaEncode(outFilePath)
            // 读取文件内容并替换 charset
            val fileContent = readFileWithEncoding(outFilePath, charset!!)
            // 添加额外的 HTML 控制头
            val updatedContent = addHtmlHeaders(fileContent)
            // 重新写入文件，采用 UTF-8 编码
            writeFileWithUtf8(outFilePath, updatedContent)
        } catch (e: IOException) {
            logger.error("Error changing the encoding of the file", e)
        }
    }

    private fun readFileWithEncoding(outFilePath: String, charset: String): String {
        val sb = StringBuilder()
        FileInputStream(outFilePath).use { inputStream ->
            BufferedReader(InputStreamReader(inputStream, charset)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line?.replace("charset=gb2312", "charset=utf-8") ?: line)
                }
            }
        }
        return sb.toString()
    }

    private fun addHtmlHeaders(content: String): String {
        val sb = StringBuilder(content)
        sb.append("<script src=\"js/jquery-3.6.1.min.js\" type=\"text/javascript\"></script>")
        sb.append("<script src=\"excel/excel.header.js\" type=\"text/javascript\"></script>")
        sb.append("<link rel=\"stylesheet\" href=\"excel/excel.css\">")
        return sb.toString()
    }

    private fun writeFileWithUtf8(outFilePath: String, content: String) {
        FileOutputStream(outFilePath).use { fos ->
            BufferedWriter(OutputStreamWriter(fos, StandardCharsets.UTF_8)).use { writer ->
                writer.write(content)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OfficeFilePreviewImpl::class.java)
    }
}