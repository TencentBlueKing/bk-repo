package com.tencent.bkrepo.common.storage

import java.io.InputStream

/**
 * 文件存储接口
 *
 * @author: carrypan
 * @date: 2019-09-09
 */
interface FileStorage {

    /**
     * 存储文件
     */
    fun store(hash: String, inputStream: InputStream)

    /**
     * 加载文件
     */
    fun load(hash: String): InputStream

    /**
     * 删除文件
     */
    fun delete(hash: String)

    /**
     * 判断是否存在
     */
    fun exist(hash: String): Boolean
}
