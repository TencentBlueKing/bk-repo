package com.tencent.bkrepo.docker.v2.model

import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
import com.google.common.collect.Sets

class DockerTagInfo {
    var title: String = ""
    lateinit var digest: DockerDigest
    var totalSize: Long = 0
    var ports: MutableSet<String> = Sets.newHashSet()
    var volumes: MutableSet<String> = Sets.newHashSet()
    var labels: Multimap<String, String> = HashMultimap.create()
}
