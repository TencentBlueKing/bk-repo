package com.tencent.bkrepo.docker.v2.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import java.io.Serializable

class DockerImageMetadata : Serializable {
    @JsonProperty("id")
    var id: String? = null
    @JsonProperty("parent")
    var parent: String? = null
    @JsonProperty("created")
    var created: String? = null
    @JsonProperty("container")
    var container: String? = null
    @JsonProperty("docker_version")
    var dockerVersion: String? = null
    @JsonProperty("author")
    var author: String? = null
    @JsonProperty("container_config")
    var containerConfig: Config? = null
    @JsonProperty("config")
    var config: Config? = null
    @JsonProperty("architecture")
    var architecture: String? = null
    @JsonProperty("os")
    var os: String? = null
    @JsonProperty("Size")
    var size: Long = 0

    class Config : Serializable {
        @JsonProperty("Hostname")
        var hostname: String? = null
        @JsonProperty("Domainname")
        var domainname: String? = null
        @JsonProperty("User")
        var user: String? = null
        @JsonProperty("Memory")
        var memory: Long = 0
        @JsonProperty("MemorySwap")
        var memorySwap: Long = 0
        @JsonProperty("CpuShares")
        var cpuShares: Long = 0
        @JsonProperty("CpuSet")
        var cpuSet: String? = null
        @JsonProperty("AttachStdin")
        var attachStdin: Boolean = false
        @JsonProperty("AttachStdout")
        var attachStdout: Boolean = false
        @JsonProperty("AttachStderr")
        var attachStderr: Boolean = false
        @JsonProperty("PortSpecs")
        var portSpecs: List<String>? = null
        @JsonProperty("ExposedPorts")
        var exposedPorts: JsonNode? = null
        @JsonProperty("Tty")
        var tty: Boolean = false
        @JsonProperty("OpenStdin")
        var openStdin: Boolean = false
        @JsonProperty("StdinOnce")
        var stdinOnce: Boolean = false
        @JsonProperty("Env")
        var env: List<String>? = null
        @JsonProperty("Cmd")
        var cmd: List<String> ? = null
        @JsonProperty("Image")
        var image: String? = null
        @JsonProperty("Volumes")
        var volumes: JsonNode? = null
        @JsonProperty("WorkingDir")
        var workingDir: String? = null
        @JsonProperty("Entrypoint")
        var entrypoint: List<String>? = null
        @JsonProperty("NetworkDisabled")
        var networkDisabled: Boolean = false
        @JsonProperty("OnBuild")
        var onBuild: List<String>? = null
        @JsonProperty("Labels")
        var labels: Map<String, String>? = null
    }
}
