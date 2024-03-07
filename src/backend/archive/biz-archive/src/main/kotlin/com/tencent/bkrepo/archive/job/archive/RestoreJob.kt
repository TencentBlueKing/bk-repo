package com.tencent.bkrepo.archive.job.archive

import com.tencent.bkrepo.archive.ArchiveStatus
import com.tencent.bkrepo.archive.config.ArchiveProperties
import com.tencent.bkrepo.archive.job.Cancellable
import com.tencent.bkrepo.archive.model.TArchiveFile
import com.tencent.bkrepo.archive.service.ArchiveService
import com.tencent.bkrepo.archive.utils.ReactiveDaoUtils
import java.util.concurrent.TimeUnit
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux

/**
 * 数据恢复任务
 * */
@Component
class RestoreJob(
    private val archiveProperties: ArchiveProperties,
    private val archiveService: ArchiveService,
) : Cancellable {

    private var subscriber: RestoreSubscriber? = null

    /**
     * 获取待归档文件列表
     * */
    fun listFiles(): Flux<TArchiveFile> {
        val criteria = where(TArchiveFile::status).isEqualTo(ArchiveStatus.WAIT_TO_RESTORE)
        val query = Query.query(criteria)
            .limit(archiveProperties.queryLimit)
        return ReactiveDaoUtils.query(query, TArchiveFile::class.java)
    }

    @Scheduled(fixedDelay = 12, timeUnit = TimeUnit.HOURS)
    fun restore() {
        val subscriber = RestoreSubscriber(archiveService)
        listFiles().subscribe(subscriber)
        this.subscriber = subscriber
        subscriber.blockLast()
        this.subscriber = null
    }

    override fun cancel() {
        subscriber?.dispose()
    }
}
