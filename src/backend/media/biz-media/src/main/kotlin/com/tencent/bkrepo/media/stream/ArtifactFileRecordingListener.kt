package com.tencent.bkrepo.media.stream

import com.tencent.bkrepo.common.artifact.resolve.file.chunk.ChunkedArtifactFile
import com.tencent.bkrepo.media.TYPE_CLIENT_MOUSE_DATA
import com.tencent.bkrepo.media.TYPE_HOST_AUDIO_DATA
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.Locale

/**
 * 保存视频流和其关联的额外文件多构件
 * */
class ArtifactFileRecordingListener(
    private val artifactFile: ChunkedArtifactFile,
    private val clientMouseArtifactFile: ChunkedArtifactFile,
    private val hostAudioArtifactFile: ChunkedArtifactFile,
    private val fileConsumer: FileConsumer,
    scheduler: ThreadPoolTaskScheduler,
) : AsyncStreamListener(scheduler) {

    private lateinit var name: String
    private lateinit var clientMouseName: String
    private lateinit var hostAudioName: String

    override fun init(name: String) {
        this.name = "$name.${MediaType.MP4.name.lowercase(Locale.getDefault())}"
        this.clientMouseName = "CM_${name}.${MediaType.JSON.name.lowercase(Locale.getDefault())}"
        this.hostAudioName = "AU_${name}.${MediaType.AAC.name.lowercase(Locale.getDefault())}"
    }

    override fun handler(packet: StreamPacket) {
        when (packet.getDataType()) {
            TYPE_HOST_AUDIO_DATA -> {
                hostAudioArtifactFile.write(packet.getData())
            }

            TYPE_CLIENT_MOUSE_DATA -> {
                clientMouseArtifactFile.write(packet.getData())
            }

            else -> artifactFile.write(packet.getData())
        }
    }

    override fun stop(endTime: Long) {
        super.stop(endTime)
        storeFile(endTime)
    }

    private fun storeFile(endTime: Long) {
        try {
            artifactFile.finish()
            clientMouseArtifactFile.finish()
            hostAudioArtifactFile.finish()
            val extraFiles = mutableMapOf<String, ChunkedArtifactFile>()
            if (clientMouseArtifactFile.getSize() != 0L) {
                extraFiles[clientMouseName] = clientMouseArtifactFile
            }
            if (hostAudioArtifactFile.getSize() != 0L) {
                extraFiles[hostAudioName] = hostAudioArtifactFile
            }
            fileConsumer.accept(
                name = name,
                file = artifactFile,
                extraFiles = extraFiles.ifEmpty { null },
                endTime = endTime
            )
        } finally {
            artifactFile.delete()
            clientMouseArtifactFile.delete()
            hostAudioArtifactFile.delete()
        }
    }
}
