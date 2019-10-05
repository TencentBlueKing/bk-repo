package com.tencent.bkrepo.registry.common

import com.google.common.collect.Lists
import com.google.common.collect.Sets

class DockerTagInfoModel {
    var title: String? = null
    var digest: String? = null
    var totalSize: String? = null
    var totalSizeLong: Long = 0
    var ports: Set<String> = Sets.newHashSet()
    var volumes: Set<String> = Sets.newHashSet()
    var labels: List<DockerLabel> = Lists.newArrayList()
}
