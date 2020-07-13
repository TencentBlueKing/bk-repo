package com.tencent.bkrepo.docker.model

import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
import com.google.common.collect.Sets

/**
 * docker tag info
 * @author: owenlxu
 * @date: 2019-10-15
 */
data class DockerTagInfo(
    var title: String = "",
    var digest: DockerDigest? = null,
    var totalSize: Long = 0L,
    var ports: MutableSet<String> = Sets.newHashSet(),
    var volumes: MutableSet<String> = Sets.newHashSet(),
    var labels: Multimap<String, String> = HashMultimap.create()
)
