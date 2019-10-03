package com.tencent.bkrepo.registry.artifact.repomd

import com.tencent.bkrepo.registry.DockerWorkContext
import com.tencent.bkrepo.registry.common.repomd.PackageWorkContext
import com.tencent.bkrepo.registry.papi.repo.RepoPath
import com.tencent.bkrepo.registry.repomd.Artifact
import com.tencent.bkrepo.registry.v2.helpers.DockerSearchBlobPolicy
import java.io.InputStream
import org.slf4j.LoggerFactory

class DockerPackageWorkContext(repoPath: RepoPath) : PackageWorkContext(repoPath), DockerWorkContext {

    companion object {
        private val log = LoggerFactory.getLogger(DockerPackageWorkContext::class.java)
        private val FIND_BLOBS_QUERY_LIMIT = 10
        private val READABLE_DOCKER_REPOSITORIES_LIMIT = 5
        val SHA2_PROPERTY = "sha256"
        val SHA2_FILENAME_PREFIX = "sha256__"
    }

    override fun translateRepoId(id: String): String {
        return id
    }

    override fun readGlobal(fullPath: String): InputStream {
        throw UnsupportedOperationException("NOT IMPLEMENTED")
    }

    override fun isBlobReadable(blob: Artifact): Boolean {
        throw UnsupportedOperationException("NOT IMPLEMENTED")
    }

    override fun findBlobsGlobally(digestFileName: String, searchPolicy: DockerSearchBlobPolicy): Iterable<Artifact> {
        throw UnsupportedOperationException("NOT IMPLEMENTED")
    }
}
