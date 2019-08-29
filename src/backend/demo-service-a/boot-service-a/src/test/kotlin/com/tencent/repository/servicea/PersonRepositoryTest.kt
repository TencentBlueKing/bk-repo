package com.tencent.repository.servicea

import com.tencent.repository.servicea.model.Gender
import com.tencent.repository.servicea.model.Person
import com.tencent.repository.servicea.repository.PersonRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.test.annotation.Rollback


@DisplayName("测试Spring Data MongoRepository")
@SpringBootTest
class PersonRepositoryTest @Autowired constructor(
        val personRepository: PersonRepository
) {

    @Test
    @Disabled("因MongoDB不支持事物，测试用例执行后不能回滚，而mobile为唯一索引，重复插入会报错，所以禁用该测试用例")
    @Rollback
    @DisplayName("测试MongoRepository插入数据")
    fun insertTest() {
        val person = Person(name = "Harden", mobile = "13888888888", gender = Gender.MALE)
        personRepository.insert(person)
        println("insert result: $person")
        assertNotNull(person.id)

    }

    @Test
    @DisplayName("测试MongoRepository CRUD操作")
    fun crudIntegrationTest() {
        // 插入
        val person = Person(name = "Harden", mobile = "13888888888", gender = Gender.MALE)
        personRepository.insert(person)
        // 插入成功会回写id
        assertNotNull(person.id)

        // 查询
        val findResult = personRepository.findByIdOrNull(person.id)!!
        assertEquals(findResult.name, person.name)
        assertEquals(findResult.gender, Gender.MALE)

        // 修改
        findResult.gender = Gender.UNKNOWN
        // 注意save与insert的区别
        // insert=插入，save = inert or update
        personRepository.save(findResult)
        val updateResult = personRepository.findByIdOrNull(person.id)!!
        assertEquals(updateResult.gender, Gender.UNKNOWN)

        // 删除
        personRepository.deleteById(person.id!!)
        assertNull(personRepository.findByIdOrNull(person.id))

    }

    @Test
    @DisplayName("测试MongoRepository查询所有数据")
    fun findAllTest() {
        val result = personRepository.findAll()
        assertNotNull(result)
        result.forEach { println(it.name) }
    }

    @Test
    @DisplayName("测试MongoRepository分页查询")
    fun pageTest() {
        // spring data 中的Pageable是从0页开始
        val page = personRepository.findAll(PageRequest.of(0, 10)) as PageImpl
        println("totalElements: ${page.totalElements}")
        println("totalPages: ${page.totalPages}")
        println("isFirst: ${page.isFirst}")
        println("isLast: ${page.isLast}")
        page.forEach{ println(it.name)}
    }

    @Test
    @DisplayName("测试MongoRepository 自定义条件查询")
    fun findByAgeGreaterThanTest() {
        val page = personRepository.findByAgeGreaterThan(40, PageRequest.of(0, 10))

        page.forEach{ println(it.name)}
    }

    @Test
    @DisplayName("测试MongoRepository 自定义条件Query查询")
    fun findByNameAndAgeRangeTest() {
        val page = personRepository.findByNameAndAgeRange("Jam", 30, 35, PageRequest.of(0, 10))

        page.forEach{ println(it.name)}
    }


}