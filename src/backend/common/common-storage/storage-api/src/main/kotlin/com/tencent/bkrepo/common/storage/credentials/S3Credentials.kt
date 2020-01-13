package com.tencent.bkrepo.common.storage.credentials

/**
 * S3配置
 *
 * @author: carrypan
 * @date: 2019/12/26
 */
data class S3Credentials(
    var accessKey: String = "",
    var secretKey: String = "",
    var endpoint: String = "",
    var region: String = "",
    var bucket: String = ""
) : StorageCredentials() {
    companion object {
        const val type = "s3"
    }
}
