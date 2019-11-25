package com.tencent.bkrepo.docker.util

import com.google.common.base.Joiner
import com.tencent.bkrepo.docker.DockerWorkContext
import com.tencent.bkrepo.docker.artifact.repomd.DockerArtifactoryService
import com.tencent.bkrepo.docker.repomd.Artifact
import com.tencent.bkrepo.docker.v2.helpers.DockerSearchBlobPolicy
import com.tencent.bkrepo.docker.v2.model.DockerDigest
import org.slf4j.LoggerFactory

abstract class DockerUtils {
    companion object {
        private val log = LoggerFactory.getLogger(DockerUtils::class.java)
        val IMAGES_DIR = ".images"
        val LAYER_FILENAME = "layer.tar"
        val JSON_FILENAME = "json.json"
        val ANCESTRY_FILENAME = "ancestry.json"
        val CHECKSUM_FILENAME = "_checksum.json"
        val REPOSITORIES_DIR = "repositories"
        val INDEX_IMAGES_FILENAME = "_index_images.json"
        val TAG_FILENAME = "tag.json"
        val IMAGE_ID_PROP = "docker.imageId"
        val SIZE_PROP = "docker.size"
        val REPO_NAME_PROP = "docker.repoName"
        val MANIFEST_PROP = "docker.manifest"
        val MANIFEST_TYPE_PROP = "docker.manifest.type"
        val MANIFEST_DIGEST_PROP = "docker.manifest.digest"
        val DESCRIPTION_PROP = "docker.description"
        val TAG_NAME_PROP = "docker.tag.name"
        val TAG_CONTENT_PROP = "docker.tag.content"
        val LABEL_PROP_PREFIX = "docker.label."
        private val PATH_DELIMITER = "/"

        fun imagePath(imageId: String): String {
            return ".images/" + imageId.substring(0, 2) + "/" + imageId
        }

        fun imageBinaryPath(imageId: String): String {
            return imagePath(imageId) + "/" + "layer.tar"
        }

        fun imageJsonPath(imageId: String): String {
            return imagePath(imageId) + "/" + "json.json"
        }

        fun imageAncestryPath(imageId: String): String {
            return imagePath(imageId) + "/" + "ancestry.json"
        }

        fun imageChecksumPath(imageId: String): String {
            return imagePath(imageId) + "/" + "_checksum.json"
        }

        fun repositoryPath(namespace: String, repoName: String): String {
            return "repositories/" + repositoryName(namespace, repoName)
        }

        fun repositoryName(namespace: String, repoName: String): String {
            return "$namespace/$repoName"
        }

        fun repositoryPath(repository: String): String {
            return "repositories/$repository"
        }

        fun repoIndexImagesPath(namespace: String, repoName: String): String {
            return repositoryPath(namespace, repoName) + "/" + "_index_images.json"
        }

        fun repoIndexImagesPath(repository: String): String {
            return repositoryPath(repository) + "/" + "_index_images.json"
        }

        fun tagJsonPath(namespace: String, repoName: String, tagName: String): String {
            return repositoryPath(namespace, repoName) + "/" + tagName + "/" + "tag.json"
        }

        fun tagJsonPath(repository: String, tagName: String): String {
            return repositoryPath(repository) + "/" + tagName + "/" + "tag.json"
        }

        fun getBlobGlobally(repo: DockerArtifactoryService, blobFilename: String, searchPolicy: DockerSearchBlobPolicy): Artifact? {
//                var repoBlobs = (repo.getWorkContextC() as DockerWorkContext).findBlobsGlobally(blobFilename, searchPolicy)
//                if (repoBlobs != null && !Iterables.isEmpty(repoBlobs!!)) {
//                    // TODO : iter
//                val var10001 = DockerV2LocalRepoHandler.nonTempUploads
//                var10001.javaClass
//                repoBlobs = Iterables.filter(repoBlobs!!, Predicate<var10001.javaClass> { var10001.test(it) })
//                    if (!Iterables.isEmpty(repoBlobs!!)) {
//                        var foundBlob = repoBlobs!!.iterator().next() as Artifact
//                        val var5 = repoBlobs!!.iterator()
//
//                        while (var5.hasNext()) {
//                            val blob = var5.next() as Artifact
//                            if (repo.getRepoId().equals(blob.getRepoId())) {
//                                foundBlob = blob
//                                break
//                            }
//                        }
//
//                        return foundBlob
//                    }
//                }

            return null
        }

        fun findBlobGlobally(repo: DockerArtifactoryService, projectId: String, repoName: String, dockerRepo: String, fileName: String): Artifact? {
            val result = repo.findArtifacts(projectId, repoName, fileName)
            log.info("yyyyyyyyyyy {} ,{} ,{},{} ", projectId, repoName, dockerRepo, fileName)
            if (result.size == 0){
                return null
            }
            val blob = result.get(0)
            val length = blob.get("size") as Integer
            val fullPath = blob.get("fullPath") as String
            return Artifact(projectId,repoName,dockerRepo).sha256(fileName.replace("sha256__","")).contentLength(length.toLong()).path(fullPath)
        }

        fun getManifestConfigBlob(repo: DockerArtifactoryService,blobFilename: String,  projectId: String, repoName: String,dockerRepo: String, tag: String): Artifact? {
            val configPath = Joiner.on("/").join(dockerRepo, tag, blobFilename)
            //search blob in the repo first
            log.info("Searching manifest config blob in: '{}'", configPath)
            if (repo.exists(projectId, repoName,configPath)) {
                log.info("Manifest config blob found in: '{}'", configPath)
                val config = repo.artifact(projectId,repoName, configPath)
                if (repo.getWorkContextC().isBlobReadable(config!!)) {
                    return config
                }
            }
            // search file in the temp path
            return getBlobFromRepoPath(repo, projectId, repoName,dockerRepo,blobFilename)
        }

        // get blob from repo path
        fun getBlobFromRepoPath(repo: DockerArtifactoryService, projectId: String, repoName: String, dockerRepo: String, blobFilename: String): Artifact? {
            val tempBlobPath = "/$dockerRepo/_uploads/$blobFilename"
            log.info("Searching blob in '{}'", tempBlobPath)
            var blob: Artifact?
            if (repo.exists(projectId, repoName, tempBlobPath)) {
                log.info("Blob found in: '{}'", tempBlobPath)
                blob = repo.artifact(projectId,repoName, tempBlobPath)
                if (repo.getWorkContextC().isBlobReadable(blob!!)) {
                    return blob
                }
            }
            log.info("Attempting to search  blob {} globally {}", dockerRepo,blobFilename)
            blob = findBlobGlobally(repo, projectId, repoName, dockerRepo,blobFilename)
            return blob
        }

        fun getFullPath(artifact: Artifact, workContext: DockerWorkContext): String {
            return workContext.translateRepoId(artifact.getRepoId()) + "/" + artifact.getArtifactPath()
        }
    }
}
