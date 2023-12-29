/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.svn.interceptor

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.ensurePrefix
import com.tencent.bkrepo.common.service.util.proxy.DefaultProxyCallHandler
import com.tencent.bkrepo.common.service.util.proxy.HttpProxyUtil.Companion.headers
import com.tencent.bkrepo.svn.config.SvnProperties
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.XMLReader
import org.xml.sax.helpers.AttributesImpl
import org.xml.sax.helpers.XMLFilterImpl
import org.xml.sax.helpers.XMLReaderFactory
import java.io.InputStream
import java.io.OutputStream
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.xml.transform.TransformerFactory
import javax.xml.transform.sax.SAXSource
import javax.xml.transform.stream.StreamResult

/**
 * 制品库的SVN仓库路径前缀为/{projectId}/{repoName}，与代理的仓库可能不一致，需要调整请求与响应中的前缀
 */
class ChangeAncestorProxyHandler(
    private val svnProperties: SvnProperties,
) : DefaultProxyCallHandler() {
    override fun before(
        proxyRequest: HttpServletRequest,
        proxyResponse: HttpServletResponse,
        request: Request
    ): Request {
        val oldPrefix = if (svnProperties.repoPrefix.isEmpty()) {
            prefix(proxyRequest.servletPath)
        } else {
            svnProperties.repoPrefix + prefix(proxyRequest.servletPath)
        }
        val newPrefix = prefix(request.url.encodedPath)
        if (oldPrefix.isNullOrEmpty() || newPrefix.isNullOrEmpty()) {
            logger.warn("prefix not found: oldPrefix[$oldPrefix], newPrefix[$newPrefix]")
            return request
        }
        val oldBaseUrl = svnProperties.baseUrl + oldPrefix
        val newBaseUrl = request.url.scheme + "://" + request.url.host + newPrefix

        val builder = request.newBuilder()
        builder.header(HttpHeaders.HOST, hostHeader(request.url))
        proxyRequest.headers().forEach { (k, v) ->
            if (k in requestHeaders) {
                val newHeaderValue = newPrefix + v.substringAfter(oldPrefix)
                builder.header(k, newHeaderValue)
            }
            if (k == HEADER_SVN_DESTINATION) {
                builder.header(k, newBaseUrl + v.substringAfter(oldBaseUrl))
            }
        }

        if (proxyRequest.contentType?.startsWith("text/xml") == true) {
            val oldBody = proxyRequest.inputStream.readBytes().toString(Charsets.UTF_8)
            val newBody = StringUtils.replaceEach(
                oldBody,
                buildSearchList(oldPrefix, oldBaseUrl, true),
                buildReplacementList(newPrefix, newBaseUrl, true)
            )
            builder.header(HttpHeaders.CONTENT_LENGTH, newBody.length.toString())
            builder.method(proxyRequest.method, RequestBody.create("text/xml".toMediaType(), newBody))
        }

        return builder.build()
    }

    override fun after(proxyRequest: HttpServletRequest, proxyResponse: HttpServletResponse, response: Response) {
        if (!response.isSuccessful) {
            return super.after(proxyRequest, proxyResponse, response)
        }

        val oldPrefix = prefix(response.request.url.encodedPath)
        val newPrefix = if (svnProperties.repoPrefix.isEmpty()) {
            prefix(proxyRequest.servletPath)
        } else {
            svnProperties.repoPrefix + prefix(proxyRequest.servletPath)
        }
        if (oldPrefix.isNullOrEmpty() || newPrefix.isNullOrEmpty()) {
            return super.after(proxyRequest, proxyResponse, response)
        }

        // 转发状态码
        proxyResponse.status = response.code

        // 转发头
        response.headers.forEach { (key, value) ->
            if (key in responseHeaders) {
                val newHeaderValue = newPrefix + value.substringAfter(oldPrefix)
                proxyResponse.setHeader(key, newHeaderValue)
            } else {
                proxyResponse.addHeader(key, value)
            }
        }

        // 转发body
        if (oldPrefix != newPrefix && response.header(HttpHeaders.CONTENT_TYPE)?.startsWith("text/xml") == true) {
            if (response.headers[HttpHeaders.TRANSFER_ENCODING] == "chunked") {
                replace(response.body!!.byteStream(), proxyResponse.outputStream, oldPrefix, newPrefix)
            } else {
                val oldBody = response.body!!.string()
                val newBody = StringUtils.replaceEach(
                    oldBody,
                    buildSearchList(oldPrefix),
                    buildReplacementList(newPrefix)
                ).toByteArray()
                proxyResponse.setContentLength(newBody.size)
                proxyResponse.outputStream.write(newBody)
            }
        } else {
            response.body?.byteStream()?.use {
                it.copyTo(proxyResponse.outputStream)
            }
        }
    }

    /**
     * 获取/{projectId}/{repoName}前缀
     *
     * /a/b/c -> /a/b
     * /a/b -> /a/b
     * /a -> null
     * a/b/c -> /a/b
     */
    private fun prefix(path: String): String? {
        var thirdSlashIndex = -1
        var slashCount = if (path[0] != '/') {
            1
        } else {
            0
        }

        for ((index, c) in path.withIndex()) {
            if (c == '/') {
                slashCount++
            }
            if (slashCount == 3) {
                thirdSlashIndex = index
                break
            }
        }

        val prefix = when (slashCount) {
            2 -> path
            3 -> path.substring(0, thirdSlashIndex)
            else -> null
        }

        return prefix?.ensurePrefix("/")?.removeSuffix("/")
    }

    private fun hostHeader(httpUrl: HttpUrl): String {
        val isHttpPort = httpUrl.port == 80 && httpUrl.scheme == "http"
        val isHttpsPort = httpUrl.port == 443 && httpUrl.scheme == "https"
        return if (isHttpPort || isHttpsPort) {
            httpUrl.host
        } else {
            "${httpUrl.host}:${httpUrl.port}"
        }
    }

    private fun buildSearchList(
        oldPrefix: String,
        oldBaseUrl: String? = null,
        request: Boolean = false
    ): Array<String> {
        return if (request) {
            arrayOf(
                "$TAG_HREF>$oldPrefix",
                "$TAG_PATH>$oldPrefix",
                "$ATTR_BC_URL=\"$oldPrefix",
                "$TAG_SRC_PATH>$oldBaseUrl",
                "$TAG_SRC_PATH>$oldPrefix",
                "$TAG_DST_PATH>$oldBaseUrl",
                "$TAG_DST_PATH>$oldPrefix",
            )
        } else {
            arrayOf("$TAG_HREF>$oldPrefix", "$TAG_PATH>$oldPrefix", "$ATTR_BC_URL=\"$oldPrefix")
        }
    }

    private fun buildReplacementList(
        newPrefix: String,
        newBaseUrl: String? = null,
        request: Boolean = false
    ): Array<String> {
        return if (request) {
            arrayOf(
                "$TAG_HREF>$newPrefix",
                "$TAG_PATH>$newPrefix",
                "$ATTR_BC_URL=\"$newPrefix",
                "$TAG_SRC_PATH>$newBaseUrl",
                "$TAG_SRC_PATH>$newPrefix",
                "$TAG_DST_PATH>$newBaseUrl",
                "$TAG_DST_PATH>$newPrefix"
            )
        } else {
            arrayOf("$TAG_HREF>$newPrefix", "$TAG_PATH>$newPrefix", "$ATTR_BC_URL=\"$newPrefix")
        }
    }

    /**
     * 流式替换，返回替换后输出的文件
     */
    private fun replace(
        inputStream: InputStream,
        outputStream: OutputStream,
        oldPrefix: String,
        newPrefix: String,
    ) {
        val reader = XMLReaderFactory.createXMLReader()
        val filter = ChangeAncestorFilter(reader, oldPrefix, newPrefix, "", "")
        val src = SAXSource(filter, InputSource(inputStream))
        val res = StreamResult(outputStream)
        TransformerFactory.newInstance().newTransformer().transform(src, res)
    }

    class ChangeAncestorFilter(
        reader: XMLReader,
        private val oldPrefix: String,
        private val newPrefix: String,
        private val oldBaseUrl: String,
        private val newBaseUrl: String,
    ) : XMLFilterImpl(reader) {
        private var processingTag = ""
        private val processingText = StringBuilder()
        override fun startElement(uri: String, localName: String, qName: String, atts: Attributes) {
            if (qName == TAG_ADD_DIRECTORY) {
                val idx = atts.getIndex("bc-url")
                val old = atts.getValue(idx)
                val newAttrs = AttributesImpl(atts)
                newAttrs.setValue(idx, old.replace(oldPrefix, newPrefix))
                super.startElement(uri, localName, qName, newAttrs)
                return
            }

            if (qName == TAG_HREF || qName == TAG_PATH || qName == TAG_SRC_PATH) {
                processingTag = qName
            }
            super.startElement(uri, localName, qName, atts)
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (processingTag != "") {
                processingText.append(ch, start, length)
            }

            var old = ""
            var new = ""
            if (processingTag == TAG_HREF || processingTag == TAG_PATH) {
                old = oldPrefix
                new = newPrefix
            } else if (processingTag == TAG_SRC_PATH && oldBaseUrl.isNotEmpty() && newBaseUrl.isNotEmpty()) {
                old = oldBaseUrl + oldPrefix
                new = newBaseUrl + newPrefix
            }

            val idx = processingText.indexOf(old)
            if (old.isNotEmpty() && new.isNotEmpty() && idx != -1) {
                processingText.replace(idx, idx + old.length, new)
                val charArray = CharArray(processingText.length)
                processingText.getChars(0, processingText.length, charArray, 0)
                super.characters(charArray, 0, charArray.size)
            } else {
                super.characters(ch, start, length)
            }
        }

        override fun endElement(uri: String, localName: String, qName: String) {
            processingTag = ""
            processingText.clear()
            super.endElement(uri, localName, qName)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ChangeAncestorProxyHandler::class.java)
        private const val HEADER_SVN_DELTA_BASE = "X-SVN-VR-Base"

        private const val HEADER_SVN_ROOT_URI = "SVN-Repository-Root"
        private const val HEADER_SVN_ME_RESOURCE = "SVN-Me-Resource"
        private const val HEADER_SVN_REV_STUB = "SVN-Rev-Stub"
        private const val HEADER_SVN_REV_ROOT_STUB = "SVN-Rev-Root-Stub"
        private const val HEADER_SVN_TXN_STUB = "SVN-Txn-Stub"
        private const val HEADER_SVN_TXN_ROOT_STUB = "SVN-Txn-Root-Stub"
        private const val HEADER_SVN_VTXN_STUB = "SVN-VTxn-Stub"
        private const val HEADER_SVN_VTXN_ROOT_STUB = "SVN-VTxn-Root-Stub"
        private const val HEADER_SVN_DESTINATION = "Destination"

        val requestHeaders = arrayOf(HEADER_SVN_DELTA_BASE)
        val responseHeaders = arrayOf(
            HEADER_SVN_DELTA_BASE,
            HEADER_SVN_ROOT_URI,
            HEADER_SVN_ME_RESOURCE,
            HEADER_SVN_REV_STUB,
            HEADER_SVN_REV_ROOT_STUB,
            HEADER_SVN_TXN_STUB,
            HEADER_SVN_TXN_ROOT_STUB,
            HEADER_SVN_VTXN_STUB,
            HEADER_SVN_VTXN_ROOT_STUB,
        )

        // xml element
        private const val TAG_HREF = "D:href"
        private const val TAG_PATH = "S:path"
        private const val TAG_SRC_PATH = "S:src-path"
        private const val TAG_DST_PATH = "S:dst-path"
        private const val TAG_ADD_DIRECTORY = "S:add-directory"
        private const val ATTR_BC_URL = "bc-url"
    }
}
