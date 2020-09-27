<template>
    <div class="create-repo-container">
        <header class="create-repo-header">
            <span class="mr5 hover-btn" @click="toRepoList">{{$t('repoManage')}}</span>
            <i class="devops-icon icon-angle-right"></i>
            <span class="ml5">{{$t('create') + $t('repository')}}</span>
        </header>
        <main class="create-repo-main">
            <div class="repo-base-info">
                <bk-form :label-width="200" :model="repoBaseInfo" :rules="rules" ref="repoBaseInfo">
                    <bk-form-item :label="$t('repoType')" :required="true" property="type" error-display-type="normal">
                        <bk-radio-group v-model="repoBaseInfo.type" class="repo-type-radio-group">
                            <bk-radio-button v-for="repo in repoEnum" :key="repo" :value="repo">
                                <div class="flex-center repo-type-radio">
                                    <icon size="60" :name="repo" />
                                    <span>{{repo}}</span>
                                    <div v-show="repoBaseInfo.type === repo" class="top-right-selected">
                                        <i class="devops-icon icon-check-1"></i>
                                    </div>
                                </div>
                            </bk-radio-button>
                        </bk-radio-group>
                    </bk-form-item>
                    <bk-form-item :label="$t('repoName')" :required="true" property="name" error-display-type="normal">
                        <bk-input v-model="repoBaseInfo.name" :placeholder="$t('repoNamePlacehodler')"></bk-input>
                    </bk-form-item>
                    <bk-form-item :label="$t('description')">
                        <bk-input type="textarea"
                            maxlength="200"
                            v-model="repoBaseInfo.description"
                            :placeholder="$t('repoDescriptionPlacehodler')">
                        </bk-input>
                    </bk-form-item>
                    <bk-form-item>
                        <bk-button class="mr5" :loading="isLoading" theme="primary" @click.stop.prevent="submitRepo">{{$t('submit')}}</bk-button>
                        <bk-button theme="default" @click="toRepoList">{{$t('cancel')}}</bk-button>
                    </bk-form-item>
                </bk-form>
            </div>
        </main>
    </div>
</template>
<script>
    import { repoEnum } from '@/store/publicEnum'
    import { mapActions } from 'vuex'
    export default {
        name: 'createRepo',
        data () {
            return {
                repoEnum,
                isLoading: false,
                repoBaseInfo: {
                    type: '',
                    name: '',
                    description: ''
                },
                rules: {
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
                            regex: /^[a-zA-Z][a-zA-Z0-9\-_]{1,31}$/,
                            message: this.$t('pleaseInput') + this.$t('legit') + this.$t('repoName'),
                            trigger: 'blur'
                        },
                        {
                            validator: this.asynCheckRepoName,
                            message: this.$t('repoName') + this.$t('repeat'),
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
            ...mapActions(['createRepo', 'checkRepoName']),
            toRepoList () {
                this.$router.push({
                    name: 'repoList'
                })
            },
            asynCheckRepoName () {
                return this.checkRepoName({
                    projectId: this.projectId,
                    name: this.repoBaseInfo.name
                }).then(res => !res)
            },
            async submitRepo () {
                await this.$refs.repoBaseInfo.validate()
                this.isLoading = true
                await this.createRepo({
                    body: {
                        projectId: this.projectId,
                        ...this.repoBaseInfo,
                        type: this.repoBaseInfo.type.toUpperCase()
                    }
                }).finally(() => {
                    this.isLoading = false
                })
                this.$bkMessage({
                    theme: 'success',
                    message: this.$t('create') + this.$t('repository') + this.$t('success')
                })
                this.toRepoList()
            }
        }
    }
</script>
<style lang="scss" scoped>
@import '@/scss/conf';
.create-repo-container {
    height: 100%;
    .create-repo-header {
        height: 60px;
        padding: 0 20px;
        display: flex;
        align-items: center;
        font-size: 14px;
        background-color: white;
    }
    .create-repo-main {
        height: calc(100% - 80px);
        margin-top: 20px;
        padding-top: 20px;
        display: flex;
        background-color: white;
        .repo-base-info {
            flex: 1;
            max-width: 1080px;
            .repo-type-radio-group {
                /deep/ .bk-form-radio-button {
                    margin: 0 20px 20px 0;
                    .bk-radio-button-text {
                        height: auto;
                        line-height: initial;
                        padding: 0;
                    }
                }
                .repo-type-radio {
                    position: relative;
                    padding: 10px;
                    width: 100px;
                    height: 100px;
                    flex-direction: column;
                    .top-right-selected {
                        position: absolute;
                        margin: -70px -70px 0 0;
                        border-width: 16px;
                        border-style: solid;
                        border-color: $primaryColor $primaryColor transparent transparent;
                        i {
                            position: absolute;
                            margin-top: -12px;
                            font-size: 12px;
                            color: white;
                        }
                    }
                }
            }
        }
    }
}
</style>
