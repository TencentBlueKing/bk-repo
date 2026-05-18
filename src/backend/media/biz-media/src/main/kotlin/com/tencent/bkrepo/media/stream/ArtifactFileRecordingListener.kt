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
    private val uploadId: String? = null,
) : AsyncStreamListener(scheduler) {

    /**
     * 是否为分块模式
     */
    private val blockMode: Boolean = uploadId != null

    /**
     * 标记当前录制是否正常完成
     * 可由外部设置，用于分块模式下区分异常断开和正常结束
     */
    @Volatile
    var isComplete: Boolean = false

    private lateinit var name: String
    private lateinit var clientMouseName: String
    private lateinit var hostAudioName: String

    override fun init(name: String) {
        val fileName = if (blockMode) uploadId!! else name
        this.name = "$fileName.${MediaType.MP4.name.lowercase(Locale.getDefault())}"
        this.clientMouseName = "CM_${fileName}.${MediaType.JSON.name.lowercase(Locale.getDefault())}"
        this.hostAudioName = "AU_${fileName}.${MediaType.AAC.name.lowercase(Locale.getDefault())}"
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

    /**
     * 停止录制（带 isComplete 标记）
     * 设置 isComplete 后执行停止和存储逻辑
     */
    override fun stop(endTime: Long, isComplete: Boolean) {
        this.isComplete = isComplete
        super.stop(endTime)
        storeFile(endTime)
    }

    private fun storeFile(endTime: Long) {
        try {
            artifactFile.finish()
            clientMouseArtifactFile.finish()
            hostAudioArtifactFile.finish()
            if (blockMode) {
                // 分块模式：调用 acceptBlock 进行分块存储
                val extraFiles = mutableMapOf<String, ChunkedArtifactFile>()
                if (clientMouseArtifactFile.getSize() != 0L) {
                    extraFiles[clientMouseName] = clientMouseArtifactFile
                }
                if (hostAudioArtifactFile.getSize() != 0L) {
                    extraFiles[hostAudioName] = hostAudioArtifactFile
                }
                fileConsumer.acceptBlock(
                    name = name,
                    file = artifactFile,
                    uploadId = uploadId!!,
                    isComplete = isComplete,
                    endTime = endTime,
                    extraFiles = extraFiles.ifEmpty { null }
                )
            } else {
                // 原有模式：存为完整制品
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
            }
        } finally {
            artifactFile.delete()
            clientMouseArtifactFile.delete()
            hostAudioArtifactFile.delete()
        }
    }
}
