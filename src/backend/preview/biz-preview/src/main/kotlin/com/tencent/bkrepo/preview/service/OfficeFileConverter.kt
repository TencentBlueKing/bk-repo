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
import com.tencent.bkrepo.preview.utils.EncodingDetects
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.io.FilenameUtils
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.jodconverter.core.office.OfficeException
import org.jodconverter.local.LocalConverter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.Reader
import java.nio.charset.Charset

/**
 * office文件转换器，支持docx -> pdf、ppt -> pdf、xls -> xlsx等多种类型转换
 */
@Component
class OfficeFileConverter(
    private val config: PreviewConfig,
    private val officePluginManager: OfficePluginManager
) {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(OfficeFileConverter::class.java)
    }

    @Throws(OfficeException::class)
    fun convertFile(inputFilePath: String,
                    outputFilePath: String,
                    fileAttribute: FileAttribute) {
        officePluginManager.startOfficeManagerIfNeeded()
        checkInputFile(inputFilePath)
        prepareOutputFile(outputFilePath)
        executeConversion(inputFilePath, outputFilePath, fileAttribute)
    }

    @Throws(OfficeException::class)
    private fun executeConversion(inputFilePath: String,
                                  outputFilePath: String,
                                  fileAttribute: FileAttribute) {
        if (isCsv(fileAttribute)) {
            convertCsvToXlsx(inputFilePath, outputFilePath, fileAttribute)
        } else {
            convertByOffice(inputFilePath, outputFilePath, fileAttribute)
        }
    }

    @Throws(OfficeException::class)
    private fun convertByOffice(inputFilePath: String,
                                outputFilePath: String,
                                fileAttribute: FileAttribute) {
        val filterData = mutableMapOf<String, Any>(
            "EncryptFile" to true,  // 加密文件
            "PageRange" to config.isOfficePageRange, // 限制页面范围
            "Watermark" to config.isOfficeWatermark, // 水印
            "Quality" to config.officeQuality, // 图片压缩质量
            "MaxImageResolution" to config.officeMaxImageResolution, // DPI
            "ExportBookmarks" to config.isOfficeExportBookmarks, // 导出书签
            "ExportNotes" to config.isOfficeExportNotes // 导出批注作为PDF注释
        )

        // 自定义转换属性
        val customProperties = mutableMapOf<String, Any>(
            "FilterData" to filterData
        )

        // 使用 LocalConverter 构建转换器
        val builder = LocalConverter.builder().storeProperties(customProperties)

        // 执行文件转换
        builder.build()
            .convert(File(inputFilePath))
            .to(File(outputFilePath))
            .execute()
    }

    private fun convertCsvToXlsx(inputFilePath: String,
                                 outputFilePath: String,
                                 fileAttribute: FileAttribute) {
        val inputFile = File(inputFilePath)
        val charset = resolveCharset(inputFile)

        val format = CSVFormat.Builder.create(CSVFormat.DEFAULT)
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .build()

        var inputStream: InputStream? = null
        var reader: Reader? = null
        var csvParser: CSVParser? = null
        var workbook: Workbook? = null
        var outputStream: OutputStream? = null

        try {
            inputStream = FileInputStream(inputFile)
            reader = InputStreamReader(inputStream, charset)
            csvParser = CSVParser(reader, format)

            workbook = XSSFWorkbook()
            outputStream = FileOutputStream(outputFilePath)

            val sheetName = FilenameUtils.getBaseName(inputFile.name)
            val sheet = workbook.createSheet(sheetName)

            var rowIndex = 0

            // 表头
            val headerRow = sheet.createRow(rowIndex++)
            val headers = csvParser.headerNames
            headers.forEachIndexed { index, header ->
                headerRow.createCell(index).setCellValue(header)
            }

            // 内容
            for (record in csvParser) {
                val row = sheet.createRow(rowIndex++)
                for (i in 0 until record.size()) {
                    row.createCell(i).setCellValue(record[i])
                }
            }
            workbook.write(outputStream)
            outputStream.flush()
        } catch (e: Exception) {
            var errorMsg = "Converting csv to xlsx failed"
            logger.error(errorMsg, e)
            throw OfficeException(errorMsg, e)
        } finally {
            closeQuietly(outputStream)
            closeQuietly(workbook)
            closeQuietly(csvParser)
            closeQuietly(reader)
            closeQuietly(inputStream)
        }
    }

    private fun closeQuietly(closeable: AutoCloseable?) {
        try {
            closeable?.close()
        } catch (ex: Exception) {
            logger.warn("close resource failed", ex)
        }
    }

    private fun prepareOutputFile(path: String) {
        val file = File(path)
        // 如果目标目录不存在，则尝试创建
        if (!file.parentFile.exists() && !file.parentFile.mkdirs()) {
            logger.error("Failed to create directory for output file [$path]")
            throw SystemErrorException(CommonMessageCode.SYSTEM_ERROR, "Failed to create output directory")
        }
    }

    private fun checkInputFile(path: String) {
        val file = File(path)
        if (!file.exists()) {
            logger.error("Input file [$path] does not exist!")
            throw SystemErrorException(CommonMessageCode.SYSTEM_ERROR, "Input file does not exist")
        }
    }

    private fun isCsv(fileAttribute: FileAttribute): Boolean =
        fileAttribute.suffix.equals("csv", ignoreCase = true)

    private fun resolveCharset(inputFile: File): Charset {
        val encoding = EncodingDetects.getJavaEncode(inputFile)
        return if (
            encoding.equals("UTF-8", ignoreCase = true) ||
            encoding.equals("UTF-8-BOM", ignoreCase = true)
        ) {
            Charsets.UTF_8
        } else {
            Charset.forName("GBK")
        }
    }
}