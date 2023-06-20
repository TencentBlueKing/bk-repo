package com.tencent.bkrepo.analyst.utils

import com.alibaba.excel.EasyExcel
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy
import com.tencent.bkrepo.analyst.message.ScannerMessageCode
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.LocaleMessageUtils
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object EasyExcelUtils {
    private val logger = LoggerFactory.getLogger(EasyExcelUtils::class.java)

    fun download(data: Collection<*>, name: String, dataClass: Class<*>) {
        val response = HttpContextHolder.getResponse()
        response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        response.characterEncoding = "utf-8"
        val date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"))
        val fileName = URLEncoder.encode("$name-repo-$date", "UTF-8")
        response.setHeader(
            "Content-disposition",
            "attachment;filename*=utf-8''$fileName.xlsx"
        )
        try {
            EasyExcel.write(response.outputStream, dataClass)
                .head(dataClass).registerWriteHandler(LongestMatchColumnWidthStyleStrategy())
                .sheet(name).doWrite(data)
        } catch (e: IOException) {
            // 重置response
            response.reset()
            response.contentType = "application/json"
            response.characterEncoding = "utf-8"
            logger.error("download excel fail:${e}")
            val errorMessage = LocaleMessageUtils.getLocalizedMessage(ScannerMessageCode.EXPORT_REPORT_FAIL)
            val fail = ResponseBuilder.fail(ScannerMessageCode.EXPORT_REPORT_FAIL.getCode(), errorMessage)
            response.writer.println(fail.toJsonString())
        }
    }
}
