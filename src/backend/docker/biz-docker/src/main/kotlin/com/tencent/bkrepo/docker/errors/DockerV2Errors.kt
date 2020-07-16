package com.tencent.bkrepo.docker.errors

import com.tencent.bkrepo.docker.constant.AUTH_CHALLENGE
import com.tencent.bkrepo.docker.constant.AUTH_CHALLENGE_SCOPE
import com.tencent.bkrepo.docker.constant.DOCKER_API_VERSION
import com.tencent.bkrepo.docker.constant.DOCKER_HEADER_API_VERSION
import com.tencent.bkrepo.docker.constant.ERROR_MESSAGE
import com.tencent.bkrepo.docker.constant.ERROR_MESSAGE_EMPTY
import com.tencent.bkrepo.docker.response.DockerResponse
import org.springframework.http.HttpHeaders.CONTENT_LENGTH
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

/**
 * define errors to return when exception occur
 * @author: owenlxu
 * @date: 2019-12-01
 */

class DockerV2Errors {
    companion object {

        fun internalError(msg: String?): DockerResponse {
            msg?.let {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).header(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(String.format(ERROR_MESSAGE, "INTERNAL_ERROR", "service internal error", "internal error"))
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND).header(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
                .contentType(MediaType.APPLICATION_JSON)
                .body(String.format(ERROR_MESSAGE, "INTERNAL_ERROR", "service internal error", msg))
        }

        fun repoInvalid(repoName: String): DockerResponse {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).header(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
                .contentType(MediaType.APPLICATION_JSON)
                .body(String.format(ERROR_MESSAGE, "REPO_ERROR", "repo not found error", repoName))
        }

        fun blobUnknown(digest: String): DockerResponse {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).header(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
                .contentType(MediaType.APPLICATION_JSON).header(CONTENT_LENGTH, "157")
                .body(String.format(ERROR_MESSAGE, "BLOB_UNKNOWN", "blob unknown to registry blobSum", digest))
        }

        fun blobUploadInvalid(message: Any): DockerResponse {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).header(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    String.format(
                        ERROR_MESSAGE,
                        "BLOB_UPLOAD_INVALID",
                        "There was an error processing the upload and it must be restarted.description",
                        message
                    )
                )
        }

        fun manifestInvalid(message: Any): DockerResponse {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).header(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
                .contentType(MediaType.APPLICATION_JSON)
                .body(String.format(ERROR_MESSAGE, "MANIFEST_INVALID", "manifest invalid description", message))
        }

        fun manifestUnknown(manifest: String): DockerResponse {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).header(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    String.format(
                        ERROR_MESSAGE,
                        "MANIFEST_UNKNOWN",
                        "The named manifest is not known to the registry.manifest",
                        manifest
                    )
                )
        }

        fun unauthorizedUpload(): DockerResponse {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).header(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
                .contentType(MediaType.APPLICATION_JSON)
                .body(String.format(ERROR_MESSAGE, "UNAUTHORIZED", "The client does not have permission to push to the repository.", ""))
        }

        fun unauthorized(tokenUrl: String, registryService: String, scopeType: String? = null, repo: String = "", scope: String = ""): DockerResponse {
            val scopeStr = if (scopeType != null) String.format(AUTH_CHALLENGE_SCOPE, scopeType, repo, scope) else ""
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).header(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
                .header("WWW-Authenticate", String.format(AUTH_CHALLENGE, tokenUrl, registryService) + scopeStr)
                .contentType(MediaType.APPLICATION_JSON)
                .body(String.format(ERROR_MESSAGE_EMPTY, "UNAUTHORIZED", "authentication required"))
        }

        fun unauthorizedManifest(manifest: String): DockerResponse {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).header(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    String.format(
                        ERROR_MESSAGE,
                        "UNAUTHORIZED",
                        "The client does not have permission for manifest:",
                        manifest
                    )
                )
        }

        fun nameUnknown(dockerRepo: String): DockerResponse {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).header(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    String.format(
                        ERROR_MESSAGE,
                        "NAME_UNKNOWN",
                        "Repository name not known to registry. name:",
                        dockerRepo
                    )
                )
        }

        fun manifestConcurrent(message: Any): DockerResponse {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).header(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    String.format(
                        ERROR_MESSAGE,
                        "MANIFEST_INVALID",
                        "MANIFEST-CONCURRENT-EXCEPTION description",
                        message
                    )
                )
        }
    }
}
