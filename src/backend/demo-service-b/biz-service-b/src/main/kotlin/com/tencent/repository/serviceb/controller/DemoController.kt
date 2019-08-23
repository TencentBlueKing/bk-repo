package com.tencent.repository.serviceb.controller

import com.tencent.repository.servicea.api.DemoResource
import com.tencent.repository.servicea.pojo.User
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class DemoController @Autowired constructor(
    private val demoResource: DemoResource
){

    @GetMapping("/get")
    fun testGet() = demoResource.sayHello("Lily")

    @GetMapping("/post")
    fun testPost() = demoResource.sayHello(User(name = "Bob", age = 28))
}