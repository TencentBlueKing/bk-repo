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

package com.tencent.bkrepo.s3.artifact.response

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.LocaleMessageUtils
import com.tencent.bkrepo.s3.artifact.utils.ContextUtil
import com.tencent.bkrepo.s3.constant.DEFAULT_ENCODING
import com.tencent.bkrepo.s3.constant.S3HttpHeaders
import com.tencent.bkrepo.s3.exception.AWS4AuthenticationException
import org.dom4j.Document
import org.dom4j.DocumentHelper
import org.dom4j.Element
import org.dom4j.io.OutputFormat
import org.dom4j.io.XMLWriter
import java.io.StringWriter
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * s3请求异常时，返回处理
 */
class S3ExceptionCommonResponse {
    companion object {

        fun buildErrorResponse(exception: ErrorCodeException) {
            val request = HttpContextHolder.getRequest()
            val response = HttpContextHolder.getResponse()

            val xml = buildXmlResponse(request, exception)

            setResponseHeaders(response)
            writeResponse(response, xml)
        }

        private fun buildXmlResponse(request: HttpServletRequest, exception: ErrorCodeException): String {
            val doc: Document = DocumentHelper.createDocument()
            val root: Element = doc.addElement("Error")
            root.addElement("Code").addText(exception.params.getOrNull(0)?.toString() ?: "")
            root.addElement("Message").addText(LocaleMessageUtils.getLocalizedMessage(exception.messageCode))
            if (exception is AWS4AuthenticationException) {
                root.addElement("StringToSign").addText(request.getHeader(HttpHeaders.AUTHORIZATION))
                root.addElement("CanonicalRequest").addText(request.getHeader(S3HttpHeaders.X_AMZ_CONTENT_SHA256))
            }
            root.addElement("Resource").addText(exception.params.getOrNull(1)?.toString() ?: "")
            root.addElement("RequestId").addText(ContextUtil.getTraceId())
            root.addElement("TraceId").addText(ContextUtil.getTraceId())

            val format = OutputFormat.createPrettyPrint()
            format.setEncoding(DEFAULT_ENCODING)
            val out = StringWriter()
            val writer = XMLWriter(out, format)
            writer.write(doc)
            writer.close()

            return out.toString()
        }

        private fun setResponseHeaders(response: HttpServletResponse) {
            response.setHeader(S3HttpHeaders.X_AMZ_REQUEST_ID, ContextUtil.getTraceId())
            response.setHeader(S3HttpHeaders.X_AMZ_TRACE_ID, ContextUtil.getTraceId())
            response.setHeader(HttpHeaders.CONTENT_TYPE, "application/xml")
            response.setCharacterEncoding(DEFAULT_ENCODING)
        }

        private fun writeResponse(response: HttpServletResponse, xml: String) {
            response.setHeader(HttpHeaders.CONTENT_LENGTH, xml.toByteArray(Charsets.UTF_8).size.toString())
            response.writer.print(xml)
        }
    }
}