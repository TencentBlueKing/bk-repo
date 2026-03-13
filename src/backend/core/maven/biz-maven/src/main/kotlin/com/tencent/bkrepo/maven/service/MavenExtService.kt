package com.tencent.bkrepo.maven.service

import com.tencent.bkrepo.common.api.exception.ParameterInvalidException
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import com.tencent.bkrepo.common.metadata.service.node.NodeSearchService
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Sort
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.maven.pojo.request.MavenArtifactSearchRequest
import com.tencent.bkrepo.maven.pojo.request.MavenGroupSearchRequest
import com.tencent.bkrepo.maven.pojo.request.MavenVersionSearchRequest
import com.tencent.bkrepo.maven.pojo.response.MavenGAVCResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class MavenExtService(
    private val nodeSearchService: NodeSearchService,
    private val mavenMetadataService: MavenMetadataService,
    private val permissionManager: PermissionManager,
) {

    @Value("\${maven.domain:http://127.0.0.1:25803}")
    val mavenDomain = ""

    fun gavc(
        projectId: String,
        pageNumber: Int,
        pageSize: Int,
        g: String?,
        a: String?,
        v: String?,
        c: String?,
        repos: String?
    ): Response<Page<MavenGAVCResponse.UriResult>> {
        gavcCheck(g, a, v, c)
        val result = buildGavcQuery(projectId, pageNumber, pageSize, g, a, v, c, repos)
        val list = result.data?.records?.map {
            MavenGAVCResponse.UriResult("$mavenDomain/${it["projectId"]}/${it["repoName"]}${it["fullPath"]}")
        }
        val page = Page(
            pageNumber = result.data!!.pageNumber,
            pageSize = result.data!!.pageSize,
            totalRecords = result.data!!.totalRecords,
            totalPages = result.data!!.totalPages,
            records = list!!
        )
        return ResponseBuilder.success(page)
    }

    private fun gavcCheck(g: String?, a: String?, v: String?, c: String?) {
        var result = g.isNullOrBlank()
        listOf(a, v, c).map {
            result = it.isNullOrBlank() && result
        }
        if (result) throw ParameterInvalidException("$g|$a|$v|$c")
    }

    private fun buildGavcQuery(
        projectId: String,
        pageNumber: Int,
        pageSize: Int,
        g: String?,
        a: String?,
        v: String?,
        c: String?,
        repos: String?
    ): Response<Page<Map<String, Any?>>> {
        val rules = mutableListOf<Rule>()
        val repoRules = mutableListOf<Rule>()
        val metadataRules = mutableListOf<Rule>()
        val projectRule = Rule.QueryRule("projectId", projectId)
        g?.let { metadataRules.add(Rule.QueryRule("metadata.groupId", g)) }
        a?.let { metadataRules.add(Rule.QueryRule("metadata.artifactId", a)) }
        v?.let { metadataRules.add(Rule.QueryRule("metadata.version", v)) }
        c?.let { metadataRules.add(Rule.QueryRule("metadata.classifier", c)) }
        repos?.let {
            val repoList = repos.trim(',').split(",")
            for (repo in repoList) {
                if (repo.isNotBlank()) repoRules.add(Rule.QueryRule("repoName", repo))
            }
        }
        rules.add(projectRule)
        rules.add(Rule.QueryRule("folder", false))
        if (repoRules.isNotEmpty()) rules.add(Rule.NestedRule(repoRules, Rule.NestedRule.RelationType.OR))
        rules.add(Rule.NestedRule(metadataRules, Rule.NestedRule.RelationType.AND))

        val rule = Rule.NestedRule(
            rules, Rule.NestedRule.RelationType.AND
        )
        val queryModel = QueryModel(
            page = PageLimit(pageNumber = pageNumber, pageSize = pageSize),
            sort = Sort(properties = listOf("lastModifiedDate"), direction = Sort.Direction.ASC),
            select = listOf("projectId", "repoName", "fullPath"),
            rule = rule
        )
        return ResponseBuilder.success(nodeSearchService.search(queryModel))
    }


    fun searchGroup(request: MavenGroupSearchRequest): Page<String> {
        permissionManager.checkRepoPermission(PermissionAction.READ, request.projectId, request.repoName)
        return mavenMetadataService.getGroupByPage(request)
    }

    fun searchArtifact(request: MavenArtifactSearchRequest): Page<String> {
        permissionManager.checkRepoPermission(PermissionAction.READ, request.projectId, request.repoName)
        return mavenMetadataService.getArtifactByPage(request)
    }

    fun searchVersion(request: MavenVersionSearchRequest): Page<String> {
        permissionManager.checkRepoPermission(PermissionAction.READ, request.projectId, request.repoName)
        return mavenMetadataService.getVersionByPage(request)
    }
}
