package com.tencent.bkrepo.docker.response

import com.tencent.bkrepo.docker.helpers.DockerPaginationElementsHolder
import java.util.TreeSet

class CatalogResponse {
    var repositories: TreeSet<String> = TreeSet()

    constructor(elementsHolder: DockerPaginationElementsHolder) {
        this.repositories = elementsHolder.elements
    }
}
