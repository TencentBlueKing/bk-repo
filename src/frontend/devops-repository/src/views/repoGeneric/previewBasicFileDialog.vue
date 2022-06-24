<template>
    <bk-dialog
        class="previewBasic-file-dialog"
        v-model="previewDialog.show"
        :width="dialogWidth"
        :show-footer="false"
        :title="($t('preview') + ' - ' + previewDialog.title)">
        <textarea v-model="previewDialog.basicFileText" class="textarea" readonly></textarea>
    </bk-dialog>
</template>

<script>
    export default {
        name: 'previewBasicFileDialog',
        data () {
            return {
                previewDialog: {
                    show: false,
                    title: '',
                    basicFileText: ''
                },
                dialogWidth: window.innerWidth - 600
            }
        },
        methods: {
            setData (data) {
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
        height: 400px;
        border: none;
    }
</style>
