package com.tencent.bkrepo.common.artifact.api

/**
 * 构件信息注解
 *
 * @author: carrypan
 * @date: 2019/11/19
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class ArtifactInfo {
    companion object {
        const val ARTIFACT_COORDINATE_URI = "/{projectId}/{repoName}/**"
    }
}
