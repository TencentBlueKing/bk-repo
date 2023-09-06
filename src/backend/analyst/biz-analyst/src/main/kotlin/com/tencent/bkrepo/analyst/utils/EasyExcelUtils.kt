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

package com.tencent.bkrepo.analyst.utils

import com.alibaba.excel.EasyExcel
import com.alibaba.excel.write.metadata.style.WriteCellStyle
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy
import com.tencent.bkrepo.analyst.message.ScannerMessageCode
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.LocaleMessageUtils
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.servlet.http.HttpServletResponse

object EasyExcelUtils {

    private val logger = LoggerFactory.getLogger(EasyExcelUtils::class.java)

    fun download(data: Collection<*>, name: String, dataClass: Class<*>, includeColumns: Set<String>? = null) {
        val response = getDownloadResponse(name)
        try {
            EasyExcel.write(response.outputStream, dataClass).build().use { excelWriter ->
                val date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"))
                val cellStyleStrategy = HorizontalCellStyleStrategy(WriteCellStyle(), WriteCellStyle().apply {
                    this.horizontalAlignment = HorizontalAlignment.LEFT
                    this.verticalAlignment = VerticalAlignment.CENTER
                    this.wrapped = true
                })
                val writerSheetBuilder = EasyExcel
                    .writerSheet(date)
                    .registerWriteHandler(cellStyleStrategy)
                    .head(dataClass)
                includeColumns?.let {
                    writerSheetBuilder.includeColumnFieldNames(includeColumns)
                }
                excelWriter.write(data, writerSheetBuilder.build())
            }
        } catch (e: IOException) {
            resetDownloadResponse(response, e)
        }
    }

    private fun getDownloadResponse(fileName: String): HttpServletResponse {
        val response = HttpContextHolder.getResponse()
        response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        response.characterEncoding = "utf-8"
        val date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"))
        val exportFileName = URLEncoder.encode("$fileName-repo-$date", "UTF-8")
        response.setHeader(
            "Content-disposition",
            "attachment;filename*=utf-8''$exportFileName.xlsx"
        )
        return response
    }

    private fun resetDownloadResponse(response: HttpServletResponse, e: IOException) {
        response.reset()
        response.contentType = "application/json"
        response.characterEncoding = "utf-8"
        logger.error("download excel fail:${e}")
        val errorMessage = LocaleMessageUtils.getLocalizedMessage(ScannerMessageCode.EXPORT_REPORT_FAIL)
        val fail = ResponseBuilder.fail(ScannerMessageCode.EXPORT_REPORT_FAIL.getCode(), errorMessage)
        response.writer.println(fail.toJsonString())
    }
}
