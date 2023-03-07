<template>
    <bk-form style="max-width: 1080px;" :label-width="120" :model="config" :rules="rules" ref="scanForm">
        <bk-form-item :label="$t('autoScan')">
            <bk-switcher v-model="config.scanOnNewArtifact" size="small" theme="primary"></bk-switcher>
            <div style="color:var(--fontSubsidiaryColor);">{{ $t('autoScanTip') }}</div>
        </bk-form-item>
        <bk-form-item :label="$t('repoScope')" :required="true" property="repo" error-display-type="normal">
            <repo-table
                ref="repoConfig"
                :init-data="config.repoNameList"
                :scan-type="config.type"
                :disabled="!config.scanOnNewArtifact"
                @clearError="clearError">
            </repo-table>
        </bk-form-item>
        <bk-form-item :label="$t('artifactScope')" :required="true" property="arti" error-display-type="normal">
            <arti-table
                ref="artiConfig"
                :init-data="config.artifactRules"
                :scan-type="config.type"
                :disabled="!config.scanOnNewArtifact"
                @clearError="clearError">
            </arti-table>
        </bk-form-item>
        <bk-form-item>
            <bk-button theme="primary" @click="save()">{{$t('save')}}</bk-button>
        </bk-form-item>
    </bk-form>
</template>
<script>
    import repoTable from './repoTable'
    import artiTable from './artiTable'
    export default {
        name: 'autoScanConfig',
        components: { repoTable, artiTable },
        props: {
            data: {
                type: Object,
                default: () => ({
                    type: '',
                    scanOnNewArtifact: false,
                    rule: {}
                })
            }
        },
        data () {
            return {
                config: {
                    type: '',
                    scanOnNewArtifact: false,
                    repoNameList: [],
                    artifactRules: []
                },
                rules: {
                    repo: [
                        {
                            validator: () => {
                                return this.$refs.repoConfig.getConfig()
                            },
                            message: this.$t('autoScanRepoRule'),
                            trigger: 'blur'
                        }
                    ],
                    arti: [
                        {
                            validator: () => {
                                return this.$refs.artiConfig.getConfig()
                            },
                            message: this.$t('autoScanArtifactRule'),
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
            planId () {
                return this.$route.params.planId
            }
        },
        watch: {
            data: {
                handler (val) {
                    const copyData = JSON.parse(JSON.stringify(val))
                    this.config.type = copyData.type
                    this.config.scanOnNewArtifact = copyData.scanOnNewArtifact
                    this.config.repoNameList = copyData.rule.rules.find(i => i.field === 'repoName')?.value || []
                    this.config.artifactRules = copyData.rule.rules.find(i => Boolean(i.rules))?.rules || []
                },
                deep: true,
                immediate: true
            }
        },
        methods: {
            clearError () {
                this.$refs.scanForm.clearError()
            },
            async save () {
                if (this.config.scanOnNewArtifact) {
                    await this.$refs.scanForm.validate()
                    const repoNameList = await this.$refs.repoConfig.getConfig()
                    const artifactRules = await this.$refs.artiConfig.getConfig()
                    this.$emit('save', {
                        scanOnNewArtifact: this.config.scanOnNewArtifact,
                        rule: {
                            rules: [
                                {
                                    field: 'projectId',
                                    value: this.projectId,
                                    operation: 'EQ'
                                },
                                repoNameList.length
                                    ? {
                                        field: 'repoName',
                                        value: repoNameList,
                                        operation: 'IN'
                                    }
                                    : undefined,
                                artifactRules.length
                                    ? {
                                        rules: artifactRules,
                                        relation: 'OR'
                                    }
                                    : undefined
                            ].filter(Boolean),
                            relation: 'AND'
                        }
                    })
                } else {
                    this.$emit('save', { scanOnNewArtifact: this.config.scanOnNewArtifact })
                }
            }
        }
    }
</script>
