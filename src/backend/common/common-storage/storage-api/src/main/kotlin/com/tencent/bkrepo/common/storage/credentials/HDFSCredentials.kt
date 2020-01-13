package com.tencent.bkrepo.common.storage.credentials

/**
 * HDFS配置
 *
 * @author: carrypan
 * @date: 2019/12/26
 */
data class HDFSCredentials(
    var uri: String = "localhost:9000",
    val username: String = ""
) : StorageCredentials() {
    companion object {
        const val type = "hdfs"
    }
}
