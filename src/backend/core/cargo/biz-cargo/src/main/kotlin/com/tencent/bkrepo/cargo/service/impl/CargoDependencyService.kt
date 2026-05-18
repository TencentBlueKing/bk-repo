package com.tencent.bkrepo.cargo.service.impl

import com.github.zafarkhaja.semver.Version
import com.tencent.bkrepo.cargo.model.TCargoVersionDependency
import com.tencent.bkrepo.cargo.pojo.index.IndexDependency
import com.tencent.bkrepo.cargo.pojo.user.CargoDependencyInfo
import com.tencent.bkrepo.cargo.pojo.user.CargoDependentInfo
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class CargoDependencyService(
    private val mongoTemplate: MongoTemplate
) {
    fun refreshByUpload(
        projectId: String,
        repoName: String,
        crateName: String,
        version: String,
        dependencies: List<IndexDependency>
    ) {
        removeBySource(projectId, repoName, crateName, version)
        if (dependencies.isEmpty()) {
            return
        }
        val now = LocalDateTime.now()
        val list = dependencies.map {
            TCargoVersionDependency(
                projectId = projectId,
                repoName = repoName,
                srcPackageName = crateName,
                srcVersion = version,
                depPackageName = it.name,
                depVersionReq = it.req,
                kind = it.kind,
                optional = it.optional,
                target = it.target,
                defaultFeatures = it.defaultFeatures,
                features = it.features,
                createdDate = now,
                lastModifiedDate = now
            )
        }
        mongoTemplate.insert(list, TCargoVersionDependency::class.java)
    }

    fun removeByPackageVersion(projectId: String, repoName: String, packageName: String, version: String) {
        removeBySource(projectId, repoName, packageName, version)
    }

    fun removeByPackage(projectId: String, repoName: String, packageName: String) {
        removeBySource(projectId, repoName, packageName, null)
    }

    fun queryDeps(projectId: String, repoName: String, packageKey: String, version: String): List<CargoDependencyInfo> {
        val packageName = PackageKeys.resolveCargo(packageKey)
        val query = Query.query(
            Criteria.where(TCargoVersionDependency::projectId.name).`is`(projectId)
                .and(TCargoVersionDependency::repoName.name).`is`(repoName)
                .and(TCargoVersionDependency::srcPackageName.name).`is`(packageName)
                .and(TCargoVersionDependency::srcVersion.name).`is`(version)
        ).with(Sort.by(TCargoVersionDependency::depPackageName.name))
        return mongoTemplate.find(query, TCargoVersionDependency::class.java).map {
            CargoDependencyInfo(
                packageKey = PackageKeys.ofCargo(it.depPackageName),
                packageName = it.depPackageName,
                versionReq = it.depVersionReq,
                kind = it.kind,
                optional = it.optional,
                target = it.target,
                defaultFeatures = it.defaultFeatures,
                features = it.features
            )
        }
    }

    fun queryDependents(
        projectId: String,
        repoName: String,
        packageKey: String,
        version: String,
        pageNumber: Int,
        pageSize: Int
    ): Page<CargoDependentInfo> {
        val packageName = PackageKeys.resolveCargo(packageKey)
        val request = Pages.ofRequest(pageNumber, pageSize)
        val criteria = Criteria.where(TCargoVersionDependency::projectId.name).`is`(projectId)
            .and(TCargoVersionDependency::repoName.name).`is`(repoName)
            .and(TCargoVersionDependency::depPackageName.name).`is`(packageName)
        val sort = Sort.by(
            Sort.Order.asc(TCargoVersionDependency::srcPackageName.name),
            Sort.Order.asc(TCargoVersionDependency::srcVersion.name)
        )
        val start = request.offset
        val end = start + pageSize
        // projection: 只取分页和结果构建所需字段，减少文档传输量
        val query = Query.query(criteria).with(sort).apply {
            fields().include(
                TCargoVersionDependency::srcPackageName.name,
                TCargoVersionDependency::srcVersion.name,
                TCargoVersionDependency::depVersionReq.name,
                TCargoVersionDependency::kind.name,
                TCargoVersionDependency::optional.name,
                TCargoVersionDependency::target.name
            )
        }
        var matched = 0L
        val records = mutableListOf<CargoDependentInfo>()
        mongoTemplate.stream(
            query,
            CargoDependentProjection::class.java,
            mongoTemplate.getCollectionName(TCargoVersionDependency::class.java)
        ).use { stream ->
            for (it in stream) {
                if (!matchVersionReq(version, it.depVersionReq)) {
                    continue
                }
                if (matched in start until end) {
                    records.add(
                        CargoDependentInfo(
                            packageKey = PackageKeys.ofCargo(it.srcPackageName),
                            packageName = it.srcPackageName,
                            version = it.srcVersion,
                            versionReq = it.depVersionReq,
                            kind = it.kind,
                            optional = it.optional,
                            target = it.target
                        )
                    )
                }
                matched++
            }
        }
        return Pages.ofResponse(request, matched, records)
    }

    private fun removeBySource(projectId: String, repoName: String, srcPackageName: String, version: String?) {
        val criteria = Criteria.where(TCargoVersionDependency::projectId.name).`is`(projectId)
            .and(TCargoVersionDependency::repoName.name).`is`(repoName)
            .and(TCargoVersionDependency::srcPackageName.name).`is`(srcPackageName)
        if (!version.isNullOrBlank()) {
            criteria.and(TCargoVersionDependency::srcVersion.name).`is`(version)
        }
        mongoTemplate.remove(Query.query(criteria), TCargoVersionDependency::class.java)
    }

    private fun matchVersionReq(version: String, rawReq: String): Boolean {
        val req = rawReq.trim()
        if (req.isEmpty() || req == "*") {
            return true
        }
        return try {
            Version.valueOf(version).satisfies(req.replace(",", " "))
        } catch (ignored: Exception) {
            // 对无法识别的约束采取保守策略，避免漏掉真实依赖方
            logger.debug("Unsupported cargo version requirement [$rawReq], fallback to include.")
            true
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CargoDependencyService::class.java)
    }

    private data class CargoDependentProjection(
        val srcPackageName: String = "",
        val srcVersion: String = "",
        val depVersionReq: String = "",
        val kind: String? = null,
        val optional: Boolean = false,
        val target: String? = null
    )
}
