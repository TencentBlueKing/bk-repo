package com.tencent.bkrepo.common.storage.filesystem.cleanup

import java.io.File

/**
 * 文件过期解析器，支持自定义文件淘汰策略
 * */
interface FileExpireResolver {
    /**
     * 文件是否过期
     * @return true过期，否则为false
     * */
    fun isExpired(file: File): Boolean
}
