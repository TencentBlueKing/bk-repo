package com.tencent.bkrepo.docker.artifact

import org.slf4j.LoggerFactory
import java.io.InputStream

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

    // TODO: need to develop
    fun readGlobal(fullPath: String): InputStream {
        throw UnsupportedOperationException("NOT IMPLEMENTED")
    }
}
