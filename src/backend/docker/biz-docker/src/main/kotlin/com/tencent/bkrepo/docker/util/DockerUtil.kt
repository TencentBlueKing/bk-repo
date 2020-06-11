package com.tencent.bkrepo.docker.util

import com.google.common.base.Joiner
import com.tencent.bkrepo.docker.artifact.Artifact
import com.tencent.bkrepo.docker.artifact.DockerArtifactService
import com.tencent.bkrepo.docker.context.RequestContext
import com.tencent.bkrepo.docker.helpers.DockerSearchBlobPolicy
import org.slf4j.LoggerFactory

class DockerUtil(repo: DockerArtifactService) {

    companion object {
        private val logger = LoggerFactory.getLogger(DockerUtil::class.java)
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

        private fun imagePath(imageId: String): String {
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

        private fun repositoryPath(namespace: String, repoName: String): String {
            return "repositories/" + repositoryName(namespace, repoName)
        }

        private fun repositoryName(namespace: String, repoName: String): String {
            return "$namespace/$repoName"
        }

        private fun repositoryPath(repository: String): String {
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

        fun getBlobGlobally(
            repo: DockerArtifactService,
            blobFilename: String,
            searchPolicy: DockerSearchBlobPolicy
        ): Artifact? {
            logger.info("get globally : {}, {} ,{} ", repo.toString(), blobFilename, searchPolicy.toString())
            return null
        }

        // find blob file cross repo
        fun findBlobGlobally(
            repo: DockerArtifactService,
            pathContext: RequestContext,
            fileName: String
        ): Artifact? {
            val result = repo.getArtifactListByName(pathContext.projectId, pathContext.repoName, fileName)
            if (result.isEmpty()) {
                return null
            }
            val blob = result[0]
            val length = blob["size"] as Int
            val fullPath = blob["fullPath"] as String
            return Artifact(
                pathContext.projectId,
                pathContext.repoName,
                pathContext.dockerRepo
            ).sha256(fileName.replace("sha256__", ""))
                .length(length.toLong()).path(fullPath)
        }

        fun getManifestConfigBlob(
            repo: DockerArtifactService,
            blobFilename: String,
            pathContext: RequestContext,
            tag: String
        ): Artifact? {
            val configPath = Joiner.on("/").join(pathContext.dockerRepo, tag, blobFilename)
            // search blob in the repo first
            logger.info("search manifest config blob in: [$configPath]")
            if (repo.exists(pathContext.projectId, pathContext.repoName, configPath)) {
                return repo.getArtifact(pathContext.projectId, pathContext.repoName, configPath)
            }
            // search file in the temp path
            return getBlobFromRepo(repo, pathContext, blobFilename)
        }

        // get blob from repo path
        fun getBlobFromRepo(
            repo: DockerArtifactService,
            pathContext: RequestContext,
            blobFilename: String
        ): Artifact? {
            val tempBlobPath = "/${pathContext.dockerRepo}/_uploads/$blobFilename"
            logger.info("search blob in temp path [$tempBlobPath] first")
            var blob: Artifact?
            if (repo.exists(pathContext.projectId, pathContext.repoName, tempBlobPath)) {
                blob = repo.getArtifact(pathContext.projectId, pathContext.repoName, tempBlobPath)
                return blob
            }
            logger.info("attempt to search  blob [$pathContext,$blobFilename]")
            blob = findBlobGlobally(repo, pathContext, blobFilename)
            return blob
        }

        fun getFullPath(artifact: Artifact): String {
            return "/" + artifact.path
        }
    }
}
