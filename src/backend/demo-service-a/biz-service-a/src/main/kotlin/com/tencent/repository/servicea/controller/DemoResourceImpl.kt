package com.tencent.repository.servicea.controller

import com.tencent.repository.servicea.api.DemoResource
import com.tencent.repository.servicea.pojo.User
import org.springframework.web.bind.annotation.RestController

@RestController
class DemoResourceImpl : DemoResource {

    override fun sayHello(name: String) = "Hello, $name!"

    override fun sayHello(user: User): String {
        return "Hello, ${user.name}, you are ${user.age} years old!"
    }

}
