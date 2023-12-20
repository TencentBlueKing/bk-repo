package com.tencent.bkrepo.archive.job.archive

import org.reactivestreams.Publisher
import reactor.core.publisher.Mono

/**
 * 抽象的文件包装回调处理
 *
 * 负责一些通用处理，如异常处理
 * */
abstract class AbstractArchiveFileWrapperCallback : ArchiveFileWrapperCallback {
    override fun onArchiveFileWrapper(fileWrapper: ArchiveFileWrapper): Publisher<ArchiveFileWrapper> {
        // 如果上游发生错误，则直接返回
        if (fileWrapper.throwable != null) {
            return Mono.just(fileWrapper)
        }
        return process(fileWrapper).onErrorResume {
            // 单个文件发生错误，将错误传递给下游，流继续执行。
            fileWrapper.throwable = it
            Mono.just(fileWrapper)
        }
    }

    /**
     * 处理文件
     * */
    abstract fun process(fileWrapper: ArchiveFileWrapper): Mono<ArchiveFileWrapper>
}
