package com.tencent.bkrepo.oci.pojo.digest

import com.tencent.bkrepo.common.api.constant.CharPool
import com.tencent.bkrepo.common.api.constant.StringPool
import org.apache.commons.lang.StringUtils

/**
 * OCI digest
 */
data class OciDigest(val digest: String? = null) {
	var alg: String = StringPool.EMPTY
	var hex: String = StringPool.EMPTY

	init {
		digest?.let {
			val sepIndex = StringUtils.indexOf(digest, CharPool.COLON)
			require(sepIndex >= 0) { "could not find ':' in digest: $digest" }
			this.alg = StringUtils.substring(digest, 0, sepIndex)
			this.hex = StringUtils.substring(digest, sepIndex + 1)
		}
	}

	fun fileName(): String {
		return this.alg + "__" + this.hex
	}

	fun getDigestAlg(): String {
		return this.alg
	}

	fun getDigestHex(): String {
		return this.hex
	}

	override fun toString(): String {
		return this.alg + ":" + this.hex
	}

	companion object {

		fun fromSha256(sha256: String): OciDigest {
			return OciDigest("sha256:$sha256")
		}

		fun isValid(reference: String): Boolean {
			val sepIndex = StringUtils.indexOf(reference, ":")
			if (sepIndex >= 0) return true
			return false
		}
	}
}
