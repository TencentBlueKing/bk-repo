package com.tencent.bkrepo.docker.manifest

import com.tencent.bkrepo.docker.artifact.DockerArtifactRepo
import com.tencent.bkrepo.docker.context.RequestContext
import com.tencent.bkrepo.docker.model.DockerDigest
import com.tencent.bkrepo.docker.model.ManifestMetadata

/**
 * the entrance for deserialize manifest
 * @author: owenlxu
 * @date: 2020-02-05
 */
object ManifestDeserializer {

    /**
     * deserialize the manifest file of the docker registry
     * @param repo docker repo to work with the storage
     * @param context the request context params
     * @param tag the docker image tag
     * @param manifestType the type of the manifest
     * @param bytes ByteArray of the manifest file
     * @param digest the digest object
     * @return ManifestMetadata
     */
    fun deserialize(repo: DockerArtifactRepo, context: RequestContext, tag: String, manifestType: ManifestType, bytes: ByteArray, digest: DockerDigest): ManifestMetadata {
        var manifestBytes = bytes
        val manifestProcess = ManifestProcess(repo)
        return when (manifestType) {
            ManifestType.Schema1 -> ManifestSchema1Deserializer.deserialize(manifestBytes, digest)
            ManifestType.Schema1Signed -> ManifestSchema1Deserializer.deserialize(manifestBytes, digest)
            ManifestType.Schema2 -> {
                val manifestJsonBytes = manifestProcess.getSchema2ManifestConfigContent(context, manifestBytes, tag)
                ManifestSchema2Deserializer.deserialize(
                    manifestBytes,
                    manifestJsonBytes,
                    context.artifactName,
                    tag,
                    digest
                )
            }
            ManifestType.Schema2List -> {
                val schema2Path = manifestProcess.getSchema2Path(context, manifestBytes)
                manifestBytes = manifestProcess.getSchema2ManifestContent(context, schema2Path)
                ManifestSchema1Deserializer.deserialize(manifestBytes, digest)
            }
        }
    }
}
