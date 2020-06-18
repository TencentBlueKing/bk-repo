package com.tencent.bkrepo.docker.helpers

import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.docker.artifact.DockerArtifactRepo
import com.tencent.bkrepo.docker.context.RequestContext
import com.tencent.bkrepo.docker.context.UploadContext
import com.tencent.bkrepo.docker.model.DockerBlobInfo
import com.tencent.bkrepo.docker.model.DockerDigest
import com.tencent.bkrepo.docker.model.ManifestMetadata
import com.tencent.bkrepo.docker.util.ContentUtil
import com.tencent.bkrepo.docker.util.ArtifactUtil
import org.apache.commons.lang.StringUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream

@Component
class DockerManifestSyncer constructor(repo: DockerArtifactRepo) {

    val repo = repo

    fun sync(info: ManifestMetadata, pathContext: RequestContext, tag: String): Boolean {
        logger.info("start to sync docker repository blobs")
        val manifestInfos = info.blobsInfo.iterator()

        while (manifestInfos.hasNext()) {
            val blobInfo = manifestInfos.next()
            logger.info("sync docker blob digest [$blobInfo]")
            if (blobInfo.digest != null && !this.isForeignLayer(blobInfo)) {
                val blobDigest = DockerDigest(blobInfo.digest!!)
                val blobFilename = blobDigest.fileName()
                logger.info("blob file name digest [$blobFilename]")
                val tempBlobPath = "/${pathContext.artifactName}/_uploads/$blobFilename"
                val finalBlobPath = "/${pathContext.artifactName}/$tag/$blobFilename"
                if (!repo.exists(pathContext.projectId, pathContext.repoName, finalBlobPath)) {
                    if (ContentUtil.isEmptyBlob(blobDigest)) {
                        logger.debug("found empty layer [$blobFilename] in manifest for image [${pathContext.artifactName}] ,create blob in path [$finalBlobPath]")
                        val blobContent = ByteArrayInputStream(ContentUtil.EMPTY_BLOB_CONTENT)
                        val artifactFile = ArtifactFileFactory.build(blobContent)
                            repo.upload(
                                UploadContext(pathContext.projectId, pathContext.repoName, finalBlobPath).sha256(
                                    ContentUtil.emptyBlobDigest().getDigestHex()
                                ).artifactFile(artifactFile)
                            )
                    } else if (repo.exists(pathContext.projectId, pathContext.repoName, tempBlobPath)) {
                        moveBlobFromTempDir(pathContext.projectId, pathContext.repoName, tempBlobPath, finalBlobPath)
                    } else {
                        logger.debug("blob temp file [$tempBlobPath] doesn't exist in temp, try other tags")
                        val targetPath = "/${pathContext.artifactName}/$tag/$blobFilename"
                        if (!copyBlobFromFirstReadableDockerRepo(pathContext, blobFilename, targetPath)) {
                            logger.error("could not find temp blob [$tempBlobPath]")
                            return false
                        }
                        logger.info("blob [$blobDigest] copy to [$finalBlobPath]")
                    }
                }
            }
        }
        logger.info("finish sync docker repository blobs")
        return true
    }

    private fun isForeignLayer(blobInfo: DockerBlobInfo): Boolean {
        return "application/vnd.docker.image.rootfs.foreign.diff.tar.gzip" == blobInfo.mediaType
    }

    private fun copyBlobFromFirstReadableDockerRepo(pathContext: RequestContext, blobFilename: String, targetPath: String): Boolean {
        val blob = ArtifactUtil.getBlobByName(repo, pathContext, blobFilename) ?: run {
            return false
        }
        return copyBlob(pathContext.projectId, pathContext.repoName, blob.fullPath, targetPath, blobFilename)
    }

    private fun copyBlob(
        projectId: String,
        repoName: String,
        sourcePath: String,
        targetPath: String,
        blobFilename: String
    ): Boolean {
        if (!StringUtils.equals(sourcePath, targetPath)) {
            logger.info("found [$blobFilename] in path [$sourcePath] copy over to [$targetPath]")
            return repo.copy(projectId, repoName, sourcePath, targetPath)
        }
        return false
    }

    private fun moveBlobFromTempDir(projectId: String, repoName: String, tempPath: String, finalPath: String) {
        logger.info("move temp blob from [$tempPath] to [$finalPath]")
        repo.move(projectId, repoName, tempPath, finalPath)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DockerManifestSyncer::class.java)
    }
}
