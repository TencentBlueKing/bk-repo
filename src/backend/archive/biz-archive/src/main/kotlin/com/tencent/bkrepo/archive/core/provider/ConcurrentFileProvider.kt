package com.tencent.bkrepo.archive.core.provider

import org.slf4j.LoggerFactory
import reactor.core.publisher.BaseSubscriber
import reactor.core.publisher.Mono
import reactor.core.publisher.MonoSink
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class ConcurrentFileProvider<T>(
    private val provider: FileProvider<T>,
) : FileProviderProxy<T>(provider) {

    private val sinksMap = ConcurrentHashMap<String, SinkListSubscriber>()

    override fun get(param: T): Mono<File> {
        return Mono.create { sink ->
            val key = provider.key(param)
            var create = false
            val subscriber = sinksMap.computeIfAbsent(key) {
                logger.info("Initialize $key")
                create = true
                SinkListSubscriber(mutableListOf())
            }
            subscriber.sinkList.add(sink)
            if (create) {
                provider.get(param).doFinally {
                    logger.info("Remove $key")
                }.subscribe(subscriber)
            }
        }
    }

    private inner class SinkListSubscriber(val sinkList: MutableList<MonoSink<File>>) :
        BaseSubscriber<File>() {
        override fun hookOnNext(value: File) {
            sinkList.forEach { it.success(value) }
        }

        override fun hookOnError(throwable: Throwable) {
            sinkList.forEach { it.error(throwable) }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ConcurrentFileProvider::class.java)
    }
}
