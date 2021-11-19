package com.tencent.bkrepo.oci.util

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.oci.constant.DOCKER_API_VERSION
import com.tencent.bkrepo.oci.constant.DOCKER_CONTENT_DIGEST
import com.tencent.bkrepo.oci.constant.DOCKER_HEADER_API_VERSION
import com.tencent.bkrepo.oci.constant.HTTP_FORWARDED_PROTO
import com.tencent.bkrepo.oci.constant.HTTP_PROTOCOL_HTTP
import com.tencent.bkrepo.oci.constant.HTTP_PROTOCOL_HTTPS
import com.tencent.bkrepo.oci.util.BlobUtils.emptyBlobDigest
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import java.net.URI
import javax.ws.rs.core.UriBuilder

/**
 * oci 响应工具
 */
object OciResponseUtils {

	private val logger = LoggerFactory.getLogger(OciResponseUtils::class.java)

	private const val LOCAL_HOST = "localhost"

	fun emptyBlobHeadResponse() {
		val response = HttpContextHolder.getResponse()
		response.status = HttpStatus.OK.value
		response.addHeader(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
		response.addHeader(DOCKER_CONTENT_DIGEST, emptyBlobDigest())
		response.addHeader(HttpHeaders.CONTENT_LENGTH, "32")
		response.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
	}

	fun getResponseLocationURI(path: String): URI {
		val hostHeaders = HttpContextHolder.getRequest().getHeader("Host")
		var host = LOCAL_HOST
		var port: Int? = null
		if (hostHeaders != null && hostHeaders.isNotEmpty()) {
			val parts = hostHeaders.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
			host = parts[0]
			if (parts.size > 1) {
				port = Integer.valueOf(parts[1])
			}
		}

		val builder = UriBuilder.fromPath("v2/$path").host(host).scheme(getProtocol())
		port?.let {
			builder.port(port)
		}
		return builder.build()
	}

	/**
	 * determine to return http protocol
	 * prefix or https prefix
	 */
	private fun getProtocol(): String {
		val protocolHeaders = HttpContextHolder.getRequest().getHeaders(HTTP_FORWARDED_PROTO)
		if (!protocolHeaders.hasMoreElements()) {
			return HTTP_PROTOCOL_HTTP
		}
		return if (protocolHeaders.hasMoreElements()) {
			protocolHeaders.iterator().next() as String
		} else {
			logger.debug("X-Forwarded-Proto does not exist, return https.")
			HTTP_PROTOCOL_HTTPS
		}
	}
}
