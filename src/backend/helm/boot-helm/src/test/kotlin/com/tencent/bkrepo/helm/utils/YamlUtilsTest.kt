package com.tencent.bkrepo.helm.utils

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@DisplayName("yaml工具类测试")
@SpringBootTest
class YamlUtilsTest {
    @Test
    @DisplayName("yaml转换为json测试")
    fun yaml2JsonTest() {
        val jsonString = YamlUtils.yaml2Json(yamlStr.byteInputStream())
        println(jsonString)
    }

    @Test
    fun convertStringToEntityTest() {
        val map = YamlUtils.convertStringToEntity<Map<String, Any>>(yamlStr)
        Assertions.assertEquals(map.size, 4)
    }

    companion object {
        val yamlStr = "apiVersion: v1\n" +
            "entries:\n" +
            "  bk-redis:\n" +
            "  - apiVersion: v1\n" +
            "    appVersion: '1.0'\n" +
            "    description: 这是一个测试示例\n" +
            "    name: bk-redis\n" +
            "    version: 0.1.1\n" +
            "    urls:\n" +
            "    - http://localhost:10021/test/helm-local/charts/bk-redis-0.1.1.tgz\n" +
            "    created: '2020-06-24T09:24:41.135Z'\n" +
            "    digest: e755d7482cb0422f9c3f7517764902c94bab7bcf93e79b6277c49572802bfba2\n" +
            "generated: '2020-06-24T09:26:05.026Z'\n" +
            "serverInfo: {}"
    }
}
