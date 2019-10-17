package com.tencent.bkrepo.docker.v2.model

import org.apache.commons.lang.StringUtils

class DockerDigest(digest: String?) {
    var alg: String = ""
        private set
    var hex: String = ""
        private set

    init {
        val sepIndex = StringUtils.indexOf(digest, ":")
        require(sepIndex >= 0) { "could not find ':' in digest: $digest" }
        this.alg = StringUtils.substring(digest, 0, sepIndex)
        this.hex = StringUtils.substring(digest, sepIndex + 1)
    }

    fun filename(): String {
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
}
