package com.tencent.bkrepo.docker.v2.helpers

import com.google.common.collect.Sets
import java.util.TreeSet


class DockerPaginationElementsHolder {
    var hasMoreElements = false
    var elements = Sets.newTreeSet<String>()

    fun addElement(element: String) {
        this.elements.add(element)
    }
}