<template>
    <canway-dialog
        v-model="show"
        :title="title"
        width="520"
        :height-num="311"
        @confirm="submit"
        @cancel="cancel">
        <bk-alert type="info" :title="forbidMsg" style="margin-bottom: 10px; margin-top: -20px" closable></bk-alert>
        <bk-form :rules="rules" ref="genericForm">
            <bk-form-item :label="$t('reason')">
                <textarea v-model="reason" class="textarea"></textarea>
            </bk-form-item>
        </bk-form>
    </canway-dialog>
</template>

<script>
    import { mapActions } from 'vuex'
    export default {
        name: 'genericForbidDialog',
        data () {
            return {
                show: false,
                loading: false,
                repoName: '',
                projectId: '',
                fullPath: '',
                reason: '',
                forbidMsg: this.$t('forbidMsg'),
                title: '',
                rules: {}
            }
        },
        methods: {
            ...mapActions([
                'forbidMetadata'
            ]),
            cancel () {
                this.$emit('refresh')
                this.reason = ''
                this.show = false
            },
            submit () {
                let param = null
                if (this.reason.trim() === '') {
                    param = [
                        { key: 'forbidStatus', value: true }
                    ]
                } else {
                    param = [
                        { key: 'forbidStatus', value: true },
                        { key: 'forbidReason', value: this.reason }
                    ]
                }
                this.forbidMetadata({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    fullPath: this.fullPath,
                    body: {
                        nodeMetadata: param
                    }
                }).then(() => {
                    this.cancel()
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('forbiddenUse') + this.$t('space') + this.$t('success')
                    })
                })
            }
        }
    }
</script>

<style lang="scss" scoped>
::v-deep .bk-form .bk-form-item .bk-label {
    padding-right: 20px;
    width: 50px !important;
}
::v-deep .bk-form .bk-form-content {
    width: auto;
    min-height: 32px;
    margin-left: 50px !important;
    position: relative;
    outline: none;
    line-height: 30px;
}
.textarea {
    resize: none;
    width: 100%;
    height: 300px;
    border: 1px solid #ccc;
    padding: 0 5px;
}
</style>
