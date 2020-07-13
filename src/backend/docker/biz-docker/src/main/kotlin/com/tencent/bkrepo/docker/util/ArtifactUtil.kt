package com.tencent.bkrepo.docker.util

import com.google.common.base.Joiner
import com.tencent.bkrepo.docker.artifact.DockerArtifact
import com.tencent.bkrepo.docker.artifact.DockerArtifactRepo
import com.tencent.bkrepo.docker.constant.DOCKER_NODE_FULL_PATH
import com.tencent.bkrepo.docker.constant.DOCKER_NODE_SIZE
import com.tencent.bkrepo.docker.constant.DOCKER_PRE_SUFFIX
import com.tencent.bkrepo.docker.constant.EMPTYSTR
import com.tencent.bkrepo.docker.context.RequestContext
import org.slf4j.LoggerFactory

/**
 * docker artifact utility
 * @author: owenlxu
 * @date: 2019-10-15
 */
class ArtifactUtil {

    companion object {
        private val logger = LoggerFactory.getLogger(ArtifactUtil::class.java)
        private const val IMAGES_DIR = ".images"
        private const val LAYER_FILENAME = "layer.tar"
        private const val JSON_FILENAME = "json.json"
        private const val REPOSITORIES_DIR = "repositories"
        private const val TAG_FILENAME = "tag.json"
        private const val PATH_DELIMITER = DOCKER_PRE_SUFFIX

        private fun imagePath(imageId: String): String {
            return IMAGES_DIR + imageId.substring(0, 2) + PATH_DELIMITER + imageId
        }

        fun imageBinaryPath(imageId: String): String {
            return imagePath(imageId) + PATH_DELIMITER + LAYER_FILENAME
        }

        fun imageJsonPath(imageId: String): String {
            return imagePath(imageId) + PATH_DELIMITER + JSON_FILENAME
        }

        private fun repositoryPath(namespace: String, repoName: String): String {
            return "$REPOSITORIES_DIR/" + repositoryName(namespace, repoName)
        }

        private fun repositoryName(namespace: String, repoName: String): String {
            return "$namespace/$repoName"
        }

        private fun repositoryPath(repository: String): String {
            return "$REPOSITORIES_DIR/$repository"
        }

        fun tagJsonPath(namespace: String, repoName: String, tagName: String): String {
            return repositoryPath(namespace, repoName) + PATH_DELIMITER + tagName + PATH_DELIMITER + TAG_FILENAME
        }

        fun tagJsonPath(repository: String, tagName: String): String {
            return repositoryPath(repository) + PATH_DELIMITER + tagName + PATH_DELIMITER + TAG_FILENAME
        }

        // get blob by file name cross repo
        fun getBlobByName(repo: DockerArtifactRepo, pathContext: RequestContext, fileName: String): DockerArtifact? {
            val result = repo.getArtifactListByName(pathContext.projectId, pathContext.repoName, fileName)
            if (result.isEmpty()) {
                return null
            }
            val blob = result[0]
            val length = blob[DOCKER_NODE_SIZE] as Int
            val fullPath = blob[DOCKER_NODE_FULL_PATH] as String
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

        // get manifest config blob data
        fun getManifestConfigBlob(repo: DockerArtifactRepo, filename: String, context: RequestContext, tag: String): DockerArtifact? {
            val configPath = Joiner.on(DOCKER_PRE_SUFFIX).join(context.artifactName, tag, filename)
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
            return PATH_DELIMITER + dockerArtifact.fullPath
        }

        private fun sha256FromFileName(fileName: String): String {
            return fileName.replace("sha256__", EMPTYSTR)
        }
    }
}
