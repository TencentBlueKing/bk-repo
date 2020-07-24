package com.tencent.bkrepo.docker.response

import com.google.common.collect.Sets
import com.tencent.bkrepo.common.api.constant.StringPool.EMPTY
import com.tencent.bkrepo.docker.helpers.DockerPaginationElementsHolder
import java.util.TreeSet

class TagsResponse {
    var name: String = EMPTY
    var tags: TreeSet<String> = Sets.newTreeSet<String>()

    constructor(elementsHolder: DockerPaginationElementsHolder, name: String) {
        this.name = name
        this.tags = elementsHolder.elements
    }
}
