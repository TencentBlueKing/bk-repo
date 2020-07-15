package com.tencent.bkrepo.docker.manifest

import com.fasterxml.jackson.databind.JsonNode
import com.tencent.bkrepo.docker.model.DockerImageMetadata
import com.tencent.bkrepo.docker.model.ManifestMetadata

/**
 * manifest util with each schema
 * @author: owenlxu
 * @date: 2020-02-05
 */
class ManifestUtil {

    companion object {

        fun populatePorts(manifestMetadata: ManifestMetadata, dockerMetadata: DockerImageMetadata) {
            if (dockerMetadata.config != null) {
                addPorts(manifestMetadata, dockerMetadata.config!!.exposedPorts)
            }

            if (dockerMetadata.containerConfig != null) {
                addPorts(manifestMetadata, dockerMetadata.containerConfig!!.exposedPorts)
            }
        }

        fun populateVolumes(manifestMetadata: ManifestMetadata, dockerMetadata: DockerImageMetadata) {
            if (dockerMetadata.config != null) {
                addVolumes(manifestMetadata, dockerMetadata.config!!.volumes)
            }

            if (dockerMetadata.containerConfig != null) {
                addVolumes(manifestMetadata, dockerMetadata.containerConfig!!.volumes)
            }
        }

        fun populateLabels(manifestMetadata: ManifestMetadata, dockerMetadata: DockerImageMetadata) {
            if (dockerMetadata.config != null) {
                addLabels(manifestMetadata, dockerMetadata.config!!.labels)
            }

            if (dockerMetadata.containerConfig != null) {
                addLabels(manifestMetadata, dockerMetadata.containerConfig!!.labels)
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

        private fun addVolumes(manifestMetadata: ManifestMetadata, volumes: JsonNode?) {
            if (volumes != null) {
                val iterVolume = volumes.fieldNames()

                while (iterVolume.hasNext()) {
                    manifestMetadata.tagInfo.volumes.add(iterVolume.next())
                }
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
