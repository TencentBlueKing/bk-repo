package com.tencent.bkrepo.archive.job

import com.tencent.bkrepo.archive.ArchiveStatus
import com.tencent.bkrepo.archive.CompressStatus
import com.tencent.bkrepo.archive.config.ArchiveProperties
import com.tencent.bkrepo.archive.constant.MAX_EMIT_TIME
import com.tencent.bkrepo.archive.job.archive.ArchiveManager
import com.tencent.bkrepo.archive.job.compress.BDZipManager
import com.tencent.bkrepo.archive.model.TArchiveFile
import com.tencent.bkrepo.archive.model.TCompressFile
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.core.publisher.Sinks.EmitFailureHandler
import java.time.Duration
import java.time.LocalDateTime

@Component
class FileCoreProcessor(
    archiveManager: ArchiveManager,
    bdZipManager: BDZipManager,
    private val reactiveMongoTemplate: ReactiveMongoTemplate,
    archiveProperties: ArchiveProperties,
) {
    private val archiveSink = Sinks.many().unicast().onBackpressureBuffer<TArchiveFile>()
    private val compressSink = Sinks.many().unicast().onBackpressureBuffer<TCompressFile>()
    private val busyLooping = EmitFailureHandler.busyLooping(Duration.ofMillis(MAX_EMIT_TIME))
    private val archiveFileSubscriber = ActivelyPullSubscriber(archiveManager, archiveProperties.pullInterval)
    private val activelyPullSubscriber = ActivelyPullSubscriber(bdZipManager, archiveProperties.pullInterval)

    init {
        archiveSink.asFlux()
            .doOnRequest { n -> requestArchiveFile(n) }
            .subscribe(archiveFileSubscriber)

        compressSink.asFlux()
            .doOnRequest { n -> requestCompressFile(n) }
            .subscribe(activelyPullSubscriber)
    }

    @EventListener(FileEntityEvent::class)
    fun listen(event: FileEntityEvent) {
        when (val entity = event.entity) {
            is TArchiveFile -> {
                archiveSink.emitNext(entity, busyLooping)
            }

            is TCompressFile -> {
                compressSink.emitNext(entity, busyLooping)
            }

            else -> error("Not support entity.")
        }
    }

    private fun requestArchiveFile(num: Long) {
        if (num <= 0) {
            return
        }
        val deadLine = LocalDateTime.now().minusHours(COS_RESTORE_MIN_HOUR)
        val criteria = where(TArchiveFile::status).isEqualTo(ArchiveStatus.WAIT_TO_RESTORE)
            .and(TArchiveFile::lastModifiedDate.name).lt(deadLine)
        val query = Query.query(criteria)
            .limit(num.toInt())
        reactiveMongoTemplate.find(query, TArchiveFile::class.java).collectList().zipWhen {
            val remain = num - it.size
            if (remain > 0) {
                val query2 = Query.query(where(TArchiveFile::status).isEqualTo(ArchiveStatus.CREATED))
                    .limit(remain.toInt())
                reactiveMongoTemplate.find(query2, TArchiveFile::class.java).collectList()
            } else {
                Mono.just(emptyList())
            }
        }.subscribe {
            val l1 = it.t1
            logger.info("Find ${l1.size} files to restore")
            val l2 = it.t2
            logger.info("Find ${l2.size} files to archive")
            l1.addAll(l2)
            l1.forEach { f -> archiveSink.emitNext(f, busyLooping) }
        }
    }

    private fun requestCompressFile(num: Long) {
        if (num <= 0) {
            return
        }
        val query = Query.query(where(TCompressFile::status).isEqualTo(CompressStatus.WAIT_TO_UNCOMPRESS))
            .limit(num.toInt())
        reactiveMongoTemplate.find(query, TCompressFile::class.java).collectList().zipWhen {
            val remain = num - it.size
            if (remain > 0) {
                val query2 = Query.query(where(TCompressFile::status).isEqualTo(CompressStatus.CREATED))
                    .limit(remain.toInt())
                reactiveMongoTemplate.find(query2, TCompressFile::class.java).collectList()
            } else {
                Mono.just(emptyList())
            }
        }.subscribe {
            val l1 = it.t1
            logger.info("Find ${l1.size} files to uncompress")
            val l2 = it.t2
            logger.info("Find ${l2.size} files to compress")
            l1.addAll(l2)
            l1.forEach { f -> compressSink.emitNext(f, busyLooping) }
        }
    }

    fun shutdown() {
        logger.info("Shutdown FileCoreProcessor")
        archiveFileSubscriber.dispose()
        activelyPullSubscriber.dispose()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FileCoreProcessor::class.java)
        private const val COS_RESTORE_MIN_HOUR = 12L
    }
}
