package com.tencent.bkrepo.repository.dao.repository

import com.tencent.bkrepo.repository.model.TModuleDeps
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface ModuleDepsRepository : MongoRepository<TModuleDeps, String>
