package com.tencent.bkrepo.migrate.controller.en.defs

import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.migrate.http.CanwayHttpUtils
import com.tencent.bkrepo.migrate.pojo.MavenArtifact
import com.tencent.bkrepo.migrate.pojo.SyncResult
import com.tencent.bkrepo.migrate.pojo.suyan.SuyanSyncRequest
import cucumber.api.java8.En
import org.junit.Assert

class CicdArtifactSyncStepDefinitions : En {

    lateinit var syncResult: SyncResult

    init {
        val suyanSyncRequest = SuyanSyncRequest(
            repositoryName = "maven-nexus",
            groupId = "org.springframework.boot",
            artifactId = "spring-boot-build",
            version = "1.0.0.RELEASE",
            packaging = "pom",
            name = null,
            artifactList = mutableListOf(
                MavenArtifact(
                    groupId = "com.tencent.bk.devops.atom",
                    artifactId = "bksdk",
                    type = "jar",
                    version = "1.0.0"
                )
            ),
            docker = null,
            productList = null
        )

        When("^cicd send request$") {
            val url = "http://127.0.0.1:25915/syncMetaData/bkrepo/maven-nexus-sync"
            val body = suyanSyncRequest.toJsonString()
            val response = CanwayHttpUtils.doPost(url, body, basicAuth = null)
            syncResult = response.content.readJsonString()
        }
        Then("^cicd receive$") {
            Assert.assertTrue(syncResult.data)
        }
    }
}
