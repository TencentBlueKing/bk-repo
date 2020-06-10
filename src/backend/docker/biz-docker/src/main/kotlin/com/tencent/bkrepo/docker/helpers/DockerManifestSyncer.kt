package com.tencent.bkrepo.docker.helpers

import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.docker.artifact.DockerArtifactService
import com.tencent.bkrepo.docker.context.UploadContext
import com.tencent.bkrepo.docker.context.RequestContext
import com.tencent.bkrepo.docker.model.DockerBlobInfo
import com.tencent.bkrepo.docker.model.DockerDigest
import com.tencent.bkrepo.docker.model.ManifestMetadata
import com.tencent.bkrepo.docker.util.DockerSchemaUtils
import com.tencent.bkrepo.docker.util.DockerUtils
import org.apache.commons.lang.StringUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.IOException

@Component
class DockerManifestSyncer() {

    @Throws(IOException::class)
    fun sync(
        repo: DockerArtifactService,
        info: ManifestMetadata,
        pathContext: RequestContext,
        tag: String
    ): Boolean {
        logger.info("start to sync docker repository blobs")
        val manifestInfo = info.blobsInfo.iterator()

        while (manifestInfo.hasNext()) {
            val blobInfo = manifestInfo.next()
            logger.info("sync docker digest {}", blobInfo.digest)
            if (blobInfo.digest != null && !this.isForeignLayer(blobInfo)) {
                val blobDigest = DockerDigest(blobInfo.digest!!)
                val blobFilename = blobDigest.filename()
                logger.info(" blob file name digest {}", blobFilename)
                val tempBlobPath = "/${pathContext.dockerRepo}/_uploads/$blobFilename"
                val finalBlobPath = "/${pathContext.dockerRepo}/$tag/$blobFilename"
                if (!repo.exists(pathContext.projectId, pathContext.repoName, finalBlobPath)) {
                    if (DockerSchemaUtils.isEmptyBlob(blobDigest)) {
                        logger.debug(
                            "found empty layer {} in manifest for image {} ,create blob in path {}",
                            blobFilename,
                            pathContext.dockerRepo,
                            finalBlobPath
                        )
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
                        this.moveBlobFromTempDir(repo, pathContext.projectId, pathContext.repoName, tempBlobPath, finalBlobPath)
                    } else {
                        logger.debug("blob temp file '{}' doesn't exist in temp, try other tags", tempBlobPath)
                        val targetPath = "/${pathContext.dockerRepo}/$tag/$blobFilename"
                        if (!this.copyBlobFromFirstReadableDockerRepo(
                                repo,
                                pathContext.projectId,
                                pathContext.repoName,
                                pathContext.dockerRepo,
                                blobFilename,
                                targetPath
                            )
                        ) {
                            logger.error("could not find temp blob '{}'", tempBlobPath)
                            return false
                        }
                        logger.debug("blob {} copy to {}", blobDigest.filename(), finalBlobPath)
                    }
                }
            }
        }

        // this.removeUnreferencedBlobs(repo, "$dockerRepo/$tag", info)
        logger.debug("finish synv docker repository blobs")
        return true
    }

    private fun isForeignLayer(blobInfo: DockerBlobInfo): Boolean {
        return "application/vnd.docker.image.rootfs.foreign.diff.tar.gzip" == blobInfo.mediaType
    }

    protected fun copyBlobFromFirstReadableDockerRepo(
        repo: DockerArtifactService,
        projectId: String,
        repoName: String,
        dockerRepo: String,
        blobFilename: String,
        targetPath: String
    ): Boolean {
        val blob = DockerUtils.findBlobGlobally(repo, projectId, repoName, dockerRepo, blobFilename) ?: run {
            return false
        }
        return this.copyBlob(repo, projectId, repoName, blob.path, targetPath, blobFilename)
    }

    protected fun copyBlob(
        repo: DockerArtifactService,
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
        repo: DockerArtifactService,
        projectId: String,
        repoName: String,
        tempBlobPath: String,
        finalBlobPath: String
    ) {
        logger.info("move temp blob from '{}' to '{}'", tempBlobPath, finalBlobPath)
        repo.move(projectId, repoName, tempBlobPath, finalBlobPath)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DockerManifestSyncer::class.java)
    }
}
