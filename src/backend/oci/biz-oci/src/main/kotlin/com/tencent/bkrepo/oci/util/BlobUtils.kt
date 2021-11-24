package com.tencent.bkrepo.oci.util

import com.tencent.bkrepo.oci.pojo.digest.OciDigest

/**
 * oci blob 工具类
 */
object BlobUtils {

	fun isEmptyBlob(digest: OciDigest): Boolean {
		return digest.toString() == emptyBlobDigest()
	}

	fun emptyBlobDigest(): String {
		return OciDigest("sha256:a3ed95caeb02ffe68cdd9fd84406680ae93d633cb16422d00e8a7c22955b46d4").toString()
	}

}
