package com.tencent.bkrepo.media.stream

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import java.io.File
import java.util.function.Consumer

/**
 * 文件消费者
 * */
interface FileConsumer : Consumer<File> {

    /**
     * 消费文件
     * @param file 待消费文件
     * @param name 文件名
     * */
    fun accept(file: File, name: String, endTime: Long)

    fun accept(name: String, file: ArtifactFile, extraFiles: Map<String, ArtifactFile>?, endTime: Long)

    /**
     * 分块存储文件（用于重连场景拼接视频）
     * @param name 文件名
     * @param file 文件
     * @param uploadId 上传ID，同一session使用相同uploadId关联多个分块
     * @param isComplete 是否完成（true=正常结束需合并分块并触发转码，false=异常断开仅存分块）
     * @param endTime 结束时间
     */
    fun acceptBlock(
        name: String,
        file: ArtifactFile,
        uploadId: String,
        isComplete: Boolean,
        endTime: Long,
        extraFiles: Map<String, ArtifactFile>? = null
    ) {
        // 默认实现：直接走原有accept逻辑，不感知分块
        accept(name, file, extraFiles, endTime)
    }
}
