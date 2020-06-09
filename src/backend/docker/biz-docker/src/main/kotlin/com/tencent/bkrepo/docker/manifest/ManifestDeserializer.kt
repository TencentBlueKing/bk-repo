package com.tencent.bkrepo.docker.manifest

import com.tencent.bkrepo.docker.artifact.DockerArtifactoryService
import com.tencent.bkrepo.docker.context.RequestContext
import com.tencent.bkrepo.docker.model.DockerDigest
import com.tencent.bkrepo.docker.model.ManifestMetadata
import com.tencent.bkrepo.docker.util.DockerSchemaUtils

class ManifestDeserializer {
    companion object {

        fun deserialize(repo: DockerArtifactoryService, pathContext: RequestContext, tag: String, manifestType: ManifestType, bytes: ByteArray, digest: DockerDigest): ManifestMetadata {
            var manifestBytes = bytes
            when (manifestType) {
                ManifestType.Schema1 -> return ManifestSchema1Deserializer.deserialize(manifestBytes, digest)
                ManifestType.Schema1Signed -> return ManifestSchema1Deserializer.deserialize(manifestBytes, digest)
                ManifestType.Schema2 -> {
                    val manifestJsonBytes = DockerSchemaUtils.fetchSchema2ManifestConfig(repo, pathContext.projectId, pathContext.repoName, manifestBytes, pathContext.dockerRepo, tag)
                    return ManifestSchema2Deserializer.deserialize(manifestBytes, manifestJsonBytes, pathContext.dockerRepo, tag, digest)
                }
                ManifestType.Schema2List -> {
                    val schema2Path = DockerSchemaUtils.fetchSchema2Path(repo, pathContext.projectId, pathContext.repoName, pathContext.dockerRepo, manifestBytes, true)
                    manifestBytes = DockerSchemaUtils.fetchSchema2Manifest(repo, schema2Path)
                    return ManifestSchema1Deserializer.deserialize(manifestBytes, digest)
                }
                else -> return ManifestSchema1Deserializer.deserialize(manifestBytes, digest)
            }
        }
    }
}
