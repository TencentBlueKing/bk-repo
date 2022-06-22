<template>
    <bk-dialog v-model="previewDialog.show"
        :width="dialogWidth"
        :show-footer="false"
        :title="($t('preview') + ' - ' + previewDialog.title)">
        <p v-text="previewDialog.basicFileText"></p>
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
