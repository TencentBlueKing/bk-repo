package com.tencent.bkrepo.common.storage.pojo

/**
 * 本地文件存储信息
 *
 * @author: carrypan
 * @date: 2019-09-17
 */
class LocalStorageCredentials : StorageCredentials() {
    var path: String = "upload"

    override fun toString(): String {
        return "LocalStorageCredentials[path: $path]"
    }

    companion object {
        const val type = "local"
    }
}
