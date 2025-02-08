<template>
    <canway-dialog
        v-model="scanForm.show"
        :title="$t('createScanPlan')"
        width="500"
        :height-num="365"
        @cancel="cancel">
        <bk-form class="mr10" :label-width="100" :model="scanForm" :rules="rules" ref="scanForm">
            <bk-form-item :label="$t('schemeName')" :required="true" property="name" error-display-type="normal">
                <bk-input v-model.trim="scanForm.name" maxlength="32" show-word-limit></bk-input>
            </bk-form-item>
            <bk-form-item :label="$t('schemeType')" :required="true" property="type" error-display-type="normal">
                <bk-select
                    v-model="scanForm.type"
                    @change="scanForm.scanner = ''">
                    <bk-option v-for="[id] in Object.entries(scanTypeEnum)" :key="id" :id="id" :name="$t(`scanTypeEnum.${id}`)"></bk-option>
                </bk-select>
            </bk-form-item>
            <bk-form-item v-if="scanForm.type" :label="$t('scanner')" :required="true" property="scanner" error-display-type="normal">
                <bk-select
                    v-model="scanForm.scanner">
                    <bk-option v-for="scanner in filterScannerList" :key="scanner.name" :id="scanner.name" :name="scanner.name"></bk-option>
                </bk-select>
                <div v-if="scannerTip" class="form-tip">{{ scannerTip }}</div>
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
                    scanner: '',
                    name: '',
                    description: ''
                },
                filterScannerList: [],
                rules: {
                    name: [
                        {
                            required: true,
                            message: this.$t('planNameTip'),
                            trigger: 'blur'
                        }
                    ],
                    type: [
                        {
                            required: true,
                            message: this.$t('planTypeTip'),
                            trigger: 'blur'
                        }
                    ],
                    scanner: [
                        {
                            required: true,
                            message: this.$t('selectScannerPlaceHolder'),
                            trigger: 'blur'
                        }
                    ]
                }
            }
        },
        computed: {
            projectId () {
                return this.$route.params.projectId
            },
            scannerTip () {
                const scanner = this.filterScannerList.find(s => s.name === this.scanForm.scanner)
                return scanner ? scanner.description : ''
            }
        },
        watch: {
            'scanForm.type': function (newVal) {
                return this
                    .getScannerList({ packageType: this.scanForm.type })
                    .then(res => {
                        this.filterScannerList = res
                    })
            }
        },
        methods: {
            ...mapActions(['getScannerList', 'createScan']),
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
                const { scanner, type, name, description } = this.scanForm
                this.createScan({
                    projectId: this.projectId,
                    type,
                    name,
                    scanTypes: this.filterScannerList.find(s => s.name === this.scanForm.scanner).supportScanTypes,
                    scanner,
                    description
                }).then(() => {
                    this.$emit('refresh')
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('createScanPlan') + this.$t('space') + this.$t('success')
                    })
                    this.scanForm.show = false
                }).finally(() => {
                    this.scanForm.loading = false
                })
            }
        }
    }
</script>
