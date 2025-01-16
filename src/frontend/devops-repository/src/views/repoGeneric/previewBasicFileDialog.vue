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
            <div class="preview-file-tips">{{ $t('previewFileTips') }}</div>
            <textarea v-model="basicFileText" class="textarea" readonly></textarea>
        </div>
    </bk-dialog>
</template>

<script>
    import { getPreviewLocalOfficeFileInfo, getPreviewRemoteOfficeFileInfo } from '@/utils/previewOfficeFile'

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
                    filePath: ''
                },
                dialogWidth: window.innerWidth - 600
            }
        },
        computed: {
            projectId () {
                return this.$route.params.projectId
            }
        },
        created () {
            if (this.repoType === 'local') {
                getPreviewLocalOfficeFileInfo(this.projectId, this.repoName, '/' + this.filePath).then(res => {
                    if (res.data.data.watermark && res.data.data.watermark.watermarkTxt && res.data.data.watermark.watermarkTxt != null) {
                        this.initWaterMark(res.data.data.watermark)
                    }
                })
            } else {
                getPreviewRemoteOfficeFileInfo(this.extraParam).then(res => {
                    if (res.data.data.watermark && res.data.data.watermark.watermarkTxt && res.data.data.watermark.watermarkTxt != null) {
                        this.initWaterMark(res.data.data.watermark)
                    }
                })
            }
        },
        methods: {
            setData (data) {
                this.basicFileText = data
                this.previewDialog.isLoading = false
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
            }
        }
    }
</script>

<style lang="scss" scoped>
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
