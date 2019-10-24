package com.tencent.bkrepo.common.storage.core

import java.io.File
import java.io.InputStream

/**
 * 文件存储接口
 * hash可理解为文件id或文件摘要
 * Credentials代表存储身份，用作缓存key；Client为具体的存储客户端
 *
 * @author: carrypan
 * @date: 2019-09-09
 */
interface FileStorage {

    /**
     * 存储文件。
     */
    fun store(hash: String, inputStream: InputStream, credentials: ClientCredentials? = null)

    /**
     * 加载文件。记住InputStream用完后关闭
     */
    fun load(hash: String, credentials: ClientCredentials? = null): File?

    /**
     * 删除文件
     */
    fun delete(hash: String, credentials: ClientCredentials? = null)

    /**
     * 判断是否存在
     */
    fun exist(hash: String, credentials: ClientCredentials? = null): Boolean

    /**
     * 获取存储属性
     */
    fun getStorageProperties(): StorageProperties
}
