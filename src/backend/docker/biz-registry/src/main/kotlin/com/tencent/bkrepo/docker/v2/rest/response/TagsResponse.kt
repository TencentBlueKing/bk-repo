package com.tencent.bkrepo.docker.v2.rest.response

import com.google.common.collect.Sets
import java.util.TreeSet
import com.tencent.bkrepo.docker.v2.helpers.DockerPaginationElementsHolder

class TagsResponse {
    var name: String = ""
    var tags = Sets.newTreeSet<String>()

    constructor(elementsHolder: DockerPaginationElementsHolder, name: String) {
        this.name = name
        this.tags = elementsHolder.elements
    }

    constructor() {}

    constructor(name: String) {
        this.name = name
    }
}