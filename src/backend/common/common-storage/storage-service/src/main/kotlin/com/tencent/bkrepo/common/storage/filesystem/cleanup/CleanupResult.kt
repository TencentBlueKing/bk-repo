package com.tencent.bkrepo.common.storage.filesystem.cleanup

data class CleanupResult(
    var fileCount: Long = 0,
    var folderCount: Long = 0,
    var size: Long = 0
) {
    fun getTotal() = fileCount + folderCount
}
