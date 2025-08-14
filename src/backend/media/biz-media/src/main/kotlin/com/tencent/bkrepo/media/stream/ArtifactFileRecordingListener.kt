package com.tencent.bkrepo.media.stream

import com.tencent.bkrepo.common.artifact.resolve.file.chunk.ChunkedArtifactFile
import com.tencent.bkrepo.media.TYPE_CLIENT_MOUSE_DATA
import com.tencent.bkrepo.media.TYPE_VIDEO_DATA
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.Locale

/**
 * 保存流为制品构件
 * */
class ArtifactFileRecordingListener(
    private val artifactFile: ChunkedArtifactFile,
    private val clientMouseArtifactFile: ChunkedArtifactFile,
    private val fileConsumer: FileConsumer,
    private val type: MediaType,
    scheduler: ThreadPoolTaskScheduler,
) : AsyncStreamListener(scheduler) {

    private lateinit var name: String
    private lateinit var clientMouseName: String

    override fun init(name: String) {
        this.name = "$name.${type.name.lowercase(Locale.getDefault())}"
        this.clientMouseName = "CM_${name}.${MediaType.JSON.name.lowercase(Locale.getDefault())}"
    }

    override fun handler(packet: StreamPacket) {
        if (packet.getDataType() == TYPE_CLIENT_MOUSE_DATA) {
            clientMouseArtifactFile.write(packet.getData())
        } else {
            artifactFile.write(packet.getData())
        }
    }

    override fun stop() {
        super.stop()
        storeFile()
    }

    private fun storeFile() {
        try {
            artifactFile.finish()
            clientMouseArtifactFile.finish()
            if (clientMouseArtifactFile.getSize() == 0L) {
                fileConsumer.accept(name, artifactFile, null)
            } else {
                fileConsumer.accept(name, artifactFile, mapOf(clientMouseName to clientMouseArtifactFile))
            }
        } finally {
            artifactFile.delete()
            clientMouseArtifactFile.delete()
        }
    }
}
