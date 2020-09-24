package com.tencent.bkrepo.maven.service.impl

import com.tencent.bkrepo.common.api.util.readXmlString
import com.tencent.bkrepo.common.api.util.toXmlString
import com.tencent.bkrepo.maven.pojo.MavenMetadata
import com.tencent.bkrepo.maven.pojo.MavenVersioning
import com.tencent.bkrepo.maven.pojo.MavenVersions
import org.junit.jupiter.api.Test

class MavenServiceImplTest {
    @Test
    fun xmlTest() {
        val str = "<metadata>\n" +
            "    <groupId>com.tencent.bk.devops.atom</groupId>\n" +
            "    <artifactId>bksdk</artifactId>\n" +
            "    <versioning>\n" +
            "      <release>1.0.1</release>\n" +
            "      <versions>\n" +
            "        <version>1.0.0</version>\n" +
            "        <version>1.0.1</version>\n" +
            "      </versions>\n" +
            "      <lastUpdated>20200922132106</lastUpdated>\n" +
            "    </versioning>\n" +
            "  </metadata>"
        val mavenMetadata = str.readXmlString<MavenMetadata>()
    }
}
