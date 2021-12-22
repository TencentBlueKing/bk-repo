package com.tencent.bkrepo.oci.util

import com.tencent.bkrepo.oci.pojo.digest.OciDigest
import javax.xml.bind.DatatypeConverter

/**
 * oci blob 工具类
 */
object BlobUtils {

	val EMPTY_BLOB_CONTENT: ByteArray =
		DatatypeConverter.parseHexBinary("1f8b080000096e8800ff621805a360148c5800080000ffff2eafb5ef00040000")

	fun isEmptyBlob(digest: OciDigest): Boolean {
		return digest.toString() == emptyBlobDigest()
	}

	fun emptyBlobDigest(): String {
		return OciDigest("sha256:a3ed95caeb02ffe68cdd9fd84406680ae93d633cb16422d00e8a7c22955b46d4").toString()
	}

}
