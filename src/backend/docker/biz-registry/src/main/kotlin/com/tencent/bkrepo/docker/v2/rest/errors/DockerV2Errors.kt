package com.tencent.bkrepo.docker.v2.rest.errors

import org.springframework.http.ResponseEntity
// import javax.ws.rs.core.MediaType
import org.springframework.http.MediaType

class DockerV2Errors {
    companion object {
        private val ERROR_MESSAGE = "{\"errors\":[{\"code\":\"%s\",\"message\":\"%s\",\"detail\":{%s}}]}"
        private val ERROR_MESSAGE_EMPTY = "{\"errors\":[{\"code\":\"%s\",\"message\":\"%s\",\"detail\":null}]}"
        private val AUTH_CHALLENGE = "Bearer realm=\"%s\",service=\"%s\""
        private val AUTH_CHALLENGE_SCOPE = ",scope=\"%s:%s:%s\""

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

//        @JvmOverloads
//        fun unauthorized(tokenUrl: String, registryService: String, scopeType: String? = null, repo: String = "", scope: String = ""): Response {
//            val scopeStr = if (scopeType != null) String.format(",scope=\"%s:%s:%s\"", scopeType, repo, scope) else ""
//            return Response.status(401).header("Docker-Distribution-Api-Version", "registry/2.0").header("WWW-Authenticate", String.format("Bearer realm=\"%s\",service=\"%s\"", tokenUrl, registryService) + scopeStr).type(MediaType.APPLICATION_JSON_TYPE).entity(String.format("{\"errors\":[{\"code\":\"%s\",\"message\":\"%s\",\"detail\":null}]}", "UNAUTHORIZED", "authentication required")).build()
//        }
//
//        fun unauthorizedManifest(manifest: String, err: String?): Response {
//            return Response.status(403).header("Docker-Distribution-Api-Version", "registry/2.0").type(MediaType.APPLICATION_JSON_TYPE).entity(String.format("{\"errors\":[{\"code\":\"%s\",\"message\":\"%s\",\"detail\":{%s}}]}", "UNAUTHORIZED", "The client does not have permission for manifest" + if (err != null) ": $err" else "", "\"manifest\":\"$manifest\"")).build()
//        }
//
//        fun nameUnknown(dockerRepo: String): Response {
//            return Response.status(404).header("Docker-Distribution-Api-Version", "registry/2.0").type(MediaType.APPLICATION_JSON_TYPE).entity(String.format("{\"errors\":[{\"code\":\"%s\",\"message\":\"%s\",\"detail\":{%s}}]}", "NAME_UNKNOWN", "Repository name not known to registry.", "\"name\":\"$dockerRepo\"")).build()
//        }
//
        fun manifestConcurrent(message: Any): ResponseEntity<Any> {
            return ResponseEntity.status(400).header("Docker-Distribution-Api-Version", "registry/2.0").contentType(MediaType.APPLICATION_JSON).body(String.format("{\"errors\":[{\"code\":\"%s\",\"message\":\"%s\",\"detail\":{%s}}]}", "MANIFEST_INVALID", "MANIFEST-CONCURRENT-EXCEPTION", "\"description\":\"$message\""))
        }
    }
}
