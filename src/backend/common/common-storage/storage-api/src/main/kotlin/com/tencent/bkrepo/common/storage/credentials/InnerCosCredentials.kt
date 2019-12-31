package com.tencent.bkrepo.common.storage.credentials

/**
 * inner cos 身份认证信息
 *
 * @author: carrypan
 * @date: 2019-09-17
 */
data class InnerCosCredentials(
    var secretId: String? = null,
    var secretKey: String? = null,
    var region: String? = null,
    var bucket: String? = null,
    var modId: Int? = null,
    var cmdId: Int? = null,
    var timeout: Float = 0.5F
) : StorageCredentials() {

    override fun toString(): String {
        return "InnerCosCredentials[region: $region, bucket: $bucket]"
    }

    companion object {
        const val type = "innercos"
    }
}
