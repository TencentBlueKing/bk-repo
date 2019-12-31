package com.tencent.bkrepo.common.storage.credentials

/**
 * 文件系统配置
 *
 * @author: carrypan
 * @date: 2019/12/26
 */
data class FileSystemCredentials(var path: String = "data/store") : StorageCredentials() {
    companion object {
        const val type = "filesystem"
    }
}
