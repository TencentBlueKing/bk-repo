package com.tencent.bkrepo.analyst.pojo.execution

import io.swagger.annotations.ApiModelProperty

@Suppress("LongParameterList", "MagicNumber")
class DockerExecutionCluster(
    override val name: String,
    @ApiModelProperty("docker host")
    val host: String = "unix://var/run/docker.sock",
    @ApiModelProperty("docker api version")
    val version: String = "1.23",
    @ApiModelProperty("docker api connect timeout")
    val connectTimeout: Int = 5000,
    @ApiModelProperty("docker api read timeout")
    val readTimeout: Int = 0,
    @ApiModelProperty("docker api write timeout")
    val maxTaskCount: Int = 1
) : ExecutionCluster(name, type) {
    companion object {
        const val type: String = "docker"
    }
}
