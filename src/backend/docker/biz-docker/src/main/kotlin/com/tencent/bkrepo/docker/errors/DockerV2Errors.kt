package com.tencent.bkrepo.docker.errors

import com.tencent.bkrepo.docker.response.DockerResponse
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

class DockerV2Errors {
    companion object {
        private val ERROR_MESSAGE = "{\"errors\":[{\"code\":\"%s\",\"message\":\"%s\",\"detail\":{%s}}]}"
        private val ERROR_MESSAGE_EMPTY = "{\"errors\":[{\"code\":\"%s\",\"message\":\"%s\",\"detail\":null}]}"
        private val AUTH_CHALLENGE = "Bearer realm=\"%s\",service=\"%s\""
        private val AUTH_CHALLENGE_SCOPE = ",scope=\"%s:%s:%s\""

        fun internalError(msg: String?): ResponseEntity<Any> {
            msg?.let {
                return ResponseEntity.status(404).header("Docker-Distribution-Api-Version", "registry/2.0")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(String.format(ERROR_MESSAGE, "INTERNAL_ERROR", "service internal error", "internal error"))
            }
            return ResponseEntity.status(404).header("Docker-Distribution-Api-Version", "registry/2.0")
                .contentType(MediaType.APPLICATION_JSON)
                .body(String.format(ERROR_MESSAGE, "INTERNAL_ERROR", "service internal error", msg))
        }

        fun repoInvalid(repoName: String): ResponseEntity<Any> {
            return ResponseEntity.status(404).header("Docker-Distribution-Api-Version", "registry/2.0")
                .contentType(MediaType.APPLICATION_JSON)
                .body(String.format(ERROR_MESSAGE, "REPO_ERROR", "repo not found error", repoName))
        }

        fun blobUnknown(digest: String): ResponseEntity<Any> {
            return ResponseEntity.status(404).header("Docker-Distribution-Api-Version", "registry/2.0")
                .contentType(MediaType.APPLICATION_JSON).header("Content-Length", "157")
                .body(String.format(ERROR_MESSAGE, "BLOB_UNKNOWN", "blob unknown to registry", "\"blobSum\":\"$digest\""))
        }

        fun blobUploadInvalid(message: Any): ResponseEntity<Any> {
            return ResponseEntity.status(400).header("Docker-Distribution-Api-Version", "registry/2.0")
                .contentType(MediaType.APPLICATION_JSON)
                .body(String.format(ERROR_MESSAGE, "BLOB_UPLOAD_INVALID", "There was an error processing the upload and it must be restarted.", "\"description\":\"$message\""))
        }

        fun manifestInvalid(message: Any): ResponseEntity<Any> {
            return ResponseEntity.status(400).header("Docker-Distribution-Api-Version", "registry/2.0")
                .contentType(MediaType.APPLICATION_JSON)
                .body(String.format(ERROR_MESSAGE, "MANIFEST_INVALID", "manifest invalid", "\"description\":\"$message\""))
        }

        fun manifestUnknown(manifest: String): ResponseEntity<Any> {
            return ResponseEntity.status(404).header("Docker-Distribution-Api-Version", "registry/2.0")
                .contentType(MediaType.APPLICATION_JSON)
                .body(String.format(ERROR_MESSAGE, "MANIFEST_UNKNOWN", "The named manifest is not known to the registry.", "\"manifest\":\"$manifest\""))
        }

        fun unauthorizedUpload(): ResponseEntity<Any> {
            return ResponseEntity.status(403).header("Docker-Distribution-Api-Version", "registry/2.0")
                .contentType(MediaType.APPLICATION_JSON)
                .body(String.format(ERROR_MESSAGE, "UNAUTHORIZED", "The client does not have permission to push to the repository.", ""))
        }

        fun unauthorized(tokenUrl: String, registryService: String, scopeType: String? = null, repo: String = "", scope: String = ""): DockerResponse {
            val scopeStr = if (scopeType != null) String.format(AUTH_CHALLENGE_SCOPE, scopeType, repo, scope) else ""
            return ResponseEntity.status(401).header("Docker-Distribution-Api-Version", "registry/2.0")
                .header("WWW-Authenticate", String.format(AUTH_CHALLENGE, tokenUrl, registryService) + scopeStr)
                .contentType(MediaType.APPLICATION_JSON)
                .body(String.format(ERROR_MESSAGE_EMPTY, "UNAUTHORIZED", "authentication required"))
        }

        fun unauthorizedManifest(manifest: String, err: String?): ResponseEntity<Any> {
            return ResponseEntity.status(403).header("Docker-Distribution-Api-Version", "registry/2.0")
                .contentType(MediaType.APPLICATION_JSON)
                .body(String.format(ERROR_MESSAGE, "UNAUTHORIZED", "The client does not have permission for manifest" + if (err != null) ": $err" else "", "\"manifest\":\"$manifest\""))
        }

        fun nameUnknown(dockerRepo: String): ResponseEntity<Any> {
            return ResponseEntity.status(404).header("Docker-Distribution-Api-Version", "registry/2.0")
                .contentType(MediaType.APPLICATION_JSON)
                .body(String.format(ERROR_MESSAGE, "NAME_UNKNOWN", "Repository name not known to registry.", "\"name\":\"$dockerRepo\""))
        }

        fun manifestConcurrent(message: Any): ResponseEntity<Any> {
            return ResponseEntity.status(400).header("Docker-Distribution-Api-Version", "registry/2.0")
                .contentType(MediaType.APPLICATION_JSON)
                .body(String.format(ERROR_MESSAGE, "MANIFEST_INVALID", "MANIFEST-CONCURRENT-EXCEPTION", "\"description\":\"$message\""))
        }
    }
}
