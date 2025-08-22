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

package com.tencent.bkrepo.preview.service

import com.tencent.bkrepo.common.api.exception.SystemErrorException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.preview.config.configuration.PreviewConfig
import com.tencent.bkrepo.preview.pojo.FileAttribute
import org.jodconverter.core.office.OfficeException
import org.jodconverter.local.LocalConverter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File

/**
 * office转pdf服务
 */
@Component
class OfficeToPdfService(
    private val config: PreviewConfig,
    private val officePluginManager: OfficePluginManager
) {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(OfficeToPdfService::class.java)
    }

    @Throws(OfficeException::class)
    fun openOfficeToPDF(inputFilePath: String, outputFilePath: String, fileAttribute: FileAttribute) {
        officePluginManager.startOfficeManagerIfNeeded()
        office2pdf(inputFilePath, outputFilePath, fileAttribute)
    }

    @Throws(OfficeException::class)
    fun converterFile(inputFile: File, outputFilePathEnd: String, fileAttribute: FileAttribute) {
        val outputFile = File(outputFilePathEnd)

        // 如果目标目录不存在，则尝试创建
        if (!outputFile.parentFile.exists() && !outputFile.parentFile.mkdirs()) {
            logger.error("Failed to create directory [$outputFilePathEnd], please check the directory permissions!")
            throw SystemErrorException(CommonMessageCode.SYSTEM_ERROR, "Failed to create directory")
        }

        // 设置转换文件时的过滤条件
        val filterData = mutableMapOf<String, Any>(
            "EncryptFile" to true,  // 加密文件
            "PageRange" to config.isOfficePageRange, // 限制页面范围
            "Watermark" to config.isOfficeWatermark, // 水印
            "Quality" to config.officeQuality, // 图片压缩质量
            "MaxImageResolution" to config.officeMaxImageResolution, // DPI
            "ExportBookmarks" to config.isOfficeExportBookmarks, // 导出书签
            "ExportNotes" to config.isOfficeExportNotes // 导出批注作为PDF注释
        )

        // 自定义属性，包含过滤条件
        val customProperties = mutableMapOf<String, Any>(
            "FilterData" to filterData
        )

        // 使用 LocalConverter 构建转换器
        val builder = LocalConverter.builder().storeProperties(customProperties)

        // 执行文件转换
        builder.build().convert(inputFile).to(outputFile).execute()
    }

    @Throws(OfficeException::class)
    fun office2pdf(inputFilePath: String?, outputFilePath: String?, fileAttribute: FileAttribute) {
        inputFilePath?.let {
            val inputFile = File(it)

            // 如果目标文件路径为空，则使用默认的输出路径
            if (outputFilePath == null) {
                val outputFilePathEnd = getOutputFilePath(it)
                if (inputFile.exists()) {
                    converterFile(inputFile, outputFilePathEnd, fileAttribute)
                }
            } else {
                // 如果目标路径不为空，直接进行转换
                if (inputFile.exists()) {
                    converterFile(inputFile, outputFilePath, fileAttribute)
                }
            }
        }
    }

    // 私有方法，获取输出文件路径，将输入文件路径中的扩展名改为 .pdf
    private fun getOutputFilePath(inputFilePath: String): String {
        return inputFilePath.replace(".${getPostfix(inputFilePath)}", ".pdf")
    }

    // 私有方法，获取文件扩展名
    private fun getPostfix(inputFilePath: String): String {
        return inputFilePath.substring(inputFilePath.lastIndexOf(".") + 1)
    }
}