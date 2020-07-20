package com.tencent.bkrepo.docker.manifest

import com.tencent.bkrepo.common.api.constant.StringPool.COLON
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.docker.constant.DOCKER_NODE_NAME
import com.tencent.bkrepo.docker.exception.DockerManifestDeseriFailException
import com.tencent.bkrepo.docker.model.DockerBlobInfo
import com.tencent.bkrepo.docker.model.DockerDigest
import com.tencent.bkrepo.docker.model.DockerImageMetadata
import com.tencent.bkrepo.docker.model.ManifestMetadata
import org.apache.commons.lang.StringUtils
import org.slf4j.LoggerFactory
import java.io.IOException

/**
 * deserialize manifest schema1 manifest
 * @author: owenlxu
 * @date: 2020-02-05
 */
object ManifestSchema1Deserializer : AbstractManifestDeserializer() {

    private val logger = LoggerFactory.getLogger(ManifestSchema1Deserializer::class.java)
    private val objectMapper = JsonUtils.objectMapper

    fun deserialize(manifestBytes: ByteArray, digest: DockerDigest): ManifestMetadata {
        try {
            return applyAttributesFromContent(manifestBytes, digest)
        } catch (exception: IOException) {
            logger.error("Unable to deserialize the manifest.json file: [$exception]")
            throw DockerManifestDeseriFailException(exception.message!!)
        }
    }

    private fun applyAttributesFromContent(manifestBytes: ByteArray?, digest: DockerDigest): ManifestMetadata {
        val manifestMetadata = ManifestMetadata()
        manifestBytes ?: run {
            return manifestMetadata
        }
        val manifest = objectMapper.readTree(manifestBytes)
        manifestMetadata.tagInfo.title = manifest.get(DOCKER_NODE_NAME).asText() + COLON + manifest.get("tag").asText()
        manifestMetadata.tagInfo.digest = digest
        var totalSize = 0L
        val history = manifest.get("history")

        for (i in 0 until history.size()) {
            val fsLayer = history.get(i)
            val v1Compatibility = fsLayer.get("v1Compatibility").asText()
            val dockerMetadata = objectMapper.readValue(v1Compatibility.toByteArray(), DockerImageMetadata::class.java)
            val blobDigest = manifest.get("fsLayers").get(i).get("blobSum").asText()
            val size = dockerMetadata.size
            totalSize += dockerMetadata.size
            val blobInfo = DockerBlobInfo(dockerMetadata.id!!, blobDigest, size, dockerMetadata.created!!)
            populateWithCommand(dockerMetadata, blobInfo)
            manifestMetadata.blobsInfo.add(blobInfo)
            populatePorts(manifestMetadata, dockerMetadata)
            populateVolumes(manifestMetadata, dockerMetadata)
            populateLabels(manifestMetadata, dockerMetadata)
        }

        manifestMetadata.tagInfo.totalSize = totalSize
        return manifestMetadata
    }

    private fun populateWithCommand(dockerMetadata: DockerImageMetadata, blobInfo: DockerBlobInfo) {
        var command = getCommand(dockerMetadata)
        if (StringUtils.contains(command, "(nop)")) {
            command = StringUtils.substringAfter(command, "(nop) ")
            val dockerCmd = StringUtils.substringBefore(command, " ")
            command = StringUtils.substringAfter(command, " ")
            blobInfo.command = dockerCmd
            blobInfo.commandText = command
        } else if (StringUtils.isNotBlank(command)) {
            blobInfo.command = "RUN"
            blobInfo.commandText = command
        }
    }

    private fun getCommand(dockerMetadata: DockerImageMetadata): String? {
        var command: String? = null
        if (dockerMetadata.containerConfig != null && dockerMetadata.containerConfig!!.cmd != null) {
            command = if (dockerMetadata.containerConfig!!.cmd!!.size == 3) {
                dockerMetadata.containerConfig!!.cmd!![2]
            } else {
                dockerMetadata.containerConfig!!.cmd.toString()
            }
        }

        if (dockerMetadata.config != null && StringUtils.isBlank(command) && dockerMetadata.config!!.cmd != null) {
            command = if (dockerMetadata.config!!.cmd!!.size == 3) {
                dockerMetadata.config!!.cmd!![2]
            } else {
                dockerMetadata.config!!.cmd.toString()
            }
        }

        return command
    }
}
