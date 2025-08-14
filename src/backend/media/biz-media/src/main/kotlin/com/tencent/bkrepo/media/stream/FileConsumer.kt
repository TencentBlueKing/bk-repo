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
    fun accept(file: File, name: String)

    fun accept(name: String, file: ArtifactFile, extraFiles: Map<String, ArtifactFile>?)
}
