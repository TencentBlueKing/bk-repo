package com.tencent.bkrepo.common.artifact.permission

import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import com.tencent.bkrepo.common.artifact.config.REPO_KEY
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.exception.PermissionCheckException
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.repository.api.RepositoryResource
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

/**
 *
 * @author: carrypan
 * @date: 2019/11/22
 */
@Aspect
class PermissionAspect {

    @Autowired
    private lateinit var permissionCheckHandler: PermissionCheckHandler

    @Autowired
    private lateinit var repositoryResource: RepositoryResource

    @Autowired
    private lateinit var artifactConfiguration: ArtifactConfiguration

    @Around("@annotation(com.tencent.bkrepo.common.artifact.permission.Permission)")
    @Throws(Throwable::class)
    fun around(point: ProceedingJoinPoint): Any? {
        val signature = point.signature as MethodSignature
        val method = signature.method
        val permission = method.getAnnotation(Permission::class.java)

        val request = HttpContextHolder.getRequest()
        val userId = request.getAttribute(USER_KEY) as? String ?: ANONYMOUS_USER

        return try {
            val artifactInfo = findArtifactInfo(point.args)
            val repositoryInfo = queryRepositoryInfo(artifactInfo)
            request.setAttribute(REPO_KEY, repositoryInfo)
            permissionCheckHandler.onPermissionCheck(userId, permission, artifactInfo, repositoryInfo)
            logger.debug("User[$userId] check permission [$permission] on [$artifactInfo] success.")
            permissionCheckHandler.onPermissionCheckSuccess()
            point.proceed()
        } catch (exception: PermissionCheckException) {
            logger.info("User[$userId] check permission [$permission] on failed.")
            permissionCheckHandler.onPermissionCheckFailed(exception)
            null
        }
    }

    private fun findArtifactInfo(args: Array<Any>): ArtifactInfo {
        for (argument in args) {
            if (argument is ArtifactInfo) return argument
        }
        throw PermissionCheckException("Missing ArtifactInfo argument.")
    }

    private fun queryRepositoryInfo(artifactInfo: ArtifactInfo): RepositoryInfo {
        with(artifactInfo) {
            val repositoryType = artifactConfiguration.getRepositoryType()
            val response = if (repositoryType == RepositoryType.NONE) {
                repositoryResource.detail(projectId, repoName)
            } else {
                repositoryResource.detail(projectId, repoName, repositoryType.name)
            }
            return response.data ?: throw ArtifactNotFoundException("Repository[$repoName] not found")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PermissionAspect::class.java)
    }
}
