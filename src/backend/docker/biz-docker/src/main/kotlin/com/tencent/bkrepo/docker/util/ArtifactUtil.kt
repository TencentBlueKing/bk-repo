package com.tencent.bkrepo.docker.util

import com.google.common.base.Joiner
import com.tencent.bkrepo.docker.artifact.DockerArtifact
import com.tencent.bkrepo.docker.artifact.DockerArtifactRepo
import com.tencent.bkrepo.docker.context.RequestContext
import org.slf4j.LoggerFactory

class ArtifactUtil {

    companion object {
        private val logger = LoggerFactory.getLogger(ArtifactUtil::class.java)
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

        private fun repositoryPath(namespace: String, repoName: String): String {
            return "repositories/" + repositoryName(namespace, repoName)
        }

        private fun repositoryName(namespace: String, repoName: String): String {
            return "$namespace/$repoName"
        }

        private fun repositoryPath(repository: String): String {
            return "repositories/$repository"
        }

        fun tagJsonPath(namespace: String, repoName: String, tagName: String): String {
            return repositoryPath(namespace, repoName) + "/" + tagName + "/" + "tag.json"
        }

        fun tagJsonPath(repository: String, tagName: String): String {
            return repositoryPath(repository) + "/" + tagName + "/" + "tag.json"
        }

        // get blob by file name cross repo
        fun getBlobByName(repo: DockerArtifactRepo, pathContext: RequestContext, fileName: String): DockerArtifact? {
            val result = repo.getArtifactListByName(pathContext.projectId, pathContext.repoName, fileName)
            if (result.isEmpty()) {
                return null
            }
            val blob = result[0]
            val length = blob["size"] as Int
            val fullPath = blob["fullPath"] as String
            with(pathContext) {
                return DockerArtifact(projectId, repoName, artifactName).sha256(sha256FromFileName(fileName)).length(length.toLong()).fullPath(fullPath)
            }
        }

        fun getManifestByName(repo: DockerArtifactRepo, context: RequestContext, fileName: String): DockerArtifact? {
            val fullPath = "/${context.artifactName}/$fileName"
            return repo.getArtifact(context.projectId, context.repoName, fullPath) ?: run {
                return null
            }
        }

        fun getManifestConfigBlob(repo: DockerArtifactRepo, filename: String, context: RequestContext, tag: String): DockerArtifact? {
            val configPath = Joiner.on("/").join(context.artifactName, tag, filename)
            // search blob by full tag path
            logger.info("search manifest config blob in: [$configPath]")
            if (repo.exists(context.projectId, context.repoName, configPath)) {
                return repo.getArtifact(context.projectId, context.repoName, configPath)
            }
            // search file in the temp path
            return getBlobFromRepo(repo, context, filename)
        }

        // get blob from repo path
        fun getBlobFromRepo(repo: DockerArtifactRepo, context: RequestContext, fileName: String): DockerArtifact? {
            val tempBlobPath = "/${context.artifactName}/_uploads/$fileName"
            logger.info("search blob in temp path [$tempBlobPath] first")
            if (repo.exists(context.projectId, context.repoName, tempBlobPath)) {
                return repo.getArtifact(context.projectId, context.repoName, tempBlobPath)
            }
            logger.info("attempt to search  blob [$context,$fileName]")
            return getBlobByName(repo, context, fileName)
        }

        fun getFullPath(dockerArtifact: DockerArtifact): String {
            return "/" + dockerArtifact.fullPath
        }

        private fun sha256FromFileName(fileName: String): String {
            return fileName.replace("sha256__", "")
        }
    }
}
