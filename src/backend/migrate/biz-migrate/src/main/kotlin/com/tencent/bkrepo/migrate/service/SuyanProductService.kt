package com.tencent.bkrepo.migrate.service

import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.migrate.dao.suyan.SuyanMavenArtifactDao
import com.tencent.bkrepo.migrate.model.suyan.TSuyanMavenArtifact
import com.tencent.bkrepo.migrate.pojo.BkProduct
import org.springframework.stereotype.Service

@Service
class SuyanProductService(
    private val suyanMavenArtifactDao: SuyanMavenArtifactDao
) {

    fun findFirstByRepositoryNameAndGroupIdAndArtifactIdAndVersionAndType(
        repositoryName: String,
        groupId: String,
        artifactId: String,
        version: String,
        type: String
    ): List<BkProduct>? {
        val tSuyanMavenArtifact =
            suyanMavenArtifactDao.findFirstByRepositoryNameAndGroupIdAndArtifactIdAndVersionAndType(
                repositoryName, groupId, artifactId, version, type
            ) ?: return null
        return transfer(tSuyanMavenArtifact)
    }

    fun transfer(tSuyanMavenArtifact: TSuyanMavenArtifact): List<BkProduct> {
        val productList = mutableListOf<BkProduct>()
        tSuyanMavenArtifact.productList?.let {
            for (productStr in it) {
                productList.add(productStr.readJsonString())
            }
        }
        return productList
    }
}
