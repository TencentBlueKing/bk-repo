package com.tencent.bkrepo.npm.dao.repository

import com.tencent.bkrepo.npm.model.TModuleDeps
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface ModuleDepsRepository : MongoRepository<TModuleDeps, String>
