package com.tencent.bkrepo.demo.repository

import com.tencent.bkrepo.demo.model.Person
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository

/**
 * MongoRepository 提供了很多数据库操作方法，它按照spring data repository接口形式进行了封装，屏蔽了mongo底层实现。
 * 底层是通过mongoTemplate来完成的操作，我们也可以直接使用mongoTemplate
 */
@Repository
interface PersonRepository : MongoRepository<Person, String> {

    /**
     * 按照指定规则声明接口，spring data会自动实现查询条件的封装
     * 参考https://docs.spring.io/spring-data/mongodb/docs/2.1.8.RELEASE/reference/html
     */
    fun findByAgeGreaterThan(age: Int, page: Pageable): Page<Person>

    /**
     * 自定义查询，Query里面是mongodb的查询语法，通过index来传递参数
     * fields用来指定查询的字段，不指定则查询所有字段
     * 注意kotlin中$需要使用转义符，因为$是kotlin的字符串模板渲染符号
     */
    @Query("{'name':{'\$regex':?0}, 'age': {'\$gte':?1, '\$lte':?2 }}", fields = "{'id' : 1, 'name': 1, 'mobile': 1 }")
    fun findByNameAndAgeRange(name: String, ageFrom: Int, ageTo: Int, page: Pageable): Page<Person>
}
