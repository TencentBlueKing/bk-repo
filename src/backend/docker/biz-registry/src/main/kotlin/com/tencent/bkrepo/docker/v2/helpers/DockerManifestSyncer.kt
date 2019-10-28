package com.tencent.bkrepo.docker.v2.helpers

import com.google.common.collect.Sets
import com.tencent.bkrepo.docker.DockerWorkContext
import com.tencent.bkrepo.docker.artifact.repomd.DockerArtifactoryService
import com.tencent.bkrepo.docker.artifact.repomd.DockerPackageWorkContext
import com.tencent.bkrepo.docker.repomd.Artifact
import com.tencent.bkrepo.docker.repomd.Repo
import com.tencent.bkrepo.docker.repomd.util.PathUtils
import com.tencent.bkrepo.docker.util.DockerSchemaUtils
import com.tencent.bkrepo.docker.util.DockerUtils
import com.tencent.bkrepo.docker.v2.model.DockerBlobInfo
import com.tencent.bkrepo.docker.v2.model.DockerDigest
import com.tencent.bkrepo.docker.v2.model.ManifestMetadata
import java.io.ByteArrayInputStream
import java.io.IOException
import org.apache.commons.lang.StringUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class DockerManifestSyncer() {

    @Throws(IOException::class)
    fun sync(repo: DockerArtifactoryService, info: ManifestMetadata, dockerRepo: String, tag: String): Boolean {
        log.info("Starting to sync docker repository blobs")
        val var5 = info.blobsInfo.iterator()

        while (var5.hasNext()) {
            val blobInfo = var5.next() as DockerBlobInfo
            log.info(" docker digest {}", blobInfo.digest)
            if (blobInfo.digest != null && !this.isForeignLayer(blobInfo)) {
                val blobDigest = DockerDigest(blobInfo.digest!!)
                val blobFilename = blobDigest.filename()
                log.info(" blob file name digest {}", blobFilename)
                val tempBlobPath = "$dockerRepo/_uploads/$blobFilename"
                val finalBlobPath = "$dockerRepo/$tag/$blobFilename"
                if (!repo.exists(finalBlobPath)) {
                    if (DockerSchemaUtils.isEmptyBlob(blobDigest)) {
                        log.debug("Found empty layer {} in manifest for image {} - creating blob in path {}", *arrayOf<Any>(blobFilename, dockerRepo, finalBlobPath))
                        val blobContent = ByteArrayInputStream(DockerSchemaUtils.EMPTY_BLOB_CONTENT)
                        var var12: Throwable? = null

                        try {
                            repo.write(finalBlobPath, blobContent)
                        } catch (var21: Throwable) {
                            var12 = var21
                            throw var21
                        } finally {
                            if (blobContent != null) {
                                if (var12 != null) {
                                    try {
                                        blobContent.close()
                                    } catch (var20: Throwable) {
                                        var12.addSuppressed(var20)
                                    }
                                } else {
                                    blobContent.close()
                                }
                            }
                        }
                    } else if (repo.exists(tempBlobPath)) {
                        this.moveBlobFromTempDir(repo, tempBlobPath, finalBlobPath)
                    } else {
                        log.debug("Blob temp file '{}' doesn't exist in temp, trying other tags", tempBlobPath)
                        val targetPath = repo.getRepoId() + "/" + finalBlobPath
                        if (!this.copyBlobFromFirstReadableDockerRepo(repo, blobFilename, targetPath)) {
                            log.error("Could not find temp blob '{}'", tempBlobPath)
                            return false
                        }

                        log.debug("blob {} copied to {}", blobDigest.filename(), targetPath)
                    }
                }
            }
        }

        this.removeUnreferencedBlobs(repo, "$dockerRepo/$tag", info)
        log.debug("Finished syncing docker repository blobs")
        return true
    }

    private fun isForeignLayer(blobInfo: DockerBlobInfo): Boolean {
        return "application/vnd.docker.image.rootfs.foreign.diff.tar.gzip" == blobInfo.mediaType
    }

    private fun removeUnreferencedBlobs(repo: DockerArtifactoryService, repoTag: String, info: ManifestMetadata) {
        log.debug("Starting to remove unreferenced blobs from '{}'", repoTag)
        val manifestBlobs = Sets.newHashSet<String>()
        val var5 = info.blobsInfo.iterator()

        while (var5.hasNext()) {
            val blobInfo = var5.next() as DockerBlobInfo
            if (blobInfo.digest != null) {
                val blobDigest = DockerDigest(blobInfo.digest!!)
                manifestBlobs.add(blobDigest.filename())
            }
        }

        val artifacts = repo.findArtifacts(repoTag, "*")
        if (artifacts != null) {
            val var11 = artifacts!!.iterator()

            while (var11.hasNext()) {
                val artifact = var11.next() as Artifact
                val path = artifact.getArtifactPath()
                val filename = PathUtils.getFileName(path)
                if (!StringUtils.equals(filename, "manifest.json") && !manifestBlobs.contains(filename)) {
                    log.info("Removing the unreferenced blob '{}'", path)
                    repo.delete(path)
                }
            }
        }

        log.debug("Completed unreferenced blobs cleanup from '{}'", repoTag)
    }

    protected fun copyBlobFromFirstReadableDockerRepo(repo: DockerArtifactoryService, blobFilename: String, targetPath: String): Boolean {
        val blob = DockerUtils.getBlobGlobally(repo, blobFilename, DockerSearchBlobPolicy.SHA_256)
        return this.copyBlob(repo, blobFilename, targetPath, blob)
    }

    protected fun copyBlob(repo: DockerArtifactoryService, blobFilename: String, targetPath: String, blob: Artifact?): Boolean {
        if (blob != null) {
            val sourcePath = DockerUtils.getFullPath(blob, repo.getWorkContextC() as DockerWorkContext)
            if (!StringUtils.equals(sourcePath, targetPath)) {
                log.debug("Found {} in path {}, copying over to {}", *arrayOf<Any>(blobFilename, sourcePath, targetPath))
                return (repo.getWorkContextC() as DockerWorkContext).copy(sourcePath, targetPath)
            }
        }

        return false
    }

    private fun moveBlobFromTempDir(repo: DockerArtifactoryService, tempBlobPath: String, finalBlobPath: String) {
        log.debug("Moving temp blob from '{}' to '{}'", tempBlobPath, finalBlobPath)
        repo.copy(tempBlobPath, finalBlobPath)
        (repo.getWorkContextC() as DockerWorkContext).setSystem()

        try {
            repo.delete(tempBlobPath)
        } finally {
            (repo.getWorkContextC() as DockerWorkContext).unsetSystem()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DockerManifestSyncer::class.java)
    }
}
