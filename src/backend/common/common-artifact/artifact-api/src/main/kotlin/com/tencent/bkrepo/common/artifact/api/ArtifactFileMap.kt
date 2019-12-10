package com.tencent.bkrepo.common.artifact.api

/**
 * 构件文件map，用于保存多个构件文件
 */
class ArtifactFileMap {
    private val fileMap = mutableMapOf<String, ArtifactFile>()

    val size: Int = fileMap.size
    val keys: MutableSet<String> = fileMap.keys
    val values: MutableCollection<ArtifactFile> = fileMap.values
    val entries: MutableSet<MutableMap.MutableEntry<String, ArtifactFile>> = fileMap.entries

    operator fun get(key: String) = fileMap[key]
    operator fun set(key: String, value: ArtifactFile) {
        fileMap[key] = value
    }
}
