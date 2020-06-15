package com.tencent.bkrepo.docker.manifest

import com.fasterxml.jackson.databind.JsonNode
import com.tencent.bkrepo.docker.model.DockerBlobInfo
import com.tencent.bkrepo.docker.model.DockerDigest
import com.tencent.bkrepo.docker.model.DockerImageMetadata
import com.tencent.bkrepo.docker.model.ManifestMetadata
import com.tencent.bkrepo.docker.util.JsonUtil
import org.apache.commons.lang.StringUtils
import org.slf4j.LoggerFactory
import java.io.IOException

class ManifestSchema1Deserializer {
    companion object {
        private val logger = LoggerFactory.getLogger(ManifestSchema1Deserializer::class.java)

        fun deserialize(manifestBytes: ByteArray, digest: DockerDigest): ManifestMetadata {
            try {
                return applyAttributesFromContent(manifestBytes, digest)
            } catch (exception: IOException) {
                logger.error("Unable to deserialize the manifest.json file: [$exception]")
                throw RuntimeException(exception)
            }
        }

        @Throws(IOException::class)
        private fun applyAttributesFromContent(manifestBytes: ByteArray?, digest: DockerDigest): ManifestMetadata {
            val manifestMetadata = ManifestMetadata()
            if (manifestBytes != null) {
                val manifest = JsonUtil.readTree(manifestBytes)
                manifestMetadata.tagInfo.title = manifest.get("name").asText() + ":" + manifest.get("tag").asText()
                manifestMetadata.tagInfo.digest = digest
                var totalSize = 0L
                val history = manifest.get("history")

                for (i in 0 until history.size()) {
                    val fsLayer = history.get(i)
                    val v1Compatibility = fsLayer.get("v1Compatibility").asText()
                    val dockerMetadata = JsonUtil.readValue(v1Compatibility.toByteArray(), DockerImageMetadata::class.java)
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
            }

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
                if (dockerMetadata.containerConfig!!.cmd!!.size == 3) {
                    command = dockerMetadata.containerConfig!!.cmd!!.get(2)
                } else {
                    command = dockerMetadata.containerConfig!!.cmd.toString()
                }
            }

            if (dockerMetadata.config != null && StringUtils.isBlank(command) && dockerMetadata.config!!.cmd != null) {
                if (dockerMetadata.config!!.cmd!!.size == 3) {
                    command = dockerMetadata.config!!.cmd!!.get(2)
                } else {
                    command = dockerMetadata.config!!.cmd.toString()
                }
            }

            return command
        }

        private fun populatePorts(manifestMetadata: ManifestMetadata, dockerMetadata: DockerImageMetadata) {
            if (dockerMetadata.config != null) {
                addPorts(manifestMetadata, dockerMetadata.config!!.exposedPorts)
            }

            if (dockerMetadata.containerConfig != null) {
                addPorts(manifestMetadata, dockerMetadata.containerConfig!!.exposedPorts)
            }
        }

        private fun addPorts(manifestMetadata: ManifestMetadata, exposedPorts: JsonNode?) {
            if (exposedPorts != null) {
                val iterPorts = exposedPorts.fieldNames()

                while (iterPorts.hasNext()) {
                    manifestMetadata.tagInfo.ports.add(iterPorts.next())
                }
            }
        }

        private fun populateVolumes(manifestMetadata: ManifestMetadata, dockerMetadata: DockerImageMetadata) {
            if (dockerMetadata.config != null) {
                addVolumes(manifestMetadata, dockerMetadata.config!!.volumes)
            }

            if (dockerMetadata.containerConfig != null) {
                addVolumes(manifestMetadata, dockerMetadata.containerConfig!!.volumes)
            }
        }

        private fun addVolumes(manifestMetadata: ManifestMetadata, volumes: JsonNode?) {
            if (volumes != null) {
                val iterVolume = volumes.fieldNames()

                while (iterVolume.hasNext()) {
                    manifestMetadata.tagInfo.volumes.add(iterVolume.next())
                }
            }
        }

        private fun populateLabels(manifestMetadata: ManifestMetadata, dockerMetadata: DockerImageMetadata) {
            if (dockerMetadata.config != null) {
                addLabels(manifestMetadata, dockerMetadata.config!!.labels)
            }

            if (dockerMetadata.containerConfig != null) {
                addLabels(manifestMetadata, dockerMetadata.containerConfig!!.labels)
            }
        }

        private fun addLabels(manifestMetadata: ManifestMetadata, labels: Map<String, String>?) {
            if (labels != null) {
                val iter = labels.entries.iterator()

                while (iter.hasNext()) {
                    val label = iter.next()
                    manifestMetadata.tagInfo.labels.put(label.key, label.value)
                }
            }
        }
    }
}
