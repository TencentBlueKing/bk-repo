package com.tencent.bkrepo.demo.resource

import com.tencent.bkrepo.common.storage.core.FileStorage
import com.tencent.bkrepo.common.storage.util.FileDigestUtils
import com.tencent.bkrepo.demo.api.UserResource
import com.tencent.bkrepo.demo.pojo.User
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.io.File

@RestController
class UserResourceImpl : UserResource {

    @Autowired
    lateinit var foo: Foo

    @Autowired
    lateinit var fileStorage: FileStorage

    override fun sayHello(name: String) = "Hello, $name!"

    override fun sayHello(user: User): String {
        return "Hello, ${user.name}, you are ${user.age} years old!"
    }

    @GetMapping
    fun config() = foo.bar

    @GetMapping("/upload/{filename}")
    fun upload(@PathVariable filename: String): HashMap<String, String> {
        val map = HashMap<String, String>()
        val testFile = File(filename)

        if (testFile.exists()) {
            val fileSize = testFile.length() / 1024F / 1024F
            map["文件大小"] = "$fileSize MB"
            // 计算sha256
            var start = System.currentTimeMillis()
            val fileSha256 = FileDigestUtils.fileSha256(listOf(testFile.inputStream()))
            map["文件SHA256"] = fileSha256
            map["计算耗时"] = "${(System.currentTimeMillis() - start) / 1000F}秒"
            // 上传
            start = System.currentTimeMillis()
            fileStorage.store(fileSha256, testFile.inputStream())
            val uploadConsume = (System.currentTimeMillis() - start) / 1000F
            map["上传耗时"] = "${uploadConsume}秒"
            map["上传平均速度"] = "${fileSize / uploadConsume} MB/S"
        } else {
            map["message"] = "文件不存在"
        }
        return map
    }
}

@Component
@ConfigurationProperties("foo")
class Foo {
    lateinit var bar: String
}
