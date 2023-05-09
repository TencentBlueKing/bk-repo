<template>
    <div class="start-scan-container">
        <bk-form style="max-width: 1080px;" :label-width="120" :model="config" :rules="rules" ref="scanForm">
            <bk-form-item :label="$t('schemeName')">
                <span>{{ config.name }}</span>
            </bk-form-item>
            <bk-form-item :label="$t('schemeType')">
                <span>{{ $t(`scanTypeEnum.${config.type}`)}}</span>
            </bk-form-item>
            <bk-form-item :label="$t('repoScope')" :required="true" property="repo" error-display-type="normal">
                <repo-table
                    ref="repoConfig"
                    :init-data="config.repoNameList"
                    :scan-type="config.type"
                    @clearError="clearError">
                </repo-table>
            </bk-form-item>
            <bk-form-item :label="$t('artifactScope')" :required="true" property="arti" error-display-type="normal">
                <arti-table
                    ref="artiConfig"
                    :init-data="config.artifactRules"
                    :scan-type="config.type"
                    @clearError="clearError">
                </arti-table>
            </bk-form-item>
            <bk-form-item>
                <bk-button style="width: 140px;" theme="primary" @click="save()">{{ $t('scanImmediately') }}</bk-button>
            </bk-form-item>
        </bk-form>
    </div>
</template>
<script>
    import repoTable from './scanConfig/repoTable'
    import artiTable from './scanConfig/artiTable'
    import { scanTypeEnum } from '@repository/store/publicEnum'
    import { mapActions } from 'vuex'
    export default {
        name: 'startScan',
        components: { repoTable, artiTable },
        data () {
            return {
                scanTypeEnum,
                config: {
                    name: '',
                    type: '',
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
        created () {
            this.getScanConfig({
                projectId: this.projectId,
                id: this.planId
            }).then(res => {
                this.config = {
                    ...res,
                    repoNameList: [],
                    artifactRules: []
                }
            })
        },
        methods: {
            ...mapActions([
                'getScanConfig',
                'startScan'
            ]),
            clearError () {
                this.$refs.scanForm.clearError()
            },
            async save () {
                await this.$refs.scanForm.validate()
                const repoNameList = await this.$refs.repoConfig.getConfig()
                const artifactRules = await this.$refs.artiConfig.getConfig()
                this.startScan({
                    id: this.planId,
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
                }).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('executeScanSuccessMsg')
                    })
                    this.$router.push({
                        name: 'scanReport',
                        query: this.$route.query
                    })
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.start-scan-container {
    height: 100%;
    padding: 40px 0;
    overflow: auto;
    background-color: white;
}
</style>
