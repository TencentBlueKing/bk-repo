package com.tencent.bkrepo.archive.job.archive

import org.reactivestreams.Publisher

/**
 * 归档文件回调
 * */
interface ArchiveFileWrapperCallback {
    fun onArchiveFileWrapper(fileWrapper: ArchiveFileWrapper): Publisher<ArchiveFileWrapper>
}
