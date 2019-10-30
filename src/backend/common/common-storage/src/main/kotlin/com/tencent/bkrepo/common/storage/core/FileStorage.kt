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
     * 存储文件
     */
    fun store(hash: String, inputStream: InputStream, clientCredentials: ClientCredentials? = null)

    /**
     * 加载文件
     */
    fun load(hash: String, clientCredentials: ClientCredentials? = null): File?

    /**
     * 删除文件
     */
    fun delete(hash: String, clientCredentials: ClientCredentials? = null)

    /**
     * 判断是否存在
     */
    fun exist(hash: String, clientCredentials: ClientCredentials? = null): Boolean
}
