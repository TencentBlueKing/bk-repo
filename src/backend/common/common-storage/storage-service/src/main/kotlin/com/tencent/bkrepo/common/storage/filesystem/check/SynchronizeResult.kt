package com.tencent.bkrepo.common.storage.filesystem.check

data class SynchronizeResult(
    var totalCount: Long = 0,
    var synchronizedCount: Long = 0,
    var ignoredCount: Long = 0,
    var errorCount: Long = 0,
    var totalSize: Long = 0
)
