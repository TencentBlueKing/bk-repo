package com.tencent.bkrepo.archive.core.provider

import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CacheFileProviderProxy<T>(
    private val provider: FileProvider<T>,
    private val expire: Duration,
    private val cachePath: Path,
) : FileProviderProxy<T>(provider) {

    private var nextTime = System.currentTimeMillis()
    private var minDelay = 1000L

    init {
        monitorExecutor.schedule(this::cleanup, 0, TimeUnit.SECONDS)
    }

    override fun get(param: T): Mono<File> {
        val key = provider.key(param)
        val filePath = cachePath.resolve(key)
        if (Files.exists(filePath)) {
            return Mono.just(filePath.toFile())
        }
        return provider.get(param).map {
            Files.move(it.toPath(), filePath)
            logger.info("Success cache file $key")
            filePath.toFile()
        }
    }

    private fun cleanup() {
        deleteAfterAccess()
        val delay = (nextTime - System.currentTimeMillis()).coerceAtLeast(minDelay)
        monitorExecutor.schedule(this::cleanup, delay, TimeUnit.MILLISECONDS)
    }

    private fun deleteAfterAccess() {
        var minLastAccessTime = System.currentTimeMillis()
        Files.list(cachePath).use {
            it.forEach { path ->
                val attributes = Files.readAttributes(cachePath, BasicFileAttributes::class.java)
                val lastAccessTime = attributes.lastAccessTime().toMillis()
                val expiredTime = System.currentTimeMillis() - expire.toMillis()
                if (Files.isRegularFile(path) && lastAccessTime <= expiredTime) {
                    Files.deleteIfExists(path)
                    logger.info("Delete cache file ${path.toAbsolutePath()}.")
                } else if (lastAccessTime < minLastAccessTime) { // 最早的未过期文件
                    minLastAccessTime = lastAccessTime
                }
            }
        }
        nextTime = minLastAccessTime + expire.toMillis() // 下一次最早过期文件
    }

    companion object {
        private val monitorExecutor = Executors.newSingleThreadScheduledExecutor()
        private val logger = LoggerFactory.getLogger(CacheFileProviderProxy::class.java)
    }
}
