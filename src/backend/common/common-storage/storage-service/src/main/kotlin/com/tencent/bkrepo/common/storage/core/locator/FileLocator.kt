package com.tencent.bkrepo.common.storage.core.locator

/**
 * 文件定位
 */
interface FileLocator {

    /**
     * 根据文件摘要定位文件存储位置
     */
    fun locate(digest: String): String
}
