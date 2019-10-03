package com.tencent.bkrepo.auth.service

import com.tencent.bkrepo.auth.model.TRole
import com.tencent.bkrepo.auth.repository.RoleRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class RoleService @Autowired constructor(
    private val roleRepository: RoleRepository,
    private val mongoTemplate: MongoTemplate
) {
    fun addRole() {
        logger.info("add Role")
        val now = LocalDateTime.now()
        roleRepository.save(
            TRole(
                id = null,
                name = "name1",
                displayName = "displahName1",
                roleType = "type1",
                createdBy = "necrohuang",
                createdDate = now,
                lastModifiedBy = "necrohuang",
                lastModifiedDate = now
            )
        )

        logger.info("allRole: ${roleRepository.findAll()}")

    }


    companion object {
        private val logger = LoggerFactory.getLogger(RoleService::class.java)
    }
}
