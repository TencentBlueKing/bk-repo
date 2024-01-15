package com.tencent.bkrepo.archive.job.compress

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.archive.CompressStatus
import com.tencent.bkrepo.archive.config.ArchiveProperties
import com.tencent.bkrepo.archive.job.Cancellable
import com.tencent.bkrepo.archive.model.TCompressFile
import com.tencent.bkrepo.archive.repository.CompressFileDao
import com.tencent.bkrepo.archive.repository.CompressFileRepository
import com.tencent.bkrepo.archive.utils.ArchiveUtils
import com.tencent.bkrepo.archive.utils.ReactiveDaoUtils
import com.tencent.bkrepo.common.storage.core.StorageService
import java.util.concurrent.TimeUnit
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux

@Component
class UncompressJob(
    private val archiveProperties: ArchiveProperties,
    private val compressFileDao: CompressFileDao,
    private val storageService: StorageService,
    private val compressFileRepository: CompressFileRepository,
) : Cancellable {

    private val uncompressThreadPool = ArchiveUtils.newFixedAndCachedThreadPool(
        archiveProperties.ioThreads,
        ThreadFactoryBuilder().setNameFormat("storage-uncompress-%d").build(),
    )
    private var subscriber: UncompressSubscriber? = null

    fun listFiles(): Flux<TCompressFile> {
        val criteria = where(TCompressFile::status).isEqualTo(CompressStatus.WAIT_TO_UNCOMPRESS)
        val query = Query.query(criteria)
            .limit(archiveProperties.queryLimit)
        return ReactiveDaoUtils.query(query, TCompressFile::class.java)
    }

    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.SECONDS)
    fun uncompress() {
        val subscriber = UncompressSubscriber(
            compressFileDao,
            compressFileRepository,
            storageService,
            uncompressThreadPool,
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
