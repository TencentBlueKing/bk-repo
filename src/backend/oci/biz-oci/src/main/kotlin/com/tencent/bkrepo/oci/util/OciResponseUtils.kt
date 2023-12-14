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

package com.tencent.bkrepo.oci.util

import com.tencent.bkrepo.common.api.constant.HttpHeaders.CONTENT_LENGTH
import com.tencent.bkrepo.common.api.constant.HttpHeaders.CONTENT_TYPE
import com.tencent.bkrepo.common.api.constant.HttpHeaders.RANGE
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.artifact.util.http.UrlFormatter
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.oci.constant.BLOB_UPLOAD_SESSION_ID
import com.tencent.bkrepo.oci.constant.DOCKER_API_VERSION
import com.tencent.bkrepo.oci.constant.DOCKER_CONTENT_DIGEST
import com.tencent.bkrepo.oci.constant.DOCKER_HEADER_API_VERSION
import com.tencent.bkrepo.oci.constant.DOCKER_UPLOAD_UUID
import com.tencent.bkrepo.oci.constant.HOST
import com.tencent.bkrepo.oci.constant.HTTP_FORWARDED_PROTO
import com.tencent.bkrepo.oci.constant.HTTP_PROTOCOL_HTTP
import com.tencent.bkrepo.oci.constant.HTTP_PROTOCOL_HTTPS
import com.tencent.bkrepo.oci.constant.OCI_API_PREFIX
import com.tencent.bkrepo.oci.pojo.digest.OciDigest
import com.tencent.bkrepo.oci.pojo.response.ResponseProperty
import org.springframework.http.HttpHeaders
import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URLDecoder
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.ws.rs.core.UriBuilder

/**
 * oci 响应工具
 */
object OciResponseUtils {
    private const val LOCAL_HOST = "localhost"

    fun getResponseURI(request: HttpServletRequest, enableHttps: Boolean, domain: String): URI {
        val hostHeaders = request.getHeaders(HOST)
        var host = LOCAL_HOST
        if (hostHeaders != null) {
            val headers = hostHeaders.toList()
            val parts = (headers[0] as String).split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            host = parts[0]
        }
        // domain为ip,port组合
        if (domain.split(":").size > 1) {
            host = domain
        }
        val builder = UriBuilder.fromPath(OCI_API_PREFIX).host(host).scheme(getProtocol(request, enableHttps))
        return builder.build()
    }

    /**
     * determine to return http protocol
     * prefix or https prefix
     */
    private fun getProtocol(request: HttpServletRequest, enableHttps: Boolean): String {
        if (enableHttps) return HTTP_PROTOCOL_HTTPS
        val protocolHeaders = request.getHeaders(HTTP_FORWARDED_PROTO) ?: return HTTP_PROTOCOL_HTTP
        return if (protocolHeaders.hasMoreElements()) {
            protocolHeaders.iterator().next() as String
        } else {
            HTTP_PROTOCOL_HTTPS
        }
    }

    private fun getResponseLocationURI(path: String, domain: String): String {
        return UrlFormatter.format(
            domain, path.trimStart('/')
        )
    }

    fun buildUploadResponse(domain: String, responseProperty: ResponseProperty, response: HttpServletResponse) {
        uploadResponse(
            domain = domain,
            response = response,
            responseProperty = responseProperty
        )
    }

    fun buildDownloadResponse(
        digest: OciDigest,
        response: HttpServletResponse,
        size: Long? = null,
        contentType: String = MediaTypes.APPLICATION_OCTET_STREAM
    ) {
        downloadResponse(
            response,
            digest.toString(),
            contentType,
            size
        )
    }

    fun buildDeleteResponse(response: HttpServletResponse) {
        deleteResponse(response)
    }

    private fun uploadResponse(
        domain: String,
        response: HttpServletResponse = HttpContextHolder.getResponse(),
        responseProperty: ResponseProperty
    ) {
        with(responseProperty) {
            val location = getResponseLocationURI(location!!, domain)
            response.status = status!!.value
            response.addHeader(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
            digest?.let {
                response.addHeader(DOCKER_CONTENT_DIGEST, digest.toString())
            }
            response.addHeader(HttpHeaders.LOCATION, location)
            uuid?.let {
                response.addHeader(BLOB_UPLOAD_SESSION_ID, uuid)
                response.addHeader(DOCKER_UPLOAD_UUID, uuid)
            }
            contentLength?.let {
                response.addHeader(CONTENT_LENGTH, contentLength.toString())
            }
            range?.let {
                response.addHeader(RANGE, "0-${range!! - 1}")
            }
        }
    }

    private fun downloadResponse(
        response: HttpServletResponse = HttpContextHolder.getResponse(),
        digest: String,
        mediaType: String,
        size: Long? = null
    ) {
        response.status = HttpStatus.OK.value
        response.addHeader(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
        response.addHeader(DOCKER_CONTENT_DIGEST, digest)
        response.addHeader(HttpHeaders.ETAG, digest)
        size?.let {
            response.addHeader(CONTENT_LENGTH, size.toString())
        }
        response.addHeader(CONTENT_TYPE, mediaType)
    }

    private fun deleteResponse(
        response: HttpServletResponse = HttpContextHolder.getResponse()
    ) {
        response.status = HttpStatus.ACCEPTED.value
    }

    /**
     * Parse a querystring into a map of key/value pairs.
     *
     * @param queryString the string to parse (without the '?')
     * @return key/value pairs mapping to the items in the querystring
     */
    fun parseQuerystring(queryString: String?): Map<String, String>? {
        val map: MutableMap<String, String> = HashMap()
        if (queryString == null || queryString == "") {
            return map
        }
        val params = queryString.split("&".toRegex()).toTypedArray()
        for (param in params) {
            try {
                val keyValuePair = param.split("=".toRegex(), 2).toTypedArray()
                val name: String = URLDecoder.decode(keyValuePair[0], "UTF-8")
                if (name === "") {
                    continue
                }
                val value = if (keyValuePair.size > 1) URLDecoder.decode(
                    keyValuePair[1], "UTF-8"
                ) else ""
                map[name] = value
            } catch (e: UnsupportedEncodingException) {
                // ignore this parameter if it can't be decoded
            }
        }
        return map
    }
}
