package com.tencent.bkrepo.media.stream

import com.tencent.bkrepo.common.artifact.resolve.file.chunk.ChunkedArtifactFile
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.Locale

/**
 * 保存流为制品构件
 * */
class ArtifactFileRecordingListener(
    private val artifactFile: ChunkedArtifactFile,
    private val fileConsumer: FileConsumer,
    private val type: MediaType,
    scheduler: ThreadPoolTaskScheduler,
) : AsyncStreamListener(scheduler) {

    private lateinit var name: String

    override fun init(name: String) {
        this.name = "$name.${type.name.lowercase(Locale.getDefault())}"
    }

    override fun handler(packet: StreamPacket) {
        artifactFile.write(packet.getData())
    }

    override fun stop() {
        super.stop()
        storeFile()
    }

    private fun storeFile() {
        try {
            artifactFile.finish()
            fileConsumer.accept(artifactFile, name)
        } finally {
            artifactFile.delete()
        }
    }
}
