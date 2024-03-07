package com.tencent.bkrepo.archive.job.archive

import com.tencent.bkrepo.archive.job.BaseJobSubscriber
import com.tencent.bkrepo.archive.model.TArchiveFile
import com.tencent.bkrepo.archive.service.ArchiveService

/**
 * 数据恢复订阅者
 * 处理具体数据恢复
 * */
class RestoreSubscriber(
    private val archiveService: ArchiveService,
) : BaseJobSubscriber<TArchiveFile>() {
    override fun doOnNext(value: TArchiveFile) {
        archiveService.restore(value)
    }

    override fun getSize(value: TArchiveFile): Long {
        return value.size
    }
}
