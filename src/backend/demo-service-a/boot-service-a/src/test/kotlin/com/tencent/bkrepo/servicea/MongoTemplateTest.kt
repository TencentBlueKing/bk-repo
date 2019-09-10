package com.tencent.bkrepo.servicea

import com.tencent.bkrepo.servicea.model.Person
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query

@DisplayName("测试MongoTemplate")
@SpringBootTest
class MongoTemplateTest @Autowired constructor(
        val mongoTemplate: MongoTemplate
){
    @Test
    @DisplayName("自定义构造Query 和 Criteria条件查询")
    fun queryTest(){
        // 通过Query 和 Criteria 可以构造出任意复杂条件查询
        val query = Query(Criteria.where("age").gte(35)).with(PageRequest.of(0, 10))

        val result = mongoTemplate.find(query, Person::class.java)
        val count = mongoTemplate.count(query, Person::class.java)

        println(count)

        result.forEach{ println(it.name)}
    }

}