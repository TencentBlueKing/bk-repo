package com.tencent.bkrepo.registry.artifact.repomd

import com.tencent.bkrepo.registry.DockerWorkContext
import com.tencent.bkrepo.registry.common.repomd.ArtifactoryService

class DockerArtifactoryService(context: DockerWorkContext, repoKey: String) : ArtifactoryService<DockerWorkContext>(context, repoKey) {

//    fun getAttributes(path: String, key: String?): Set<*>? {
//        if (key == null) {
//            val properties = this.propertiesService.getProperties(this.repoPath(path))
//            return if (!properties.isEmpty()) properties.entries() else null
//        } else {
//            return super.getAttributes(path, key)
//        }
//    }

    fun deleteAllAttributes(path: String): Boolean {
        throw UnsupportedOperationException("NOT IMPLEMENTED")
//        return this.propertiesService.removeProperties(this.repoPath(path))
    }
}
