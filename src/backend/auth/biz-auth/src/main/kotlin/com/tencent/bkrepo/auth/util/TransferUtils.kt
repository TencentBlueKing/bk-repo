package com.tencent.bkrepo.auth.util

import com.tencent.bkrepo.auth.model.TProject
import com.tencent.bkrepo.auth.pojo.CreateUserRequest
import com.tencent.bkrepo.auth.pojo.Project
import com.tencent.bkrepo.auth.pojo.User

object TransferUtils {
    fun transferProject(tProject: TProject): Project {
        return Project(
            id = tProject.id!!,
            name = tProject.name,
            displayName = tProject.displayName,
            description = tProject.description
        )
    }
}