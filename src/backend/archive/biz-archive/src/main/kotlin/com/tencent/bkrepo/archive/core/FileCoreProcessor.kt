package com.tencent.bkrepo.archive.core

import com.tencent.bkrepo.archive.ArchiveStatus
import com.tencent.bkrepo.archive.CompressStatus
import com.tencent.bkrepo.archive.config.ArchiveProperties
import com.tencent.bkrepo.archive.constant.MAX_EMIT_TIME
import com.tencent.bkrepo.archive.core.archive.ArchiveManager
import com.tencent.bkrepo.archive.core.compress.BDZipManager
import com.tencent.bkrepo.archive.model.TArchiveFile
import com.tencent.bkrepo.archive.model.TCompressFile
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.core.publisher.Sinks.EmissionException
import reactor.core.publisher.Sinks.EmitFailureHandler
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 文件核心处理器
 * 处理文件的归档和压缩任务，同时会根据任务类型对任务顺序进行重新编排。
 * 基于reactor中的sink实现，通过监听事件来触发任务执行，同时也支持任务的主动拉取。
 * */
@Component
class FileCoreProcessor(
    private val archiveManager: ArchiveManager,
    private val bdZipManager: BDZipManager,
    private val reactiveMongoTemplate: ReactiveMongoTemplate,
    archiveProperties: ArchiveProperties,
) {
    /**
     * 归档任务队列
     * */
    private val archiveFileQueue = PriorityBlockingQueue<PriorityWrapper<TArchiveFile>>()

    /**
     * 归档sink
     * */
    private val archiveSink = Sinks.many().unicast().onBackpressureBuffer(archiveFileQueue)

    /**
     * 压缩任务队列
     * */
    private val compressFileQueue = PriorityBlockingQueue<PriorityWrapper<TCompressFile>>()

    /**
     * 压缩sink
     * */
    private val compressSink = Sinks.many().unicast().onBackpressureBuffer(compressFileQueue)

    /**
     * 归档任务订阅
     * */
    private val archiveArchiveSubscriber = ActiveTaskSubscriber(
        archiveProperties.maxConcurrency,
        archiveProperties.pullInterval,
        archiveManager,
    ) { archiveFileQueue.offer(it) }

    /**
     * 压缩任务订阅
     * */
    private val activeCompressTaskSubscriber = ActiveTaskSubscriber(
        archiveProperties.gc.maxConcurrency,
        archiveProperties.pullInterval,
        bdZipManager,
    ) { compressFileQueue.offer(it) }

    /**
     * 处理器关闭标志
     * */
    private val closed = AtomicBoolean(false)

    /**
     * 普通事件序列号
     * */
    private val seq = AtomicInteger()

    /**
     * 优先事件序列号
     * */
    private val prioritySeq = AtomicInteger(Int.MIN_VALUE)

    init {
        archiveSink.asFlux()
            .doOnRequest { n -> requestArchiveFile(n) }
            .subscribe(archiveArchiveSubscriber)

        compressSink.asFlux()
            .doOnRequest { n -> requestCompressFile(n) }
            .subscribe(activeCompressTaskSubscriber)
    }

    @EventListener(FileEntityEvent::class)
    fun listen(event: FileEntityEvent) {
        logger.info("Received event: ${event.id}")
        val busyLooping = EmitFailureHandler.busyLooping(Duration.ofMillis(MAX_EMIT_TIME))
        try {
            when (val entity = event.entity) {
                is TArchiveFile -> {
                    val wrapper = PriorityWrapper(entity.getPriority(), entity)
                    archiveSink.emitNext(wrapper, busyLooping)
                }

                is TCompressFile -> {
                    val wrapper = PriorityWrapper(entity.getPriority(), entity)
                    compressSink.emitNext(wrapper, busyLooping)
                }

                else -> error("Not support entity.")
            }
        } catch (e: EmissionException) {
            logger.warn("Error when process event: ${event.id}", e)
        }
    }

    /**
     * 从db中按时间逆序，获取待归档文件
     * */
    private fun requestArchiveFile(request: Long) {
        if (request <= 0 || archiveFileQueue.size > 0) {
            return
        }
        logger.info("Request $request files to archive or restore.")
        val req = request.toInt()
        val deadLine = LocalDateTime.now().minusHours(COS_RESTORE_MIN_HOUR)
        val criteria = where(TArchiveFile::status).isEqualTo(ArchiveStatus.WAIT_TO_RESTORE)
            .and(TArchiveFile::lastModifiedDate.name).lt(deadLine)
        val query = Query.query(criteria)
            .with(Sort.by(Sort.Direction.DESC, TArchiveFile::lastModifiedDate.name))
            .limit(req)
        reactiveMongoTemplate.find(query, TArchiveFile::class.java).collectList().zipWhen {
            val remain = req - it.size
            if (remain > 0) {
                val query2 = Query.query(where(TArchiveFile::status).isEqualTo(ArchiveStatus.CREATED))
                    .with(Sort.by(Sort.Direction.DESC, TArchiveFile::lastModifiedDate.name))
                    .limit(remain)
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
            l1.forEach { f -> listen(FileEntityEvent(f.sha256, f)) }
        }
    }

    /**
     * 从db中按时间逆序，获取待压缩文件
     * */
    private fun requestCompressFile(request: Long) {
        if (request <= 0 || compressFileQueue.size > 0) {
            return
        }
        logger.info("Request $request files to compress or uncompress.")
        val req = request.toInt()
        val query = Query.query(where(TCompressFile::status).isEqualTo(CompressStatus.WAIT_TO_UNCOMPRESS))
            .with(Sort.by(Sort.Direction.DESC, TCompressFile::lastModifiedDate.name))
            .limit(req)
        reactiveMongoTemplate.find(query, TCompressFile::class.java).collectList().zipWhen {
            val remain = req - it.size
            if (remain > 0) {
                val query2 = Query.query(where(TCompressFile::status).isEqualTo(CompressStatus.CREATED))
                    .with(Sort.by(Sort.Direction.DESC, TCompressFile::lastModifiedDate.name))
                    .limit(remain)
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
            l1.forEach { f -> listen(FileEntityEvent(f.sha256, f)) }
        }
    }

    private fun TArchiveFile.getPriority(): Int {
        return when (status) {
            ArchiveStatus.CREATED -> seq.getAndIncrement()
            ArchiveStatus.WAIT_TO_RESTORE -> prioritySeq.getAndIncrement()
            else -> error("Not support status.")
        }
    }

    private fun TCompressFile.getPriority(): Int {
        return when (status) {
            CompressStatus.CREATED -> seq.getAndIncrement()
            CompressStatus.WAIT_TO_UNCOMPRESS -> prioritySeq.getAndIncrement()
            else -> error("Not support status.")
        }
    }

    /**
     * 关闭文件核心处理器，停止所有任务
     * */
    fun shutdown() {
        if (closed.compareAndSet(false, true)) {
            logger.info("Shutdown FileCoreProcessor")
            activeCompressTaskSubscriber.dispose()
            archiveArchiveSubscriber.dispose()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FileCoreProcessor::class.java)
        private const val COS_RESTORE_MIN_HOUR = 12L
    }
}
