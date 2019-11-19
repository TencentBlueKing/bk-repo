package com.tencent.bkrepo.common.artifact.locator

/**
 * 构件定位器注解
 *
 * @author: carrypan
 * @date: 2019/11/19
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class ArtifactLocator {
    companion object {
        const val ARTIFACT_LOCATE_URI = "/{projectId}/{repoName}/**"
    }
}
