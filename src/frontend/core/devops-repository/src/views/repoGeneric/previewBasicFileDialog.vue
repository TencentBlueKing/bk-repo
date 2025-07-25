<template>
    <bk-dialog
        class="previewBasic-file-dialog"
        v-model="previewDialog.show"
        :width="dialogWidth"
        :show-footer="false"
        @cancel="cancel"
        :title="($t('preview') + ' - ' + previewDialog.title)">
        <div v-if="previewDialog.isLoading" style="windt: 100%;" v-bkloading="{ isLoading: previewDialog.isLoading }"></div>
        <div v-else>
            <div v-if="imgShow" style="width: 100%; height: 100%">
                <img id="image" :src="imgUrl" alt="Picture" style="max-width: 100%; max-height: 100%;">
            </div>
            <div class="preview-file-tips" v-if="!imgShow">{{ $t('previewFileTips') }}</div>
            <textarea v-if="!imgShow" v-model="basicFileText" class="textarea" readonly></textarea>
        </div>
    </bk-dialog>
</template>

<script>
    import {
        customizePreviewLocalOfficeFile, customizePreviewRemoteOfficeFile,
        getPreviewLocalOfficeFileInfo,
        getPreviewRemoteOfficeFileInfo
    } from '@repository/utils/previewOfficeFile'
    import Viewer from "viewerjs";

    export default {
        name: 'PreviewBasicFileDialog',
        data () {
            return {
                basicFileText: '',
                previewDialog: {
                    title: '',
                    show: false,
                    isLoading: true,
                    repoType: '',
                    extraParam: '',
                    repoName: '',
                    filePath: '',
                    imgShow: false,
                    imgUrl: ''
                },
                dialogWidth: window.innerWidth - 600
            }
        },
        computed: {
            projectId () {
                return this.$route.params.projectId
            },
            enableMultipleTypeFilePreview () {
                return RELEASE_MODE === 'community' || RELEASE_MODE === 'tencent'
            }
        },
        methods: {
            setData (data) {
                this.basicFileText = data
                this.previewDialog.isLoading = false
                if (!this.enableMultipleTypeFilePreview) return
                if (this.previewDialog.repoType === 'local') {
                    getPreviewLocalOfficeFileInfo(this.projectId, this.previewDialog.repoName, '/' + this.previewDialog.filePath).then(res => {
                        if (res.data.data.watermark && res.data.data.watermark.watermarkTxt && res.data.data.watermark.watermarkTxt != null) {
                            this.initWaterMark(res.data.data.watermark)
                        }
                    })
                } else {
                    getPreviewRemoteOfficeFileInfo(this.previewDialog.extraParam).then(res => {
                        if (res.data.data.watermark && res.data.data.watermark.watermarkTxt && res.data.data.watermark.watermarkTxt != null) {
                            this.initWaterMark(res.data.data.watermark)
                        }
                    })
                }
            },
            setPic () {
                this.imgShow = true
                if (this.previewDialog.repoType === 'local') {
                    getPreviewLocalOfficeFileInfo(this.projectId, this.previewDialog.repoName, '/' + this.previewDialog.filePath).then(res => {
                        if (res.data.data.watermark && res.data.data.watermark.watermarkTxt && res.data.data.watermark.watermarkTxt != null) {
                            this.initWaterMark(res.data.data.watermark)
                        }
                    })
                    customizePreviewLocalOfficeFile(this.projectId, this.previewDialog.repoName, '/' + this.previewDialog.filePath).then(res => {
                        this.dealDate(res)
                    }).catch(() => {
                        this.loading = false
                        this.hasError = true
                    })
                } else {
                    getPreviewRemoteOfficeFileInfo(this.previewDialog.extraParam).then(res => {
                        if (res.data.data.watermark && res.data.data.watermark.watermarkTxt && res.data.data.watermark.watermarkTxt != null) {
                            this.initWaterMark(res.data.data.watermark)
                        }
                    })
                    customizePreviewRemoteOfficeFile(this.previewDialog.extraParam).then(res => {
                        this.dealDate(res)
                    }).catch(() => {
                        this.loading = false
                        this.hasError = true
                    })
                }
            },
            setDialogData (data) {
                this.previewDialog = {
                    ...data
                }
            },
            initWaterMark (param) {
                window.initWaterMark(param)
            },
            cancel () {
                window.resetWaterMark()
            },
            dealDate (res) {
                this.previewDialog.isLoading = false
                this.imgUrl = URL.createObjectURL(res.data)
                this.$nextTick(() => {
                    const viewer = new Viewer(document.getElementById('image'), {
                        inline: true,
                        viewed () {
                            viewer.zoomTo(1)
                        }
                    })
                })
            }
        }
    }
</script>

<style lang="scss" scoped>
@import 'viewerjs/dist/viewer.css';
    .preview-file-tips {
        margin-bottom: 10px;
        color: #707070;
    }
    .textarea {
        resize: none;
        width: 100%;
        height: 500px;
        border: 1px solid #ccc;
        padding: 0 5px;
    }
</style>
