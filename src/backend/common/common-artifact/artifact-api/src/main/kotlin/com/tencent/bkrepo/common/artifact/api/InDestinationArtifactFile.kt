package com.tencent.bkrepo.common.artifact.api

/**
 * 已存在于最终存储的的构件文件接口
 */
interface InDestinationArtifactFile: ArtifactFile {
    /**
     * 获取文件所在目录
     */
    fun getPath(): String

    /**
     * 获取文件名
     */
    fun getName(): String
}
