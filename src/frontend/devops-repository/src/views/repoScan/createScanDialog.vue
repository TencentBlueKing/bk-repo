<template>
    <canway-dialog
        v-model="scanForm.show"
        title="创建扫描方案"
        width="500"
        :height-num="365"
        @cancel="cancel">
        <bk-form class="mr10" :label-width="80" :model="scanForm" :rules="rules" ref="scanForm">
            <bk-form-item label="方案名称" :required="true" property="name" error-display-type="normal">
                <bk-input v-model.trim="scanForm.name" maxlength="32" show-word-limit></bk-input>
            </bk-form-item>
            <bk-form-item label="方案类型" :required="true" property="type" error-display-type="normal">
                <bk-select
                    v-model="scanForm.type">
                    <bk-option v-for="[id, name] in Object.entries(scanTypeEnum)" :key="id" :id="id" :name="name"></bk-option>
                </bk-select>
            </bk-form-item>
            <bk-form-item :label="$t('description')" property="description">
                <bk-input
                    v-model="scanForm.description"
                    type="textarea"
                    maxlength="200"
                    show-word-limit
                    :rows="4">
                </bk-input>
            </bk-form-item>
        </bk-form>
        <template #footer>
            <bk-button theme="default" @click="cancel">{{$t('cancel')}}</bk-button>
            <bk-button class="ml10" :loading="scanForm.loading" theme="primary" @click="submit">{{$t('confirm')}}</bk-button>
        </template>
    </canway-dialog>
</template>
<script>
    import { mapActions } from 'vuex'
    import { scanTypeEnum } from '@repository/store/publicEnum'
    export default {
        name: 'createScan',
        data () {
            return {
                scanTypeEnum,
                scanForm: {
                    show: false,
                    loading: false,
                    type: '',
                    name: '',
                    description: ''
                },
                rules: {
                    name: [
                        {
                            required: true,
                            message: '请输入方案名称',
                            trigger: 'blur'
                        }
                    ],
                    type: [
                        {
                            required: true,
                            message: '请选择方案类型',
                            trigger: 'blur'
                        }
                    ]
                }
            }
        },
        computed: {
            projectId () {
                return this.$route.params.projectId
            }
        },
        methods: {
            ...mapActions(['createScan']),
            setData (data) {
                this.scanForm = {
                    ...this.scanForm,
                    ...data
                }
            },
            cancel () {
                this.$refs.scanForm.clearError()
                this.scanForm.show = false
            },
            submit () {
                this.$refs.scanForm.validate().then(() => {
                    this.submitScanForm()
                })
            },
            submitScanForm () {
                this.scanForm.loading = true
                const { type, name, description } = this.scanForm
                this.createScan({
                    projectId: this.projectId,
                    type,
                    name,
                    description
                }).then(() => {
                    this.$emit('refresh')
                    this.$bkMessage({
                        theme: 'success',
                        message: '创建扫描方案' + this.$t('success')
                    })
                    this.scanForm.show = false
                }).finally(() => {
                    this.scanForm.loading = false
                })
            }
        }
    }
</script>
