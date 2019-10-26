package com.tencent.bkrepo.docker.repomd

 class Artifact(contentLength: Long , fielSha256: String )  {

     var fielSha256 : String= ""
     var contentLength : Long = 0
     var path : String = ""
     init {
         this.fielSha256 = fielSha256
         this.contentLength = contentLength
     }

     fun getSha256():String{
         return  this.fielSha256
     }

     fun getLength():Long{
         return  this.contentLength
     }

     fun getArtifactPath():String {
         return  this.path
     }

     fun getRepoId() :String{
         return ""
     }
}
