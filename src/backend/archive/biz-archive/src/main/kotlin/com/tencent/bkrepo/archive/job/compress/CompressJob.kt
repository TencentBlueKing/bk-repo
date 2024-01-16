package com.tencent.bkrepo.archive.job.compress

import com.tencent.bkrepo.archive.CompressStatus
import com.tencent.bkrepo.archive.config.ArchiveProperties
import com.tencent.bkrepo.archive.job.Cancellable
import com.tencent.bkrepo.archive.model.TCompressFile
import com.tencent.bkrepo.archive.service.CompressService
import com.tencent.bkrepo.archive.utils.ReactiveDaoUtils
import java.util.concurrent.TimeUnit
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux

@Component
class CompressJob(
    private val archiveProperties: ArchiveProperties,
    private val compressService: CompressService,
) : Cancellable {

    private var subscriber: CompressSubscriber? = null

    fun listFiles(): Flux<TCompressFile> {
        val criteria = where(TCompressFile::status).isEqualTo(CompressStatus.CREATED)
        val query = Query.query(criteria)
            .limit(archiveProperties.queryLimit)
        return ReactiveDaoUtils.query(query, TCompressFile::class.java)
    }

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.HOURS)
    fun compress() {
        val subscriber = CompressSubscriber(compressService)
        listFiles().subscribe(subscriber)
        this.subscriber = subscriber
        subscriber.blockLast()
        this.subscriber = null
    }

    override fun cancel() {
        subscriber?.dispose()
    }
}
