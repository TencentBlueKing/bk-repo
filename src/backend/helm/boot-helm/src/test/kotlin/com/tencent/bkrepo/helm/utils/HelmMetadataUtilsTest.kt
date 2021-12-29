package com.tencent.bkrepo.helm.utils

import com.tencent.bkrepo.common.artifact.api.FileSystemArtifactFile
import java.io.File
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@DisplayName("helmMeta工具类测试")
@SpringBootTest
class HelmMetadataUtilsTest {
    @Test
    @DisplayName("HelmChartMetadata转换为Map测试")
    fun toMapTest() {
        val file = File("/XXXXX/test/helm-local/tomcat-0.4.1.tgz")
        val arFile = FileSystemArtifactFile(file)
        val chartMetadata = ChartParserUtil.parseChartFileInfo(arFile)
        println(HelmMetadataUtils.convertToMap(chartMetadata))
    }

    @Test
    @DisplayName("map转换为HelmChartMetadata测试")
    fun toObjectTest() {
        val file = File("/XXXX/test/helm-local/tomcat-0.4.1.tgz")
        val arFile = FileSystemArtifactFile(file)
        val chartMetadata = ChartParserUtil.parseChartFileInfo(arFile)
        val map = HelmMetadataUtils.convertToMap(chartMetadata)
        val newChartMetadata = HelmMetadataUtils.convertToObject(map)
        Assertions.assertEquals(chartMetadata, newChartMetadata)
    }
}
