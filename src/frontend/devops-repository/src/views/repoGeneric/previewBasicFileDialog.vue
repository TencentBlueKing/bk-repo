<template>
    <bk-dialog
        class="previewBasic-file-dialog"
        v-model="previewDialog.show"
        :width="dialogWidth"
        :show-footer="false"
        :title="($t('preview') + ' - ' + previewDialog.title)">
        <div v-if="previewDialog.isLoading" style="windt: 100%;" v-bkloading="{ isLoading: previewDialog.isLoading }"></div>
        <textarea v-else v-model="basicFileText" class="textarea" readonly></textarea>
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

<style lang="scss">
    .preview-basic-file-dialog {
        height: 600px;
    }
    .textarea {
        resize: none;
        width: 100%;
        height: 500px;
        border: none;
    }
</style>
