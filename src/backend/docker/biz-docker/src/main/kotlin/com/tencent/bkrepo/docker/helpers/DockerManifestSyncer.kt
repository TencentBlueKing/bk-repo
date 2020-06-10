package com.tencent.bkrepo.docker.helpers

import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.docker.artifact.DockerArtifactService
import com.tencent.bkrepo.docker.context.RequestContext
import com.tencent.bkrepo.docker.context.UploadContext
import com.tencent.bkrepo.docker.model.DockerBlobInfo
import com.tencent.bkrepo.docker.model.DockerDigest
import com.tencent.bkrepo.docker.model.ManifestMetadata
import com.tencent.bkrepo.docker.util.DockerSchemaUtils
import com.tencent.bkrepo.docker.util.DockerUtils
import org.apache.commons.lang.StringUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream

@Component
class DockerManifestSyncer constructor(repoService: DockerArtifactService) {

    val repo = repoService

    fun sync(info: ManifestMetadata, pathContext: RequestContext, tag: String): Boolean {
        logger.info("start to sync docker repository blobs")
        val manifestInfos = info.blobsInfo.iterator()

        while (manifestInfos.hasNext()) {
            val blobInfo = manifestInfos.next()
            logger.info("sync docker blob digest [${blobInfo.digest}]")
            if (blobInfo.digest != null && !this.isForeignLayer(blobInfo)) {
                val blobDigest = DockerDigest(blobInfo.digest!!)
                val blobFilename = blobDigest.filename()
                logger.info("blob file name digest [$blobFilename]")
                val tempBlobPath = "/${pathContext.dockerRepo}/_uploads/$blobFilename"
                val finalBlobPath = "/${pathContext.dockerRepo}/$tag/$blobFilename"
                if (!repo.exists(pathContext.projectId, pathContext.repoName, finalBlobPath)) {
                    if (DockerSchemaUtils.isEmptyBlob(blobDigest)) {
                        logger.debug("found empty layer [$blobFilename] in manifest for image [${pathContext.dockerRepo}] ,create blob in path [$finalBlobPath]")

                        val blobContent = ByteArrayInputStream(DockerSchemaUtils.EMPTY_BLOB_CONTENT)
                        val artifactFile = ArtifactFileFactory.build(blobContent)
                        blobContent.use {
                            repo.upload(
                                UploadContext(pathContext.projectId, pathContext.repoName, finalBlobPath).content(it).sha256(
                                    DockerSchemaUtils.emptyBlobDigest().getDigestHex()
                                ).artifactFile(artifactFile)
                            )
                        }
                    } else if (repo.exists(pathContext.projectId, pathContext.repoName, tempBlobPath)) {
                        moveBlobFromTempDir(pathContext.projectId, pathContext.repoName, tempBlobPath, finalBlobPath)
                    } else {
                        logger.debug("blob temp file [$tempBlobPath] doesn't exist in temp, try other tags")
                        val targetPath = "/${pathContext.dockerRepo}/$tag/$blobFilename"
                        if (!copyBlobFromFirstReadableDockerRepo(pathContext, blobFilename, targetPath)
                        ) {
                            logger.error("could not find temp blob [$tempBlobPath]")
                            return false
                        }
                        logger.info("blob [${blobDigest.filename()}] copy to [$finalBlobPath]")
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
        val blob = DockerUtils.findBlobGlobally(repo, pathContext, blobFilename) ?: run {
            return false
        }
        return copyBlob(pathContext.projectId, pathContext.repoName, blob.path, targetPath, blobFilename)
    }

    private fun copyBlob(
        projectId: String,
        repoName: String,
        sourcePath: String,
        targetPath: String,
        blobFilename: String
    ): Boolean {
        if (!StringUtils.equals(sourcePath, targetPath)) {
            logger.info("found {} in path {}, copy over to {}", blobFilename, sourcePath, targetPath)
            return repo.copy(projectId, repoName, sourcePath, targetPath)
        }
        return false
    }

    private fun moveBlobFromTempDir(
        projectId: String,
        repoName: String,
        tempBlobPath: String,
        finalBlobPath: String
    ) {
        logger.info("move temp blob from [$tempBlobPath] to [$finalBlobPath]")
        repo.move(projectId, repoName, tempBlobPath, finalBlobPath)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DockerManifestSyncer::class.java)
    }
}
