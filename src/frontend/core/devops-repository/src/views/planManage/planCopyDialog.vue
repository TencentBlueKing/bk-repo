<template>
    <canway-dialog
        :value="show"
        width="600"
        height-num="350"
        :title="$t('copyPlan')"
        @cancel="$emit('cancel')">
        <bk-form :label-width="100" :model="formData" :rules="rules" ref="planCopyForm">
            <bk-form-item :label="$t('planName')" :required="true" property="name" error-display-type="normal">
                <bk-input v-model.trim="formData.name" maxlength="32" show-word-limit></bk-input>
            </bk-form-item>
            <bk-form-item :label="$t('description')" property="description">
                <bk-input
                    v-model="formData.description"
                    type="textarea"
                    maxlength="200"
                    show-word-limit
                    :rows="6">
                </bk-input>
            </bk-form-item>
        </bk-form>
        <template #footer>
            <bk-button @click="$emit('cancel')">{{$t('cancel')}}</bk-button>
            <bk-button class="ml10" theme="primary" :loading="loading" @click="confirm">{{$t('confirm')}}</bk-button>
        </template>
    </canway-dialog>
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
                            message: this.$t('pleaseInput') + this.$t('space') + this.$t('planName'),
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
                        message: this.$t('copyPlan') + this.$t('space') + this.$t('success')
                    })
                    this.$emit('refresh')
                    this.$emit('cancel')
                }).finally(() => {
                    this.loading = false
                })
            }
        }
    }
</script>
