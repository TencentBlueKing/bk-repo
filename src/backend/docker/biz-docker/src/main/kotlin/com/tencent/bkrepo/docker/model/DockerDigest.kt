package com.tencent.bkrepo.docker.model

import com.tencent.bkrepo.docker.constant.EMPTYSTR
import org.apache.commons.lang.StringUtils

/**
 * docker digest
 * @author: owenlxu
 * @date: 2019-10-15
 */
data class DockerDigest(val digest: String?) {

    var alg: String = EMPTYSTR
    var hex: String = EMPTYSTR

    init {
        val sepIndex = StringUtils.indexOf(digest, ":")
        require(sepIndex >= 0) { "could not find ':' in digest: $digest" }
        this.alg = StringUtils.substring(digest, 0, sepIndex)
        this.hex = StringUtils.substring(digest, sepIndex + 1)
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
        fun fromSha256(sha256: String): DockerDigest {
            return DockerDigest("sha256:$sha256")
        }
    }
}
