package com.tencent.bkrepo.archive.job.archive

import com.tencent.bkrepo.archive.job.BaseJobSubscriber
import com.tencent.bkrepo.archive.model.TArchiveFile
import com.tencent.bkrepo.archive.service.ArchiveService

/**
 * 归档任务订阅者
 * */
class ArchiveSubscriber(
    private val archiveService: ArchiveService,
) : BaseJobSubscriber<TArchiveFile>() {

    override fun doOnNext(value: TArchiveFile) {
        archiveService.archive(value)
    }

    override fun getSize(value: TArchiveFile): Long {
        return value.size
    }
}
