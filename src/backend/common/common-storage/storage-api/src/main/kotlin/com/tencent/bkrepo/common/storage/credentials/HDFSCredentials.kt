package com.tencent.bkrepo.common.storage.credentials

import com.tencent.bkrepo.common.storage.config.CacheProperties
import com.tencent.bkrepo.common.storage.config.UploadProperties

/**
 * HDFS配置
 */
data class HDFSCredentials(
    var clusterMode: Boolean = false,
    var url: String = "hdfs://localhost:9000",
    var user: String = "root",
    var workingDirectory: String = "/",
    var clusterName: String = "localhost",
    var nameNodeMap: MutableMap<String, String> = mutableMapOf(),
    override var key: String? = null,
    override var cache: CacheProperties = CacheProperties(),
    override var upload: UploadProperties = UploadProperties()
) : StorageCredentials(key, cache, upload) {
    companion object {
        const val type = "hdfs"
    }
}
