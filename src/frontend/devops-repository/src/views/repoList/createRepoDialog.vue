<template>
    <canway-dialog
        v-model="show"
        width="800"
        :height-num="dialogHeightNum"
        title="创建仓库"
        @cancel="cancel">
        <bk-form class="mr10 repo-base-info" :label-width="130" :model="repoBaseInfo" :rules="rules" ref="repoBaseInfo">
            <bk-form-item :label="$t('repoType')" :required="true" property="type" error-display-type="normal">
                <bk-radio-group v-model="repoBaseInfo.type" class="repo-type-radio-group" @change="changeRepoType">
                    <bk-radio-button v-for="repo in repoEnum" :key="repo" :value="repo">
                        <div class="flex-column flex-center repo-type-radio">
                            <Icon size="32" :name="repo" />
                            <span>{{repo}}</span>
                        </div>
                    </bk-radio-button>
                </bk-radio-group>
            </bk-form-item>
            <bk-form-item :label="$t('repoName')" :required="true" property="name" error-display-type="normal">
                <div class="flex-align-center">
                    <bk-input style="width:400px" v-model.trim="repoBaseInfo.name" maxlength="32" show-word-limit
                        :placeholder="$t(repoBaseInfo.type === 'docker' ? 'repoDockerNamePlacehodler' : 'repoNamePlacehodler')">
                    </bk-input>
                    <span v-if="repoBaseInfo.type === 'docker'" class="ml10 form-tip">docker仓库仅能使用英文小写</span>
                </div>
            </bk-form-item>
            <bk-form-item label="访问权限">
                <card-radio-group
                    v-model="available"
                    :list="availableList">
                </card-radio-group>
            </bk-form-item>
            <template v-if="repoBaseInfo.type === 'generic'">
                <bk-form-item :label="$t('mobileDownload')" property="mobileDownload">
                    <bk-radio-group v-model="repoBaseInfo.mobileDownload">
                        <bk-radio class="mr20" :value="true">{{ $t('open') }}</bk-radio>
                        <bk-radio :value="false">{{ $t('close') }}</bk-radio>
                    </bk-radio-group>
                    <template v-if="repoBaseInfo.mobileDownload">
                        <bk-form-item :label="$t('fileName')" :label-width="60" class="mt10"
                            property="interceptorsRulesMap.mobile.filename" error-display-type="normal">
                            <bk-input class="w250" v-model.trim="repoBaseInfo.interceptorsRulesMap.mobile.filename"></bk-input>
                        </bk-form-item>
                        <bk-form-item :label="$t('metadata')" :label-width="60"
                            property="interceptorsRulesMap.mobile.metadata" error-display-type="normal">
                            <bk-input class="w250" v-model.trim="repoBaseInfo.interceptorsRulesMap.mobile.metadata"></bk-input>
                        </bk-form-item>
                    </template>
                </bk-form-item>
                <bk-form-item :label="$t('webDownload')" property="webDownload">
                    <bk-radio-group v-model="repoBaseInfo.webDownload">
                        <bk-radio class="mr20" :value="true">{{ $t('open') }}</bk-radio>
                        <bk-radio :value="false">{{ $t('close') }}</bk-radio>
                    </bk-radio-group>
                    <template v-if="repoBaseInfo.webDownload">
                        <bk-form-item :label="$t('fileName')" :label-width="60" class="mt10"
                            property="interceptorsRulesMap.web.filename" error-display-type="normal">
                            <bk-input class="w250" v-model.trim="repoBaseInfo.interceptorsRulesMap.web.filename"></bk-input>
                        </bk-form-item>
                        <bk-form-item :label="$t('metadata')" :label-width="60"
                            property="interceptorsRulesMap.web.metadata" error-display-type="normal">
                            <bk-input class="w250" v-model.trim="repoBaseInfo.interceptorsRulesMap.web.metadata"></bk-input>
                        </bk-form-item>
                    </template>
                </bk-form-item>
            </template>
            <template v-if="repoBaseInfo.type === 'rpm'">
                <bk-form-item :label="$t('enabledFileLists')">
                    <bk-checkbox v-model="repoBaseInfo.enabledFileLists"></bk-checkbox>
                </bk-form-item>
                <bk-form-item :label="$t('repodataDepth')" property="repodataDepth" error-display-type="normal">
                    <bk-input class="w480" v-model.trim="repoBaseInfo.repodataDepth"></bk-input>
                </bk-form-item>
                <bk-form-item :label="$t('groupXmlSet')" property="groupXmlSet" error-display-type="normal">
                    <bk-tag-input
                        class="w480"
                        :value="repoBaseInfo.groupXmlSet"
                        @change="(val) => {
                            repoBaseInfo.groupXmlSet = val.map(v => {
                                return v.replace(/^([^.]*)(\.xml)?$/, '$1.xml')
                            })
                        }"
                        :list="[]"
                        trigger="focus"
                        :clearable="false"
                        allow-create
                        has-delete-icon>
                    </bk-tag-input>
                </bk-form-item>
            </template>
            <bk-form-item :label="$t('description')">
                <bk-input type="textarea"
                    class="w480"
                    maxlength="200"
                    :rows="6"
                    v-model.trim="repoBaseInfo.description"
                    :placeholder="$t('repoDescriptionPlacehodler')">
                </bk-input>
            </bk-form-item>
        </bk-form>
        <template #footer>
            <bk-button @click="cancel">{{$t('cancel')}}</bk-button>
            <bk-button class="ml10" :loading="loading" theme="primary" @click="confirm">{{$t('confirm')}}</bk-button>
        </template>
    </canway-dialog>
