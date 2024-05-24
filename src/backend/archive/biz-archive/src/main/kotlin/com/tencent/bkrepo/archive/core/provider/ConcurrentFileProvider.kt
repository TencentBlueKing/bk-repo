package com.tencent.bkrepo.archive.core.provider

import org.slf4j.LoggerFactory
import reactor.core.publisher.BaseSubscriber
import reactor.core.publisher.Mono
import reactor.core.publisher.MonoSink
import java.io.File
import java.lang.IllegalStateException
import java.util.concurrent.ConcurrentHashMap

class ConcurrentFileProvider<T>(
    private val provider: FileProvider<T>,
) : FileProviderProxy<T>(provider) {

    private val sinksMap = ConcurrentHashMap<String, SinkListSubscriber>()

    override fun get(param: T): Mono<File> {
        return Mono.create { sink ->
            val key = provider.key(param)
            var create = false
            var added = false
            var subscriber: SinkListSubscriber? = null
            var retry = 0
            while (!added && retry++ < MAX_RETRY_TIMES) {
                logger.info("Start init $key")
                subscriber = sinksMap.computeIfAbsent(key) {
                    logger.info("Initialize $key")
                    create = true
                    SinkListSubscriber(key)
                }
                added = subscriber.add(sink)
            }
            if (!added) {
                sink.error(IllegalStateException("Can't init $key"))
            }
            if (create) {
                provider.get(param).subscribe(subscriber!!)
            }
        }
    }

    private inner class SinkListSubscriber(val key: String) : BaseSubscriber<File>() {
        private val sinkList = mutableListOf<MonoSink<File>>()
        override fun hookOnNext(value: File) {
            synchronized(this) {
                sinksMap.remove(key)
                logger.info("Finished $key")
                sinkList.forEach { it.success(value) }
            }
        }

        override fun hookOnError(throwable: Throwable) {
            synchronized(this) {
                sinkList.forEach { it.error(throwable) }
            }
        }

        fun add(item: MonoSink<File>): Boolean {
            synchronized(this) {
                if (!sinksMap.containsKey(key)) {
                    return false
                }
                sinkList.add(item)
                return true
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ConcurrentFileProvider::class.java)
        private const val MAX_RETRY_TIMES = 3
    }
}
