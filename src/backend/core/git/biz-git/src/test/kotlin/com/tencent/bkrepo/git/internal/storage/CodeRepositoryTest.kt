package com.tencent.bkrepo.git.internal.storage

import org.eclipse.jgit.junit.TestRepository
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class CodeRepositoryTest {

    @DisplayName("常用操作测试")
    @Test
    fun normalOperationsTest() {
        val dataService = InMemoryRepositoryDataService()
        val db = createRepository(dataService)
        val git = TestRepository(db)
        git.use {
            git.branch("master").commit().message("first commit").create()
            val blob = git.blob("hello")
            git.commit().add("file", blob).message("add data").message("0").create()
            Assertions.assertEquals(1, git.repository.refDatabase.refs.size)
            Assertions.assertEquals("refs/heads/master", git.repository.refDatabase.refs.first().name)
        }
        val db2 = createRepository(dataService)
        val git2 = TestRepository(db2)
        git2.use {
            Assertions.assertEquals(1, git.repository.refDatabase.refs.size)
            Assertions.assertEquals("refs/heads/master", git.repository.refDatabase.refs.first().name)
        }
    }

    private fun createRepository(dataService: RepositoryDataService = InMemoryRepositoryDataService()): CodeRepository {
        return CodeRepositoryBuilder(
            projectId = "ut-project",
            repoName = "ut-repo",
            storageCredentials = null,
            dataService = dataService,
            blockSize = 1024,
            lockProvider = EmptyLockProvider()
        ).build()
    }
}
