package com.tencent.bkrepo.docker.v2.rest.response

import java.util.TreeSet
import com.tencent.bkrepo.docker.v2.helpers.DockerPaginationElementsHolder

class CatalogResponse {
    var repositories: TreeSet<String> = TreeSet()

    constructor() {}

    constructor(elementsHolder: DockerPaginationElementsHolder) {
        this.repositories = elementsHolder.elements
    }
}