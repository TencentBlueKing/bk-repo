package com.tencent.bkrepo.archive.job.archive

import com.tencent.bkrepo.archive.constant.XZ_SUFFIX
import com.tencent.bkrepo.archive.utils.ArchiveUtils
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executor

class XZUtils(
    private val memoryLimit: Long,
    private val executor: Executor,
) : CompressionUtils {
    private val scheduler = Schedulers.fromExecutor(executor)
    override fun compress(src: Path, target: Path): Mono<File> {
        return Mono.fromCallable {
            val xzFilePath = src.parent.resolve("${src.fileName}$XZ_SUFFIX")
            if (Files.exists(xzFilePath)) {
                Files.delete(xzFilePath)
            }
            /*
            * 使用多线程模式，且设置内存阈值，进行压缩
            * xz -T0 {file} --memory={memoryLimit}
            * */
            val cmd = mutableListOf(
                "xz",
                "-T0",
                src.toAbsolutePath().toString(),
                "--memory=$memoryLimit",
            )
            ArchiveUtils.runCmd(cmd)
            Files.move(xzFilePath, target, StandardCopyOption.REPLACE_EXISTING)
            logger.info("Move $xzFilePath to $target.")
            target.toFile()
        }.publishOn(scheduler)
    }

    override fun uncompress(target: Path, src: Path): Mono<File> {
        return Mono.fromCallable {
            val path = target.toAbsolutePath()
            val cmd = mutableListOf("xz", "-d", path.toString())
            ArchiveUtils.runCmd(cmd)
            val filePath = Paths.get(path.toString().removeSuffix(XZ_SUFFIX))
            Files.move(filePath, src)
            logger.info("Move $filePath to $src.")
            src.toFile()
        }.publishOn(scheduler)
    }

    override fun getSuffix(): String = XZ_SUFFIX

    override fun name(): String = NAME

    companion object {
        private val logger = LoggerFactory.getLogger(XZUtils::class.java)
        const val NAME = "xz"
    }
}
