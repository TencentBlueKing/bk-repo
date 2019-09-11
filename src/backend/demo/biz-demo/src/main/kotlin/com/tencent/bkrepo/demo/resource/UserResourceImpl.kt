package com.tencent.bkrepo.demo.resource

import com.tencent.bkrepo.demo.api.UserResource
import com.tencent.bkrepo.demo.pojo.User
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class UserResourceImpl : UserResource {

    override fun sayHello(name: String) = "Hello, $name!"

    override fun sayHello(user: User): String {
        return "Hello, ${user.name}, you are ${user.age} years old!"
    }
}
