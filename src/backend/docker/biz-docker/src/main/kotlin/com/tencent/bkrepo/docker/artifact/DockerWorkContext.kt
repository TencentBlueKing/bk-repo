package com.tencent.bkrepo.docker.artifact

// import com.tencent.bkrepo.docker.DockerWorkContext
import java.io.InputStream
import java.net.URI
import org.slf4j.LoggerFactory

class DockerWorkContext() {

    private var contextPath: String = ""
    private lateinit var contextMap: MutableMap<String, Any>

    companion object {
        private val log = LoggerFactory.getLogger(DockerWorkContext::class.java)
        private val FIND_BLOBS_QUERY_LIMIT = 10
        private val READABLE_DOCKER_REPOSITORIES_LIMIT = 5
        val SHA2_PROPERTY = "sha256"
        val SHA2_FILENAME_PREFIX = "sha256__"
    }

    fun translateRepoId(id: String): String {
        return id
    }

    fun readGlobal(fullPath: String): InputStream {
        throw UnsupportedOperationException("NOT IMPLEMENTED")
    }

    fun isBlobReadable(blob: Artifact): Boolean {
        return true
    }

    fun cleanup(repoId: String, uploadsPath: String) {
        return
        // (ContextHelper.get().beanForType(DockerService::class.java) as DockerService).cleanup(repoId, uploadsPath)
    }

    fun onTagPushedSuccessfully(s: String, s1: String, s2: String) {}

    fun obtainManifestLock(repoTag: String): String {
        return ""
    }

    fun releaseManifestLock(lockId: String, repoTag: String) {}

    fun copy(sourcePath: String, targetPath: String): Boolean {
        throw UnsupportedOperationException("NOT IMPLEMENTED")
    }

    fun getContextPath(): String {
        return this.contextPath
    }

    fun getSubject(): String {
        throw UnsupportedOperationException("NOT IMPLEMENTED")
    }

    fun getContextMap(): Map<String, Any> {
        return this.contextMap
    }

    fun setSystem() {
    }

    fun unsetSystem() {
    }

    fun rewriteRepoURI(repoKey: String, uri: URI, headers: MutableSet<MutableMap.MutableEntry<String, List<String>>>): URI {
        return uri
        // return DockerInternalRewrite.rewriteBack(repoKey, uri, headers)
    }
}
