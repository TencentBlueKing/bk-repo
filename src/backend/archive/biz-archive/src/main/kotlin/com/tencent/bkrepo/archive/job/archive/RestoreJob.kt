package com.tencent.bkrepo.archive.job.archive

import com.tencent.bkrepo.archive.ArchiveStatus
import com.tencent.bkrepo.archive.config.ArchiveProperties
import com.tencent.bkrepo.archive.job.Cancellable
import com.tencent.bkrepo.archive.model.TArchiveFile
import com.tencent.bkrepo.archive.repository.ArchiveFileDao
import com.tencent.bkrepo.archive.repository.ArchiveFileRepository
import com.tencent.bkrepo.archive.utils.ReactiveDaoUtils
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.innercos.client.CosClient
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
    private val archiveFileRepository: ArchiveFileRepository,
    private val storageService: StorageService,
    private val archiveProperties: ArchiveProperties,
    private val archiveFileDao: ArchiveFileDao,
) : Cancellable {
    private val cosClient = CosClient(archiveProperties.cos)

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
        val subscriber = RestoreSubscriber(
            cosClient,
            archiveFileDao,
            storageService,
            archiveFileRepository,
            archiveProperties.workDir,
        )
        listFiles().subscribe(subscriber)
        this.subscriber = subscriber
        subscriber.blockLast()
        this.subscriber = null
    }

    override fun cancel() {
        subscriber?.dispose()
    }
}