</template>
<script>
    import CardRadioGroup from '@repository/components/CardRadioGroup'
    import { repoEnum } from '@repository/store/publicEnum'
    import { mapActions } from 'vuex'

    const getRepoBaseInfo = () => {
        return {
            type: 'generic',
            name: '',
            public: false,
            system: false,
            enabledFileLists: false,
            repodataDepth: 0,
            interceptors: [],
            groupXmlSet: [],
            description: '',
            mobileDownload: true,
            webDownload: true,
            interceptorsRulesMap: {
                mobile: {
                    filename: '',
                    metadata: ''
                },
                web: {
                    filename: '',
                    metadata: ''
                }
            }
        }
    }

    export default {
        name: 'createRepo',
        components: { CardRadioGroup },
        data () {
            return {
                repoEnum,
                show: false,
                loading: false,
                repoBaseInfo: getRepoBaseInfo()
                
            }
        },
        computed: {
            dialogHeightNum () {
                let num = 603
                switch (this.repoBaseInfo.type) {
                    case 'rpm':
                        num = 759
                        break
                    case 'generic':
                        num = 880
                        break
                }
                return num
            },
            projectId () {
                return this.$route.params.projectId
            },
            rules () {
                return {
                    type: [
                        {
                            required: true,
                            message: this.$t('pleaseSelect') + this.$t('repoType'),
                            trigger: 'blur'
                        }
                    ],
                    name: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('repoName'),
                            trigger: 'blur'
                        },
                        {
                            regex: this.repoBaseInfo.type === 'docker' ? /^[a-z][a-z0-9\-_]{1,31}$/ : /^[a-zA-Z][a-zA-Z0-9\-_]{1,31}$/,
                            message: this.$t(this.repoBaseInfo.type === 'docker' ? 'repoDockerNamePlacehodler' : 'repoNamePlacehodler'),
                            trigger: 'blur'
                        },
                        {
                            validator: this.asynCheckRepoName,
                            message: this.$t('repoName') + '已存在',
                            trigger: 'blur'
                        }
                    ],
                    repodataDepth: [
                        {
                            regex: /^(0|[1-9][0-9]*)$/,
                            message: this.$t('pleaseInput') + this.$t('legit') + this.$t('repodataDepth'),
                            trigger: 'blur'
                        }
                    ],
                    groupXmlSet: [
                        {
                            validator: arr => {
                                return arr.every(v => {
                                    return /\.xml$/.test(v)
                                })
                            },
                            message: this.$t('pleaseInput') + this.$t('legit') + this.$t('groupXmlSet') + `(.xml${this.$t('type')})`,
                            trigger: 'change'
                        }
                    ],
                    'interceptorsRulesMap.mobile.filename': [
                        {
                            required: true,
                            message: this.$t('pleaseFileName'),
                            trigger: 'blur'
                        }
                    ],
                    'interceptorsRulesMap.mobile.metadata': [
                        {
                            required: true,
                            message: this.$t('pleaseMetadata'),
                            trigger: 'blur'
                        },
                        {
                            regex: /^[^\s]+:[^\s]+/,
                            message: this.$t('metadataRule'),
                            trigger: 'blur'
                        }
                    ],
                    'interceptorsRulesMap.web.filename': [
                        {
                            required: true,
                            message: this.$t('pleaseFileName'),
                            trigger: 'blur'
                        }
                    ],
                    'interceptorsRulesMap.web.metadata': [
                        {
                            required: true,
                            message: this.$t('pleaseMetadata'),
                            trigger: 'blur'
                        },
                        {
                            regex: /^[^\s]+:[^\s]+/,
                            message: this.$t('metadataRule'),
                            trigger: 'blur'
                        }
                    ]
                }
            },
            available: {
                get () {
                    if (this.repoBaseInfo.public) return 'public'
                    if (this.repoBaseInfo.system) return 'system'
                    return 'project'
                },
                set (val) {
                    this.repoBaseInfo.public = val === 'public'
                    this.repoBaseInfo.system = val === 'system'
                }
            },
            availableList () {
                return [
                    { label: '项目内公开', value: 'project', tip: '项目内成员可以使用' },
                    // { label: '系统内公开', value: 'system', tip: '系统内成员可以使用' },
                    { label: '可匿名下载', value: 'public', tip: '不鉴权，任意终端都可下载' }
                ]
            }
        },
        methods: {
            ...mapActions(['createRepo', 'checkRepoName']),
            showDialogHandler () {
                this.show = true
                this.repoBaseInfo = getRepoBaseInfo()
                this.$refs.repoBaseInfo && this.$refs.repoBaseInfo.clearError()
            },
            cancel () {
                this.show = false
            },
            asynCheckRepoName () {
                return this.checkRepoName({
                    projectId: this.projectId,
                    name: this.repoBaseInfo.name
                }).then(res => !res)
            },
            changeRepoType () {
                if (this.repoBaseInfo.type === 'docker') this.repoBaseInfo.name = ''
                this.$refs.repoBaseInfo.clearError()
            },
            async confirm () {
                await this.$refs.repoBaseInfo.validate()
                const { mobileDownload, webDownload, interceptorsRulesMap, type } = this.repoBaseInfo
                let { interceptors } = this.repoBaseInfo
                interceptors = []
                if (type === 'generic') {
                    for (const [key, val] of Object.entries(interceptorsRulesMap)) {
                        if ((key === 'mobile' && mobileDownload) || (key === 'web' && webDownload)) {
                            interceptors.push({
                                type: key.toLocaleUpperCase(),
                                rules: val
                            })
                        }
                    }
                }
                this.loading = true
                this.createRepo({
                    body: {
                        projectId: this.projectId,
                        type: this.repoBaseInfo.type.toUpperCase(),
                        name: this.repoBaseInfo.name,
                        public: this.repoBaseInfo.public,
                        description: this.repoBaseInfo.description,
                        category: this.repoBaseInfo.type === 'generic' ? 'LOCAL' : 'COMPOSITE',
                        configuration: {
                            type: 'composite',
                            settings: {
                                system: this.repoBaseInfo.system,
                                interceptors: this.repoBaseInfo.type === 'generic' && interceptors.length ? interceptors : undefined,
                                ...(
                                    this.repoBaseInfo.type === 'rpm'
                                        ? {
                                            enabledFileLists: this.repoBaseInfo.enabledFileLists,
                                            repodataDepth: this.repoBaseInfo.repodataDepth,
                                            groupXmlSet: this.repoBaseInfo.groupXmlSet
                                        }
                                        : {}
                                )
                            }
                        }
                    }
                }).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('create') + this.$t('repository') + this.$t('success')
                    })
                    this.cancel()
                    this.$emit('refresh')
                }).finally(() => {
                    this.loading = false
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.repo-base-info {
    .repo-type-radio-group {
        display: grid;
        grid-template: auto / repeat(6, 80px);
        grid-gap: 20px;
        ::v-deep .bk-form-radio-button {
            .bk-radio-button-text {
                height: auto;
                line-height: initial;
                padding: 0;
                border-radius: 2px;
                &:hover {
                    border-color: var(--primaryHoverColor);
                    box-shadow: 0px 0px 6px 0px var(--primaryHoverColor);
                }
            }
        }
        .repo-type-radio {
            position: relative;
            padding: 5px;
            width: 80px;
            height: 60px;
        }
    }
    .form-tip {
        font-size: 12px;
        color: var(--subsidiaryColor)
    }
}
</style>
