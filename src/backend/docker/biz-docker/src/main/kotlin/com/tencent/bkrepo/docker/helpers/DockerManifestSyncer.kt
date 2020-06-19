package com.tencent.bkrepo.docker.helpers

import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.docker.artifact.DockerArtifactRepo
import com.tencent.bkrepo.docker.context.RequestContext
import com.tencent.bkrepo.docker.context.UploadContext
import com.tencent.bkrepo.docker.model.DockerBlobInfo
import com.tencent.bkrepo.docker.model.DockerDigest
import com.tencent.bkrepo.docker.model.ManifestMetadata
import com.tencent.bkrepo.docker.util.ArtifactUtil
import com.tencent.bkrepo.docker.util.ContentUtil
import org.apache.commons.lang.StringUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream

@Component
class DockerManifestSyncer constructor(repo: DockerArtifactRepo) {

    val repo = repo

    fun sync(info: ManifestMetadata, context: RequestContext, tag: String): Boolean {
        logger.info("start to sync docker repository blobs [${info.toString()}]")
        val manifestInfos = info.blobsInfo.iterator()
        while (manifestInfos.hasNext()) {
            val blobInfo = manifestInfos.next()
            logger.info("sync docker blob digest [${blobInfo.digest}]")
            if (blobInfo.digest != null && !this.isForeignLayer(blobInfo)) {
                val blobDigest = DockerDigest(blobInfo.digest!!)
                val fileName = blobDigest.fileName()
                val tempPath = "/${context.artifactName}/_uploads/$fileName"
                val finalPath = "/${context.artifactName}/$tag/$fileName"
                // check path exist
                if (repo.exists(context.projectId, context.repoName, finalPath)) {
                    logger.info("node exist in the repo [$finalPath]")
                    return true
                }
                // check is empty digest
                if (ContentUtil.isEmptyBlob(blobDigest)) {
                    with(context) {
                        logger.info("found empty layer [$fileName] in manifest  ,create blob in path [$finalPath]")
                        val blobContent = ByteArrayInputStream(ContentUtil.EMPTY_BLOB_CONTENT)
                        val artifactFile = ArtifactFileFactory.build(blobContent)
                        val uploadContext = UploadContext(projectId, repoName, finalPath)
                            .sha256(ContentUtil.emptyBlobDigest().getDigestHex()).artifactFile(artifactFile)
                        return repo.upload(uploadContext)
                    }
                }
                // temp path exist, move from it to final
                if (repo.exists(context.projectId, context.repoName, tempPath)) {
                    logger.info("move blob from the temp path [$context,$tempPath,$finalPath]")
                    return moveBlobFromTempDir(context, tempPath, finalPath)
                }
                // copy from other blob
                logger.info("blob temp file [$tempPath] doesn't exist in temp, try other tags")
                return copyBlobFromFirstRepo(context, fileName, finalPath)
            }
        }
        logger.warn("finish sync docker repository blobs,false")
        return false
    }

    private fun isForeignLayer(blobInfo: DockerBlobInfo): Boolean {
        return "application/vnd.docker.image.rootfs.foreign.diff.tar.gzip" == blobInfo.mediaType
    }

    private fun copyBlobFromFirstRepo(context: RequestContext, fileName: String, targetPath: String): Boolean {
        val blob = ArtifactUtil.getBlobByName(repo, context, fileName) ?: run {
            return false
        }
        val sourcePath = blob.fullPath
        if (StringUtils.equals(sourcePath, targetPath)) {
            return true
        }
        return repo.copy(context, sourcePath, targetPath)
    }

    private fun moveBlobFromTempDir(context: RequestContext, tempPath: String, finalPath: String): Boolean {
        return repo.move(context, tempPath, finalPath)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DockerManifestSyncer::class.java)
    }
}
