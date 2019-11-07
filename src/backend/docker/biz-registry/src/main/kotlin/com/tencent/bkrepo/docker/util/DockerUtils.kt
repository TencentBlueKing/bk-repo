package com.tencent.bkrepo.docker.util

import com.google.common.base.Joiner
import com.tencent.bkrepo.docker.DockerWorkContext
import com.tencent.bkrepo.docker.artifact.repomd.DockerArtifactoryService
import com.tencent.bkrepo.docker.repomd.Artifact
import com.tencent.bkrepo.docker.v2.helpers.DockerSearchBlobPolicy
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

        fun findBlobGlobally(repo: DockerArtifactoryService, projectId: String, repoName: String, path: String, fileDigest: String): Artifact? {
            //val fullPath = "/$projectId/$repoName/$path"
            var nodeDetail = repo.findArtifacts(projectId, repoName, path)
            if (nodeDetail == null) {
                return null
            } else {
                if (nodeDetail.nodeInfo.sha256 == fileDigest) {
                    return Artifact(projectId, repoName, path).sha256(nodeDetail.nodeInfo.sha256!!).contentLength(nodeDetail.nodeInfo.size)
                }
                return null
            }

            return null
        }

        fun getManifestConfigBlob(repo: DockerArtifactoryService,blobFilename: String,  projectId: String, repoName: String,dockerRepoPath: String, tag: String): Artifact? {
            val configPath = Joiner.on("/").join(dockerRepoPath, tag, *arrayOf<Any>(blobFilename))
            //search blob in the repo first
            log.debug("Searching manifest config blob in: '{}'", configPath)
            if (repo.exists(configPath)) {
                log.debug("Manifest config blob found in: '{}'", configPath)
                val config = repo.artifact(projectId,repoName, configPath)
                if ((repo.getWorkContextC() as DockerWorkContext).isBlobReadable(config!!)) {
                    return config
                }
            }
            // search file in the temp path
            return getBlobFromRepoPath(repo, blobFilename, dockerRepoPath, "", "")
        }

        // get blob from local path
        fun getBlobFromRepoPath(repo: DockerArtifactoryService, projectId: String, repoName: String, dockerRepoPath: String, blobFilename: String): Artifact? {
            val tempBlobPath = dockerRepoPath + "/" + "_uploads" + "/" + blobFilename
            log.info("Searching blob in '{}'", tempBlobPath)
            var blob: Artifact?
            if (repo.existsLocal(projectId, repoName, tempBlobPath)) {
                log.debug("Blob found in: '{}'", tempBlobPath)
                blob = repo.artifactLocal(projectId,repoName, tempBlobPath)
                return blob
                // TODO("remove auth logics")
//                if ((repo.getWorkContextC() as DockerWorkContext).isBlobReadable(blob!!)) {
//                    return blob
//                }
            }
            return null
//            log.debug("Attempting to search blob {} globally", path)
//            blob = findBlobGlobally(repo, projectId, repoName, path, fileDigest)
//            return blob
        }

        fun getFullPath(artifact: Artifact, workContext: DockerWorkContext): String {
            return workContext.translateRepoId(artifact.getRepoId()) + "/" + artifact.getArtifactPath()
        }
    }
}
