package com.tencent.bkrepo.docker.helpers

import com.google.common.collect.Sets


class DockerPaginationElementsHolder {
    var hasMoreElements = false
    var elements = Sets.newTreeSet<String>()

    fun addElement(element: String) {
        this.elements.add(element)
    }
}