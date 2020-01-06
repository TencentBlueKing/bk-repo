package com.tencent.bkrepo.docker.errors

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

class DockerV2Errors {
    companion object {
        private val ERROR_MESSAGE = "{\"errors\":[{\"code\":\"%s\",\"message\":\"%s\",\"detail\":{%s}}]}"
        private val ERROR_MESSAGE_EMPTY = "{\"errors\":[{\"code\":\"%s\",\"message\":\"%s\",\"detail\":null}]}"
        private val AUTH_CHALLENGE = "Bearer realm=\"%s\",service=\"%s\""
        private val AUTH_CHALLENGE_SCOPE = ",scope=\"%s:%s:%s\""

        fun internalError(msg: String ?): ResponseEntity<Any> {
            if (null == msg) {
                return ResponseEntity.status(404).header("Docker-Distribution-Api-Version", "registry/2.0").contentType(MediaType.APPLICATION_JSON).body(String.format("{\"errors\":[{\"code\":\"%s\",\"message\":\"%s\",\"detail\":{%s}}]}", "INTERNAL_ERROR", "service internal error", "internal error"))
            }
            return ResponseEntity.status(404).header("Docker-Distribution-Api-Version", "registry/2.0").contentType(MediaType.APPLICATION_JSON).body(String.format("{\"errors\":[{\"code\":\"%s\",\"message\":\"%s\",\"detail\":{%s}}]}", "INTERNAL_ERROR", "service internal error", msg))
        }

        fun repoInvalid(repoName: String): ResponseEntity<Any> {
            return ResponseEntity.status(404).header("Docker-Distribution-Api-Version", "registry/2.0").contentType(MediaType.APPLICATION_JSON).body(String.format("{\"errors\":[{\"code\":\"%s\",\"message\":\"%s\",\"detail\":{%s}}]}", "REPO_ERROR", "repo not found error", repoName))
        }

        fun blobUnknown(digest: String): ResponseEntity<Any> {
            return ResponseEntity.status(404).header("Docker-Distribution-Api-Version", "registry/2.0").contentType(MediaType.APPLICATION_JSON).header("Content-Length", "157").body(String.format("{\"errors\":[{\"code\":\"%s\",\"message\":\"%s\",\"detail\":{%s}}]}", "BLOB_UNKNOWN", "blob unknown to registry", "\"blobSum\":\"$digest\""))
        }

        fun blobUploadInvalid(message: Any): ResponseEntity<Any> {
            return ResponseEntity.status(400).header("Docker-Distribution-Api-Version", "registry/2.0").contentType(MediaType.APPLICATION_JSON).body(String.format("{\"errors\":[{\"code\":\"%s\",\"message\":\"%s\",\"detail\":{%s}}]}", "BLOB_UPLOAD_INVALID", "There was an error processing the upload and it must be restarted.", "\"description\":\"$message\""))
        }

        fun manifestInvalid(message: Any): ResponseEntity<Any> {
            return ResponseEntity.status(400).header("Docker-Distribution-Api-Version", "registry/2.0").contentType(MediaType.APPLICATION_JSON).body(String.format("{\"errors\":[{\"code\":\"%s\",\"message\":\"%s\",\"detail\":{%s}}]}", "MANIFEST_INVALID", "manifest invalid", "\"description\":\"$message\""))
        }

        fun manifestUnknown(manifest: String): ResponseEntity<Any> {
            return ResponseEntity.status(404).header("Docker-Distribution-Api-Version", "registry/2.0").contentType(MediaType.APPLICATION_JSON).body(String.format("{\"errors\":[{\"code\":\"%s\",\"message\":\"%s\",\"detail\":{%s}}]}", "MANIFEST_UNKNOWN", "The named manifest is not known to the registry.", "\"manifest\":\"$manifest\""))
        }

        fun unauthorizedUpload(): ResponseEntity<Any> {
            return ResponseEntity.status(403).header("Docker-Distribution-Api-Version", "registry/2.0").contentType(MediaType.APPLICATION_JSON).body(String.format("{\"errors\":[{\"code\":\"%s\",\"message\":\"%s\",\"detail\":{%s}}]}", "UNAUTHORIZED", "The client does not have permission to push to the repository.", ""))
        }

        fun unauthorized(tokenUrl: String, registryService: String, scopeType: String? = null, repo: String = "", scope: String = ""): ResponseEntity<Any> {
            val scopeStr = if (scopeType != null) String.format(",scope=\"%s:%s:%s\"", scopeType, repo, scope) else ""
            return ResponseEntity.status(401).header("Docker-Distribution-Api-Version", "registry/2.0").header("WWW-Authenticate", String.format("Bearer realm=\"%s\",service=\"%s\"", tokenUrl, registryService) + scopeStr).contentType(MediaType.APPLICATION_JSON).body(String.format("{\"errors\":[{\"code\":\"%s\",\"message\":\"%s\",\"detail\":null}]}", "UNAUTHORIZED", "authentication required"))
        }

        fun unauthorizedManifest(manifest: String, err: String?): ResponseEntity<Any> {
            return ResponseEntity.status(403).header("Docker-Distribution-Api-Version", "registry/2.0").contentType(MediaType.APPLICATION_JSON).body(String.format("{\"errors\":[{\"code\":\"%s\",\"message\":\"%s\",\"detail\":{%s}}]}", "UNAUTHORIZED", "The client does not have permission for manifest" + if (err != null) ": $err" else "", "\"manifest\":\"$manifest\""))
        }

        fun nameUnknown(dockerRepo: String): ResponseEntity<Any> {
            return ResponseEntity.status(404).header("Docker-Distribution-Api-Version", "registry/2.0").contentType(MediaType.APPLICATION_JSON).body(String.format("{\"errors\":[{\"code\":\"%s\",\"message\":\"%s\",\"detail\":{%s}}]}", "NAME_UNKNOWN", "Repository name not known to registry.", "\"name\":\"$dockerRepo\""))
        }

        fun manifestConcurrent(message: Any): ResponseEntity<Any> {
            return ResponseEntity.status(400).header("Docker-Distribution-Api-Version", "registry/2.0").contentType(MediaType.APPLICATION_JSON).body(String.format("{\"errors\":[{\"code\":\"%s\",\"message\":\"%s\",\"detail\":{%s}}]}", "MANIFEST_INVALID", "MANIFEST-CONCURRENT-EXCEPTION", "\"description\":\"$message\""))
        }
    }
}
