package com.tencent.bkrepo.common.service.util

import com.tencent.bkrepo.common.api.constant.S3ErrorTypes
import com.tencent.bkrepo.common.api.exception.AWS4AuthenticationException
import com.tencent.bkrepo.common.api.exception.S3NotFoundException
import org.dom4j.Document
import org.dom4j.DocumentHelper
import org.dom4j.Element
import org.dom4j.io.OutputFormat
import org.dom4j.io.XMLWriter
import org.springframework.beans.BeansException
import org.springframework.cloud.sleuth.Tracer
import org.springframework.http.HttpHeaders
import java.io.IOException
import java.io.StringWriter

/**
 * s3请求异常时，返回处理
 */
class S3ExceptionCommonResponse {
    companion object {

        /**
         * 签名认证失败响应
         */
        fun buildUnauthorizedResponse(exception: AWS4AuthenticationException) {
            var request = HttpContextHolder.getRequest()

            // 转成xml格式
            var xml = ""
            val doc: Document = DocumentHelper.createDocument()
            val root: Element = doc.addElement("Error")
            root.addElement("Code").addText(S3ErrorTypes.SIGN_NOT_MATCH)
            root.addElement("Message").addText(LocaleMessageUtils.getLocalizedMessage(exception.messageCode))
            root.addElement("StringToSign").addText(request.getHeader(HttpHeaders.AUTHORIZATION))
            root.addElement("CononicalRequest").addText(request.getHeader("x-amz-content-sha256"))
            root.addElement("Resource").addText(exception.getFirstParam()?:"")
            root.addElement("RequestId").addText(getTraceId())
            root.addElement("TraceId").addText(getTraceId())

            val format = OutputFormat.createPrettyPrint()
            format.setEncoding("utf-8")
            val out = StringWriter()
            val writer = XMLWriter(out, format)
            writer.write(doc)
            writer.close()
            xml = out.toString()
            out.close()

            var response = HttpContextHolder.getResponse()
            response.setHeader("x-amz-request-id", getTraceId())
            response.setHeader("x-amz-trace-id", getTraceId())
            response.setHeader(HttpHeaders.CONTENT_TYPE, "application/xml")
            response.setHeader(HttpHeaders.CONTENT_LENGTH, xml.toByteArray(Charsets.UTF_8).size.toString())
            response.setCharacterEncoding("utf-8")
            response.addHeader(HttpHeaders.SERVER, HttpContextHolder.getClientAddress())
            response.status = exception.status.value

            try {
                val writer = response.writer
                writer.print(xml)
                writer.flush()
            } catch (e: IOException) {
                null
            }
        }

        /**
         * 请求资源不存在响应，比如key不存在
         */
        fun buildNotFoundResponse(exception: S3NotFoundException) {
            var request = HttpContextHolder.getRequest()
            var response = HttpContextHolder.getResponse()
            var xml = ""
            val doc: Document = DocumentHelper.createDocument()
            val root: Element = doc.addElement("Error")
            root.addElement("Code").addText(exception.getSecondParam()?:"")
            root.addElement("Message").addText(LocaleMessageUtils.getLocalizedMessage(exception.messageCode))
            root.addElement("Resource").addText(exception.getFirstParam()?:"")
            root.addElement("RequestId").addText(getTraceId())
            root.addElement("TraceId").addText(getTraceId())

            val format = OutputFormat.createPrettyPrint()
            format.setEncoding("utf-8")
            val out = StringWriter()
            val writer = XMLWriter(out, format)
            writer.write(doc)
            writer.close()
            xml = out.toString()
            out.close()

            response.setHeader("x-amz-request-id", getTraceId())
            response.setHeader("x-amz-trace-id", getTraceId())
            response.setHeader(HttpHeaders.CONTENT_TYPE, "application/xml")
            response.setHeader(HttpHeaders.CONTENT_LENGTH, xml.toByteArray(Charsets.UTF_8).size.toString())
            response.setCharacterEncoding("utf-8")
            response.addHeader(HttpHeaders.SERVER, HttpContextHolder.getClientAddress())
            response.status = exception.status.value
            try {
                val writer = response.writer
                writer.print(xml)
                writer.flush()
            } catch (e: IOException) {
                null
            }
        }

        private fun getTraceId(): String? {
            return try {
                SpringContextUtils.getBean<Tracer>().currentSpan()?.context()?.traceId()
            } catch (_: BeansException) {
                null
            }
        }
    }
}