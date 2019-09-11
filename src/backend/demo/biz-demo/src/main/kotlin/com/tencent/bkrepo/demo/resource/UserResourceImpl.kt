package com.tencent.bkrepo.demo.resource

import com.tencent.bkrepo.demo.api.UserResource
import com.tencent.bkrepo.demo.pojo.User
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.cloud.context.config.annotation.RefreshScope
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class UserResourceImpl : UserResource {

    @Autowired
    lateinit var foo: Foo


    override fun sayHello(name: String) = "Hello, $name!"

    override fun sayHello(user: User): String {
        return "Hello, ${user.name}, you are ${user.age} years old!"
    }

    @GetMapping
    fun config() = foo.bar
}


@Component
@ConfigurationProperties("foo")
class Foo {
    lateinit var bar: String
}

