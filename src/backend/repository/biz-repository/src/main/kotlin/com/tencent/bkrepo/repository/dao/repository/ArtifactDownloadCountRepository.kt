package com.tencent.bkrepo.repository.dao.repository

import com.tencent.bkrepo.repository.model.TArtifactDownloadCount
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface ArtifactDownloadCountRepository : MongoRepository<TArtifactDownloadCount,String>