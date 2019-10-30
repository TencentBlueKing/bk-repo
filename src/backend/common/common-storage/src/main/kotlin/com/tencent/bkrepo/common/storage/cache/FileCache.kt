package com.tencent.bkrepo.common.storage.cache

import java.io.File
import java.io.InputStream

/**
 * 文件缓存接口
 *
 * @author: carrypan
 * @date: 2019/10/29
 */
interface FileCache {
    /**
     * 缓存文件
     */
    fun cache(path: String, filename: String, inputStream: InputStream): File

    /**
     * 获取缓存文件
     */
    fun get(path: String, filename: String): File?

    /**
     * 移除缓存文件
     */
    fun remove(path: String, filename: String)

    /**
     * 创建新缓存文件
     */
    fun touch(path: String, filename: String): File

    /**
     * 判断文件是否存在
     */
    fun exist(path: String, filename: String): Boolean

    /**
     * 清理缓存文件
     */
    fun onClean(): CleanResult

    data class CleanResult(val count: Long, val size: Long)
}
