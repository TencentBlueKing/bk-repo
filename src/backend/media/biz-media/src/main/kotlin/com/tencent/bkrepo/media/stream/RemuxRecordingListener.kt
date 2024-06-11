package com.tencent.bkrepo.media.stream

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.common.api.constant.StringPool
import org.slf4j.LoggerFactory
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 将流数据转封装
 * */
class RemuxRecordingListener(
    private val path: String,
    scheduler: ThreadPoolTaskScheduler,
    private val type: MediaType,
    private val fileConsumer: FileConsumer,
) : AsyncStreamListener(scheduler) {

    /**
     * 流数据输出端
     * */
    private val pipeIn = PipedInputStream(16 * MB)

    /**
     * 流数据输入端
     * */
    private val pipeOut = PipedOutputStream(pipeIn)

    /**
     * 流临时保存文件
     * */
    private var tempFilePath: Path? = null

    /**
     * 流封装工具
     * */
    private var mux: Mux? = null

    /**
     * 录制器是否初始化
     * */
    private val initialized: AtomicBoolean = AtomicBoolean(false)

    /**
     * 文件保存名
     * */
    private var fileName: String? = null

    override fun handler(packet: StreamPacket) {
        pipeOut.write(packet.getData())
    }

    override fun init(name: String) {
        if (initialized.compareAndSet(false, true)) {
            super.init(name)
            val fileType = type.name.toLowerCase()
            fileName = "$name.$fileType"
            val tempFileName = StringPool.randomStringByLongValue(REMUX_PREFIX, ".$fileType")
            tempFilePath = Paths.get(path, tempFileName)
            mux = Mux(pipeIn, tempFilePath!!.toFile())
            val remuxFuture = threadPool.submit {
                mux!!.start()
            }
            if (remuxFuture.isDone) {
                throw IllegalStateException("Remux start error")
            }
        }
    }

    override fun stop() {
        try {
            if (initialized.get()) {
                super.stop()
                pipeOut.close()
                mux!!.stop()
                pipeIn.close()
                fileConsumer.accept(tempFilePath!!.toFile(), fileName!!)
            }
        } finally {
            Files.deleteIfExists(tempFilePath)
            logger.info("Delete remux temp file $tempFilePath")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RemuxRecordingListener::class.java)
        private const val MB = 1024 * 1024
        private const val REMUX_PREFIX = "remux_"
        private val threadPool = Executors.newCachedThreadPool(
            ThreadFactoryBuilder().setNameFormat("remux-work-%d").build(),
        )
    }
}
