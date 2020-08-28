package com.tencent.bkrepo.common.artifact.repository.context

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactFileMap
import com.tencent.bkrepo.common.artifact.resolve.file.multipart.MultipartArtifactFile
import com.tencent.bkrepo.common.artifact.resolve.file.stream.OctetStreamArtifactFile

/**
 * 构件上传context
 */
class ArtifactUploadContext : ArtifactContext {

    private var artifactFileMap: ArtifactFileMap? = null
    private var artifactFile: ArtifactFile? = null

    constructor(artifactFile: ArtifactFile) {
        this.artifactFile = artifactFile
    }

    constructor(artifactFileMap: ArtifactFileMap) {
        this.artifactFileMap = artifactFileMap
    }

    /**
     * 根据[name]获取构件文件[ArtifactFile]
     * [name]为空则返回二进制流[OctetStreamArtifactFile]
     * [name]不为空则返回字段为[name]的[MultipartArtifactFile]
     * 如果[name]对应的构件文件不存在，则抛出[NullPointerException]
     */
    @Throws(NullPointerException::class)
    fun getArtifactFile(name: String? = null): ArtifactFile {
        return if (name.isNullOrBlank()) {
            artifactFile!! as OctetStreamArtifactFile
        } else {
            artifactFileMap!![name]!! as MultipartArtifactFile
        }
    }

    /**
     * 如果名为[name]的构件存在则返回`true`
     */
    fun checkArtifactExist(name: String? = null): Boolean {
        return if (name.isNullOrBlank()) {
            return artifactFile != null
        } else {
            artifactFileMap?.get(name) != null
        }
    }

    /**
     * 返回名为[name]的构件sha256校验值
     */
    fun getArtifactSha256(name: String? = null): String {
        return getArtifactFile(name).getFileSha256()
    }

    /**
     * 返回名为[name]的构件md5校验值
     */
    fun getArtifactMd5(name: String? = null): String {
        return getArtifactFile(name).getFileMd5()
    }
}
