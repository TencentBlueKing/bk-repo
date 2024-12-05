/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.preview.config.configuration

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

@Configuration
class PreviewConfig {
    // 通用设置
    /**
     * projectId
     */
    @Value("\${preview.projectId:bk-repo}")
    val projectId = "bk-repo"

    /**
     * repoName
     */
    @Value("\${preview.repoName:convert}")
    val repoName = "convert"

    /**
     * 临时文件保存路径
     */
    @Value("\${preview.file.dir:/data/workspace/tmp}")
    val fileDir = "/data/workspace/tmp"

    /**
     * 禁止下载的文件后缀
     */
    @Value("\${preview.prohibitSuffix:exe,dll}")
    val prohibitSuffix = "exe,dll"

    /**
     * 预览仓库配额,默认10G
     */
    @Value("\${preview.repoQuota:10485760}")
    val repoQuota: Long? = 10485760

    /**
     * 预览仓库制品保留天数,默认7天
     */
    @Value("\${preview.artifactKeepDays:7}")
    val artifactKeepDays: Long? = 7

    /**
     * 预览地址缓存时间,单位s, 默认0不缓存
     */
    @Value("\${preview.fullPathCacheTime:0}")
    val fullPathCacheTime: Long? = 0

    /**
     * 预览服务domain
     */
    @Value("\${preview.domain}")
    val domain: String? = null

    /**
     * generic服务domain
     */
    @Value("\${preview.genericDomain}")
    val genericDomain: String? = null

    // office 相关配置
    /**
     * openoffice 或 LibreOffice 的 home 路径
     */
    @Value("\${preview.office.home:}")
    val officeHome: String? = null

    /**
     * office 转换服务的端口，默认开启两个进程
     */
    @Value("\${preview.office.plugin.server.ports:2001,2002}")
    val officePluginServerPorts = "2001,2002"

    /**
     * office 转换服务 task 超时时间，默认五分钟
     */
    @Value("\${preview.office.plugin.task.timeout:5m}")
    val officePluginTaskTimeout = "5m"

    /**
     * 此属性设置 office 进程在重新启动之前可以执行的最大任务数。0 表示无限数量的任务（永远不会重新启动）
     */
    @Value("\${preview.office.plugin.task.maxTasksPerProcess:200}")
    val officePluginTaskMaxTasksPerProcess = 200

    /**
     * 此属性设置处理任务所允许的最长时间。如果任务的处理时间长于此超时，则此任务将中止，并处理下一个任务
     */
    @Value("\${preview.office.plugin.task.taskExecutionTimeout:5m}")
    val officePluginTaskExecutionTimeout = "5m"

    /**
     * 生成限制，默认不限，设置使用方法 (1-5)
     */
    @Value("\${preview.office.pageRange:false}")
    val isOfficePageRange = false

    /**
     * 生成水印，默认不启用
     */
    @Value("\${preview.office.watermark:false}")
    val isOfficeWatermark = false

    /**
     * OFFICE JPEG 图片压缩
     */
    @Value("\${preview.office.quality:80}")
    val officeQuality = 80

    /**
     * 图像分辨率限制
     */
    @Value("\${preview.office.maxImageResolution:150}")
    val officeMaxImageResolution = 150

    /**
     * 导出书签
     */
    @Value("\${preview.office.exportBookmarks:true}")
    val isOfficeExportBookmarks = true

    /**
     * 批注作为 PDF 的注释
     */
    @Value("\${preview.office.exportNotes:true}")
    val isOfficeExportNotes = true

    /**
     * 加密文档生成的 PDF 文档，添加密码（密码为加密文档的密码）
     */
    @Value("\${preview.office.documentOpenPasswords:true}")
    val isOfficeDocumentOpenPasswords = true

    /**
     * xlsx 格式前端解析
     */
    @Value("\${preview.office.type.web:web}")
    val officeTypeWeb = "web"

    /**
     * office 类型文档 (word, ppt) 样式，默认为pdf,也可以是图片image
     */
    @Value("\${preview.office.preview.type:pdf}")
    val officePreviewType = "pdf"

    /**
     * 是否关闭 office 预览切换开关，默认为 false，可配置为 true 关闭
     */
    @Value("\${preview.office.preview.switch.disabled:true}")
    val isOfficePreviewSwitchDisabled = true
    // PDF 相关配置
    /**
     * 配置 PDF 文件生成图片的像素大小，dpi 越高，图片质量越清晰，同时也会消耗更多的计算资源
     */
    @Value("\${preview.pdf2jpg.dpi:144}")
    val pdfToJpgDpi = 144

    /**
     * 是否禁止演示模式
     */
    @Value("\${preview.pdf.presentationMode.disable:true}")
    val isPdfPresentationModeDisable = true

    /**
     * 是否禁止打开文件
     */
    @Value("\${preview.pdf.openFile.disable:true}")
    val isPdfOpenFileDisable = true

    /**
     * 是否禁止打印转换生成的 PDF 文件
     */
    @Value("\${preview.pdf.print.disable:true}")
    val isPdfPrintDisable = true

    /**
     * 是否禁止下载转换生成的 PDF 文件
     */
    @Value("\${preview.pdf.download.disable:true}")
    val isPdfDownloadDisable = true

    /**
     * 是否禁止 bookmarkFileConvertQueueTask
     */
    @Value("\${preview.pdf.bookmark.disable:true}")
    val isPdfBookmarkDisable = true

    /**
     * 是否禁止签名
     */
    @Value("\${preview.pdf.disable.editing:true}")
    val isPdfDisableEditing = true
    // FTP配置
    /**
     * ftp用户名
     */
    @Value("\${preview.ftp.username:ftpuser}")
    val ftpUsername = "ftpuser"

    /**
     * ftp密码
     */
    @Value("\${preview.ftp.password:123456}")
    val ftpPassword = "123456"

    /**
     * fFTP连接默认ControlEncoding(根据FTP服务器操作系统选择，Linux一般为UTF-8，Windows一般为GBK)，
     * 可在ftp url后面加参数ftp.control.encoding=UTF-8指定，不指定默认用配置的
     */
    @Value("\${preview.ftp.controlEncoding:UTF-8}")
    val ftpControlEncoding = "UTF-8"

    // 水印
    /**
     * 水印内容
     */
    @Value("\${preview.watermark.txt:}")
    val watermarkTxt = ""
    /**
     * 水印x轴间隔
     */
    @Value("\${preview.watermark.x.space:10}")
    val watermarkXSpace = "10"
    /**
     * 水印y轴间隔
     */
    @Value("\${preview.watermark.y.space:10}")
    val watermarkYSpace = "10"
    /**
     * 水印字体
     */
    @Value("\${preview.watermark.font:微软雅黑}")
    val watermarkFont = "微软雅黑"
    /**
     * 水印字体大小
     */
    @Value("\${preview.watermark.fontsize:18px}")
    val watermarkFontsize = "18px"
    /**
     * 水印颜色
     */
    @Value("\${preview.watermark.color:black}")
    val watermarkColor = "black"
    /**
     * 水印透明度
     */
    @Value("\${preview.watermark.alpha:0.2}")
    val watermarkAlpha = "0.2"
    /**
     * 水印宽度
     */
    @Value("\${preview.watermark.width:180}")
    val watermarkWidth = "180"
    /**
     * 水印高度
     */
    @Value("\${preview.watermark.height:80}")
    val watermarkHeight = "80"
    /**
     * 水印倾斜度数
     */
    @Value("\${preview.watermark.angle:10}")
    val watermarkAngle = "10"

}