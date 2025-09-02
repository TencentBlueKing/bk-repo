package com.tencent.bkrepo.repository.controller.user

import com.google.common.net.HttpHeaders
import com.tencent.bkrepo.repository.pojo.experience.AppExperienceChangeLogRequest
import com.tencent.bkrepo.repository.pojo.experience.AppExperienceRequest
import com.tencent.bkrepo.repository.service.experience.ExperienceService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/experience")
class UserExperienceController(
    private val experienceService: ExperienceService
) {

    @PostMapping("/list")
    fun listAppExperience(
        @RequestAttribute userId: String,
        @RequestBody request: AppExperienceRequest
    ): ResponseEntity<Any> {
        val response = experienceService.list(userId, request)
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).body(response)
    }

    @PostMapping("/detail/{experienceId}")
    fun detail(
        @RequestAttribute userId: String,
        @PathVariable experienceId: String,
        @RequestBody request: AppExperienceRequest
    ): ResponseEntity<Any> {
        val response = experienceService.getExperienceDetail(userId, experienceId, request)
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).body(response)
    }

    @PostMapping("/changelog/{experienceId}")
    fun changelog(
        @RequestAttribute userId: String,
        @PathVariable experienceId: String,
        @RequestBody request: AppExperienceChangeLogRequest
    ): ResponseEntity<Any> {
        val response = experienceService.getExperienceChangeLog(userId, experienceId, request)
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).body(response)
    }

    @PostMapping("/installPackages/{experienceId}")
    fun installPackages(
        @RequestAttribute userId: String,
        @PathVariable experienceId: String,
        @RequestBody request: AppExperienceRequest
    ): ResponseEntity<Any> {
        val response = experienceService.getExperienceInstallPackages(userId, experienceId, request)
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).body(response)
    }
}
