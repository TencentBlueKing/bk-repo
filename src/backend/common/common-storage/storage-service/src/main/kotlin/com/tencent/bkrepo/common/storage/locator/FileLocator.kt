package com.tencent.bkrepo.common.storage.locator

/**
 * 文件定位
 *
 * @author: carrypan
 * @date: 2019-09-09
 */
interface FileLocator {

    /**
     * 根据文件摘要定位文件存储位置
     */
    fun locate(digest: String): String
}
