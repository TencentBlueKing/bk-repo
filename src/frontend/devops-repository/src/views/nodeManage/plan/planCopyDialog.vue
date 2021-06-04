<template>
    <bk-dialog
        :value="show"
        width="500"
        title="复制计划"
        :mask-close="false"
        :close-icon="false">
        <bk-form :label-width="85" :model="formData" :rules="rules" ref="planCopyForm">
            <bk-form-item label="计划名称" :required="true" property="name">
                <bk-input v-model.trim="formData.name"></bk-input>
            </bk-form-item>
            <bk-form-item :label="$t('description')" property="description">
                <bk-input
                    v-model="formData.description"
                    type="textarea"
                    :rows="3">
                </bk-input>
            </bk-form-item>
        </bk-form>
        <template #footer>
            <bk-button theme="primary" :loading="loading" @click="confirm">{{$t('confirm')}}</bk-button>
            <bk-button @click="$emit('cancel')">{{$t('cancel')}}</bk-button>
        </template>
    </bk-dialog>
</template>
<script>
    import { mapActions } from 'vuex'
    export default {
        name: 'planCopyDialog',
        props: {
            show: Boolean,
            name: String,
            planKey: String,
            description: String
        },
        data () {
            return {
                loading: false,
                formData: {
                    name: '',
                    description: ''
                },
                rules: {
                    name: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + '计划名称',
                            trigger: 'blur'
                        }
                    ]
                }
            }
        },
        watch: {
            show (val) {
                this.$refs.planCopyForm.clearError()
                if (!val) return
                this.formData.name = this.name + '_copy'
                this.formData.description = this.description
            }
        },
        methods: {
            ...mapActions(['copyPlan']),
            async confirm () {
                await this.$refs.planCopyForm.validate()
                this.loading = true
                this.copyPlan({
                    body: {
                        name: this.formData.name,
                        key: this.planKey,
                        description: this.formData.description
                    }
                }).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: '复制计划' + this.$t('success')
                    })
                    this.$emit('cancel')
                }).finally(() => {
                    this.loading = false
                })
            }
        }
    }
</script>
