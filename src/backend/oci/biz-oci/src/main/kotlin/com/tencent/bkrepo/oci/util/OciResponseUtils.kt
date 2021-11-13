package com.tencent.bkrepo.oci.util

import com.tencent.bkrepo.oci.constant.DOCKER_API_VERSION
import com.tencent.bkrepo.oci.constant.DOCKER_CONTENT_DIGEST
import com.tencent.bkrepo.oci.constant.DOCKER_HEADER_API_VERSION
import com.tencent.bkrepo.oci.util.BlobUtils.emptyBlobDigest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

/**
 * oci 响应工具
 */
object OciResponseUtils {

	fun emptyBlobHeadResponse(): ResponseEntity<Any> {
		return ResponseEntity.ok().header(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
			.header(DOCKER_CONTENT_DIGEST, emptyBlobDigest())
			.header(HttpHeaders.CONTENT_LENGTH, "32")
			.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
			.build()
	}
}
