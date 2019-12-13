package com.tencent.bkrepo.docker.response

import com.google.common.collect.Sets
import com.tencent.bkrepo.docker.helpers.DockerPaginationElementsHolder

class TagsResponse {
    var name: String = ""
    var tags = Sets.newTreeSet<String>()

    constructor(elementsHolder: DockerPaginationElementsHolder, name: String) {
        this.name = name
        this.tags = elementsHolder.elements
    }


    constructor(name: String) {
        this.name = name
    }
}