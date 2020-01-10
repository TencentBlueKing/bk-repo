package com.tencent.bkrepo.common.storage.filesystem.cleanup

/**
 *
 * @author: carrypan
 * @date: 2020/1/6
 */
data class CleanupResult(
    var fileCount: Long = 0,
    var folderCount: Long = 0,
    var size: Long = 0
) {
    fun getTotal() = fileCount + folderCount
}
