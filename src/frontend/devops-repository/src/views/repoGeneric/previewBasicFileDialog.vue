<template>
    <bk-dialog
        class="previewBasic-file-dialog"
        v-model="previewDialog.show"
        :width="dialogWidth"
        :show-footer="false"
        :title="($t('preview') + ' - ' + previewDialog.title)">
        <div v-if="previewDialog.isLoading" style="windt: 100%;" v-bkloading="{ isLoading: previewDialog.isLoading }"></div>
        <div v-else>
            <div class="preview-file-tips">{{ $t('previewFileTips') }}</div>
            <textarea v-model="basicFileText" class="textarea" readonly></textarea>
        </div>
    </bk-dialog>
</template>

<script>
    export default {
        name: 'previewBasicFileDialog',
        data () {
            return {
                basicFileText: '',
                previewDialog: {
                    title: '',
                    show: false,
                    isLoading: true
                },
                dialogWidth: window.innerWidth - 600
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
