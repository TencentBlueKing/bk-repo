package com.tencent.bkrepo.common.storage.credentials

/**
 * HDFS配置
 *
 * @author: carrypan
 * @date: 2019/12/26
 */
data class HDFSCredentials(
    var clusterMode: Boolean = false,
    var url: String = "hdfs://localhost:9000",
    var user: String = "",
    var clusterName: String = "",
    var nameNodeMap: MutableMap<String, String> = mutableMapOf()
) : StorageCredentials() {
    companion object {
        const val type = "hdfs"
    }
}
