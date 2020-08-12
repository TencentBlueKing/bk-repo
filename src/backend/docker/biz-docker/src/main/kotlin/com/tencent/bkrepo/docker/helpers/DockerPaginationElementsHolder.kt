package com.tencent.bkrepo.docker.helpers

import com.google.common.collect.Sets
import java.util.TreeSet

class DockerPaginationElementsHolder {
    var hasMoreElements = false
    var elements: TreeSet<String> = Sets.newTreeSet<String>()

    fun addElement(element: String) {
        this.elements.add(element)
    }
}
