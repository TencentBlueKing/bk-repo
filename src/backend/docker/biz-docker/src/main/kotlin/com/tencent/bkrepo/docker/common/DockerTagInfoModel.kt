package com.tencent.bkrepo.docker.common

import com.google.common.collect.Lists
import com.google.common.collect.Sets

/**
 * model to describe docker tag model
 */
data class DockerTagInfoModel(
    var title: String? = null,
    var digest: String? = null,
    var totalSize: String? = null,
    var totalSizeLong: Long = 0,
    var ports: Set<String> = Sets.newHashSet(),
    var volumes: Set<String> = Sets.newHashSet(),
    var labels: List<DockerLabel> = Lists.newArrayList()
)
