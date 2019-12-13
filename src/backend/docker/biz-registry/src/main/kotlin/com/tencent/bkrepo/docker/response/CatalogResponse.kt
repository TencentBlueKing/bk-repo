package com.tencent.bkrepo.docker.response

import java.util.TreeSet
import com.tencent.bkrepo.docker.helpers.DockerPaginationElementsHolder

class CatalogResponse {
    var repositories: TreeSet<String> = TreeSet()

    constructor(elementsHolder: DockerPaginationElementsHolder) {
        this.repositories = elementsHolder.elements
    }
}