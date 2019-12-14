//package com.tencent.bkrepo.repository.service
//
//import com.tencent.bkrepo.common.artifact.constant.ArtifactMessageCode.FOLDER_CANNOT_BE_MODIFIED
//import com.tencent.bkrepo.common.artifact.constant.ArtifactMessageCode.NODE_IS_EXIST
//import com.tencent.bkrepo.common.artifact.constant.ArtifactMessageCode.NODE_NOT_FOUND
//import com.tencent.bkrepo.common.artifact.constant.ArtifactMessageCode.NODE_PATH_INVALID
//import com.tencent.bkrepo.common.artifact.constant.ArtifactMessageCode.REPOSITORY_NOT_FOUND
//import com.tencent.bkrepo.common.artifact.constant.ArtifactMessageCode.SHA256_CHECK_FAILED
//import org.junit.jupiter.api.Test
//
//import org.junit.jupiter.api.Disabled
//import org.junit.jupiter.api.DisplayName
//import org.springframework.beans.factory.annotation.Autowired
//import org.springframework.boot.test.context.SpringBootTest
//
///**
// *
// * @author: carrypan
// * @date: 2019-10-09
// */
//@Disabled
//@DisplayName("节点服务测试")
//@SpringBootTest
//internal class MessageCodeDetailServiceTest @Autowired constructor(
//        private val messageCodeDetailService: MessageCodeDetailService
//) {
//
//    @Test
//    @DisplayName("创建消息码")
//    fun create() {
//        messageCodeDetailService.create(MessageCodeCreateRequest( SYSTEM_ERROR.toString(), SystemModule.COMMON, "系统内部繁忙，请稍后再试", "系統內部繁忙，請稍後再試", "System service is busy, please try again later"))
//        messageCodeDetailService.create(MessageCodeCreateRequest( PARAMETER_IS_NULL.toString(), SystemModule.COMMON, "参数{0}不能为空", "參數{0}不能為空", "Parameter {0} cannot be null"))
//        messageCodeDetailService.create(MessageCodeCreateRequest( PARAMETER_IS_EXIST.toString(), SystemModule.COMMON, "参数{0}已经存在", "參數{0}已經存在", "Parameter {0} already exists"))
//        messageCodeDetailService.create(MessageCodeCreateRequest( PARAMETER_INVALID.toString(), SystemModule.COMMON, "参数{0}为非法数据", "參數{0}為非法數據", "Parameter {0} is invalid"))
//        messageCodeDetailService.create(MessageCodeCreateRequest( OAUTH_TOKEN_IS_INVALID.toString(), SystemModule.COMMON, "无效的token，请先oauth认证", "無效的token，請先oauth認證", "Invalid token, please authenticate oauth firstly"))
//        messageCodeDetailService.create(MessageCodeCreateRequest( PERMISSION_DENIED.toString(), SystemModule.COMMON, "权限不足", "權限不足", "Permission denied"))
//        messageCodeDetailService.create(MessageCodeCreateRequest( ERROR_SERVICE_NO_FOUND.toString(), SystemModule.COMMON, "找不到任何有效的{0}服务提供者", "找不到任何有效的{0}服務提供者", "No {0} service providers were found"))
//        messageCodeDetailService.create(MessageCodeCreateRequest( ELEMENT_NOT_FOUND.toString(), SystemModule.COMMON, "访问的资源{0}不存在", "訪問的資源{0}不存在", "The resource {0} does not exist"))
//        messageCodeDetailService.create(MessageCodeCreateRequest( ELEMENT_CANNOT_BE_MODIFIED.toString(), SystemModule.COMMON, "资源无法被编辑", "資源無法被編輯", "Resource cannot be edited"))
//        messageCodeDetailService.create(MessageCodeCreateRequest( NOT_SUPPORTED.toString(), SystemModule.COMMON, "不支持的功能", "不支持的功能", "Unsupported operation"))
//
//
//
//        messageCodeDetailService.create(MessageCodeCreateRequest( REPOSITORY_NOT_FOUND.toString(), SystemModule.REPOSITORY, "仓库{0}不存在", "倉庫{0}不存在", "Repository {0} does not exist"))
//        messageCodeDetailService.create(MessageCodeCreateRequest( NODE_NOT_FOUND.toString(), SystemModule.REPOSITORY, "节点{0}不存在", "節點{0}不存在", "Node {0} does not exist"))
//        messageCodeDetailService.create(MessageCodeCreateRequest( NODE_PATH_INVALID.toString(), SystemModule.REPOSITORY, "节点路径{0}非法", "節點路徑{0}非法", "Node path {0} is invalid"))
//        messageCodeDetailService.create(MessageCodeCreateRequest( FOLDER_CANNOT_BE_MODIFIED.toString(), SystemModule.REPOSITORY, "文件夹不能被覆盖", "文件夾不能被覆蓋", "Folder cannot be overwritten"))
//        messageCodeDetailService.create(MessageCodeCreateRequest( NODE_IS_EXIST.toString(), SystemModule.REPOSITORY, "节点{0}已存在", "節點{0}已存在", "Node {0} has existed"))
//        messageCodeDetailService.create(MessageCodeCreateRequest( SHA256_CHECK_FAILED.toString(), SystemModule.REPOSITORY, "文件sha256校验失败", "文件sha256校驗失敗", "Failed to check sha256"))
//
//
//        messageCodeDetailService.create(MessageCodeCreateRequest( "2511001", SystemModule.GENERIC, "uploadId{0}不存在", "uploadId{0}不存在", "uploadId {0} not does not exist"))
//
//    }
//}