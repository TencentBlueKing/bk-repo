package com.tencent.bkrepo.archive.job.archive

import com.tencent.bkrepo.archive.constant.XZ_SUFFIX
import com.tencent.bkrepo.archive.event.FileCompressedEvent
import com.tencent.bkrepo.archive.extensions.key
import com.tencent.bkrepo.archive.utils.ArchiveUtils
import com.tencent.bkrepo.common.api.util.HumanReadable
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.common.storage.monitor.measureThroughput
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.text.DecimalFormat
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono

/**
 * 文件压缩器
 * */
class FileCompressor(
    /**
     * 压缩内存限制，只有在只用xz cmd时生效
     * */
    private val memoryLimit: Long,
    /**
     * 工作路径
     * */
    private val workPath: String,
) : AbstractArchiveFileWrapperCallback() {

    /**
     * 压缩文件路径
     * */
    private val compressedPath: Path = Paths.get(workPath, "compressed")

    /**
     * 压缩比格式
     * */
    private val df = DecimalFormat("#.#")

    init {
        if (!Files.exists(compressedPath)) {
            Files.createDirectories(compressedPath)
        }
    }

    override fun process(fileWrapper: ArchiveFileWrapper): Mono<ArchiveFileWrapper> {
        return Mono.create {
            // 如果没有设置源文件，则跳过压缩
            if (fileWrapper.srcFilePath != null) {
                val size = fileWrapper.archiveFile.size
                val throughput = measureThroughput(size) { compress(fileWrapper) }
                // 发送压缩事件
                val event = FileCompressedEvent(
                    sha256 = fileWrapper.archiveFile.sha256,
                    uncompressed = size,
                    compressed = Files.size(fileWrapper.compressedFilePath!!),
                    throughput = throughput,
                )
                SpringContextUtils.publishEvent(event)
            }
            it.success(fileWrapper)
        }
    }

    /**
     * 压缩文件
     * 如果压缩成功，则设置压缩路径，上传器会根据压缩路径，进行上传压缩文件
     * */
    private fun compress(fileWrapper: ArchiveFileWrapper) {
        with(fileWrapper.archiveFile) {
            val key = "$sha256$XZ_SUFFIX"
            // 压缩文件
            logger.info("Start compress ${key()}.")
            val srcFilePath = fileWrapper.srcFilePath!!
            val xzFilePath = compressedPath.resolve(key)
            try {
                val throughput = measureThroughput(size) { compressFile(srcFilePath, xzFilePath) }
                // 打印压缩效果
                val newSize = Files.size(xzFilePath)
                val ratio = df.format((size - newSize.toDouble()) / size * 100)
                logger.info(
                    "Summary: sha256:$sha256 compressed:${newSize.humanReadable()} " +
                        "uncompressed:${size.humanReadable()} ratio:$ratio%, $throughput",
                )
                // 设置压缩文件路径
                fileWrapper.compressedFilePath = xzFilePath
            } catch (e: Exception) {
                val tempFile = srcFilePath.parent.resolve("${srcFilePath.fileName}$XZ_SUFFIX")
                Files.deleteIfExists(tempFile)
                logger.info("Delete xz temp file $srcFilePath")
            } finally {
                Files.deleteIfExists(srcFilePath)
                logger.info("Delete src file $srcFilePath")
            }
        }
    }

    /**
     * 压缩文件
     * 如果支持本地命令，则使用本地命令进行压缩
     * @param src 源文件
     * @param dst 目标文件
     * */
    private fun compressFile(src: Path, dst: Path) {
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
        Files.move(xzFilePath, dst, StandardCopyOption.REPLACE_EXISTING)
        logger.info("Move $xzFilePath to $dst.")
    }

    private fun Long.humanReadable(): String {
        return HumanReadable.size(this)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FileCompressor::class.java)
    }
}
