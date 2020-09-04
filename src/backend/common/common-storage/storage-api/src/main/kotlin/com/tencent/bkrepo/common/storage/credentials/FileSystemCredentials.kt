package com.tencent.bkrepo.common.storage.credentials

import com.tencent.bkrepo.common.storage.config.CacheProperties
import com.tencent.bkrepo.common.storage.config.UploadProperties

/**
 * 文件系统配置
 */
data class FileSystemCredentials(
    var path: String = "data/store",
    override var key: String? = null,
    override var cache: CacheProperties = CacheProperties(),
    override var upload: UploadProperties = UploadProperties()
) : StorageCredentials(key, cache, upload) {

    companion object {
        const val type = "filesystem"
    }
}
