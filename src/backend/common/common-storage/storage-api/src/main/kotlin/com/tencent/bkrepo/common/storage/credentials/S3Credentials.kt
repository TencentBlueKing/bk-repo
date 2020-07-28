package com.tencent.bkrepo.common.storage.credentials

import com.tencent.bkrepo.common.storage.config.CacheProperties
import com.tencent.bkrepo.common.storage.config.UploadProperties

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
    var bucket: String = "",
    override var cache: CacheProperties = CacheProperties(),
    override var upload: UploadProperties = UploadProperties()
) : StorageCredentials(cache, upload) {
    companion object {
        const val type = "s3"
    }
}
