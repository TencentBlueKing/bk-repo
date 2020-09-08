package com.tencent.bkrepo.docker.helper

import com.tencent.bkrepo.docker.helpers.DockerCatalogTagsSlicer
import com.tencent.bkrepo.docker.helpers.DockerPaginationElementsHolder
import com.tencent.bkrepo.docker.response.CatalogResponse
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class DockerCatalogTagsSlicerTest {

    @Test
    @DisplayName("测试分页索引")
    fun testSliceCatalog() {
        val maxEntries = 3
        val lastEntry = "apache"
        val manifests = mapOf("nginx" to "v1", "linux" to "latest", "apache" to "v1", "java" to "v1")
        val elementsHolder = DockerPaginationElementsHolder()

        manifests.forEach {
            if (it.key.isNotBlank()) {
                elementsHolder.addElement(it.key)
            }
            DockerCatalogTagsSlicer.sliceCatalog(elementsHolder, maxEntries, lastEntry)
        }

        val catalogResponse = CatalogResponse(elementsHolder)
        Assertions.assertEquals(catalogResponse.repositories.size, 1)
    }
}
