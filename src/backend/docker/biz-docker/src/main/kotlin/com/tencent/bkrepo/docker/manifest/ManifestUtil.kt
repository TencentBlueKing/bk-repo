package com.tencent.bkrepo.docker.manifest

import com.fasterxml.jackson.databind.JsonNode
import com.tencent.bkrepo.docker.model.DockerImageMetadata
import com.tencent.bkrepo.docker.model.ManifestMetadata

/**
 * manifest utility with each schema
 * @author: owenlxu
 * @date: 2020-02-05
 */
object ManifestUtil {

    fun populatePorts(manifestMetadata: ManifestMetadata, dockerMetadata: DockerImageMetadata) {
        dockerMetadata.config?.let {
            addPorts(manifestMetadata, dockerMetadata.config!!.exposedPorts)
        }

        dockerMetadata.containerConfig?.let {
            addPorts(manifestMetadata, dockerMetadata.containerConfig!!.exposedPorts)
        }
    }

    fun populateVolumes(manifestMetadata: ManifestMetadata, dockerMetadata: DockerImageMetadata) {
        dockerMetadata.config?.let {
            addVolumes(manifestMetadata, dockerMetadata.config!!.volumes)
        }

        dockerMetadata.containerConfig?.let {
            addVolumes(manifestMetadata, dockerMetadata.containerConfig!!.volumes)
        }
    }

    fun populateLabels(manifestMetadata: ManifestMetadata, dockerMetadata: DockerImageMetadata) {
        dockerMetadata.config?.let {
            addLabels(manifestMetadata, dockerMetadata.config!!.labels)
        }

        dockerMetadata.containerConfig?.let {
            addLabels(manifestMetadata, dockerMetadata.containerConfig!!.labels)
        }
    }

    private fun addPorts(manifestMetadata: ManifestMetadata, exposedPorts: JsonNode?) {
        exposedPorts?.let {
            val iterPorts = exposedPorts.fieldNames()
            while (iterPorts.hasNext()) {
                manifestMetadata.tagInfo.ports.add(iterPorts.next())
            }
        }
    }

    private fun addVolumes(manifestMetadata: ManifestMetadata, volumes: JsonNode?) {
        volumes?.let {
            val iterVolume = volumes.fieldNames()
            while (iterVolume.hasNext()) {
                manifestMetadata.tagInfo.volumes.add(iterVolume.next())
            }
        }
    }

    private fun addLabels(manifestMetadata: ManifestMetadata, labels: Map<String, String>?) {
        labels?.let {
            val iter = labels.entries.iterator()
            while (iter.hasNext()) {
                val label = iter.next()
                manifestMetadata.tagInfo.labels.put(label.key, label.value)
            }
        }
    }
}
