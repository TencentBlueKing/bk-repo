package com.tencent.bkrepo.docker.helpers

import com.tencent.bkrepo.common.artifact.file.ArtifactFileFactory
import com.tencent.bkrepo.docker.artifact.DockerArtifactoryService
import com.tencent.bkrepo.docker.context.UploadContext
import com.tencent.bkrepo.docker.model.DockerBasicPath
import com.tencent.bkrepo.docker.model.DockerBlobInfo
import com.tencent.bkrepo.docker.model.DockerDigest
import com.tencent.bkrepo.docker.model.ManifestMetadata
import com.tencent.bkrepo.docker.util.DockerSchemaUtils
import com.tencent.bkrepo.docker.util.DockerUtils
import java.io.ByteArrayInputStream
import java.io.IOException
import org.apache.commons.lang.StringUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class DockerManifestSyncer() {

    @Throws(IOException::class)
    fun sync(
        repo: DockerArtifactoryService,
        info: ManifestMetadata,
        path: DockerBasicPath,
        tag: String
    ): Boolean {
        log.info("start to sync docker repository blobs")
        val manifestInfo = info.blobsInfo.iterator()

        while (manifestInfo.hasNext()) {
            val blobInfo = manifestInfo.next()
            log.info(" docker digest {}", blobInfo.digest)
            if (blobInfo.digest != null && !this.isForeignLayer(blobInfo)) {
                val blobDigest = DockerDigest(blobInfo.digest!!)
                val blobFilename = blobDigest.filename()
                log.info(" blob file name digest {}", blobFilename)
                val tempBlobPath = "/${path.dockerRepo}/_uploads/$blobFilename"
                val finalBlobPath = "/${path.dockerRepo}/$tag/$blobFilename"
                if (!repo.exists(path.projectId, path.repoName, finalBlobPath)) {
                    if (DockerSchemaUtils.isEmptyBlob(blobDigest)) {
                        log.debug(
                            "found empty layer {} in manifest for image {} ,create blob in path {}",
                            blobFilename,
                            path.dockerRepo,
                            finalBlobPath
                        )
                        val artifactFile = ArtifactFileFactory.build()
                        val blobContent = ByteArrayInputStream(DockerSchemaUtils.EMPTY_BLOB_CONTENT)
                        blobContent.use {
                            repo.upload(
                                UploadContext(path.projectId, path.repoName, finalBlobPath).content(it).sha256(
                                    DockerSchemaUtils.emptyBlobDigest().getDigestHex()
                                ).artifactFile(artifactFile)
                            )
                        }
                    } else if (repo.exists(path.projectId, path.repoName, tempBlobPath)) {
                        this.moveBlobFromTempDir(repo, path.projectId, path.repoName, tempBlobPath, finalBlobPath)
                    } else {
                        log.debug("blob temp file '{}' doesn't exist in temp, try other tags", tempBlobPath)
                        val targetPath = "/${path.dockerRepo}/$tag/$blobFilename"
                        if (!this.copyBlobFromFirstReadableDockerRepo(
                                repo,
                                path.projectId,
                                path.repoName,
                                path.dockerRepo,
                                blobFilename,
                                targetPath
                            )
                        ) {
                            log.error("could not find temp blob '{}'", tempBlobPath)
                            return false
                        }
                        log.debug("blob {} copy to {}", blobDigest.filename(), finalBlobPath)
                    }
                }
            }
        }

        // this.removeUnreferencedBlobs(repo, "$dockerRepo/$tag", info)
        log.debug("finish synv docker repository blobs")
        return true
    }

    private fun isForeignLayer(blobInfo: DockerBlobInfo): Boolean {
        return "application/vnd.docker.image.rootfs.foreign.diff.tar.gzip" == blobInfo.mediaType
    }

    protected fun copyBlobFromFirstReadableDockerRepo(
        repo: DockerArtifactoryService,
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
        repo: DockerArtifactoryService,
        projectId: String,
        repoName: String,
        sourcePath: String,
        targetPath: String,
        blobFilename: String
    ): Boolean {
        if (!StringUtils.equals(sourcePath, targetPath)) {
            log.info("found {} in path {}, copy over to {}", blobFilename, sourcePath, targetPath)
            return repo.copy(projectId, repoName, sourcePath, targetPath)
        }
        return false
    }

    private fun moveBlobFromTempDir(
        repo: DockerArtifactoryService,
        projectId: String,
        repoName: String,
        tempBlobPath: String,
        finalBlobPath: String
    ) {
        log.info("move temp blob from '{}' to '{}'", tempBlobPath, finalBlobPath)
        repo.move(projectId, repoName, tempBlobPath, finalBlobPath)
    }

    companion object {
        private val log = LoggerFactory.getLogger(DockerManifestSyncer::class.java)
    }
}
