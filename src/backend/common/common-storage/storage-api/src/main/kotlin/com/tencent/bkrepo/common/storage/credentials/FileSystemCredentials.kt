package com.tencent.bkrepo.common.storage.credentials

import com.tencent.bkrepo.common.storage.config.CacheProperties
import com.tencent.bkrepo.common.storage.config.UploadProperties

/**
 * 文件系统配置
 *
 * @author: carrypan
 * @date: 2019/12/26
 */
data class FileSystemCredentials(
    var path: String = "data/store",
    override var cache: CacheProperties = CacheProperties(),
    override var upload: UploadProperties = UploadProperties()
) : StorageCredentials(cache, upload) {

    companion object {
        const val type = "filesystem"
    }
}
