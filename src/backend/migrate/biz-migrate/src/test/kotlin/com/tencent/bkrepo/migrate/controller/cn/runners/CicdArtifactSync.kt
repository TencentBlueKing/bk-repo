package com.tencent.bkrepo.migrate.controller.cn.runners

import cucumber.api.CucumberOptions
import cucumber.api.junit.Cucumber
import org.junit.runner.RunWith

@RunWith(Cucumber::class)
@CucumberOptions(
    monochrome = true,
    features = ["classpath:features/sync/cn/cicd_artifact_sync.feature"],
    glue = ["con.tencent.bkrepo.migrate.controller.cn.defs"],
    plugin = ["html:target/cucumber"]
)
class CicdArtifactSync
