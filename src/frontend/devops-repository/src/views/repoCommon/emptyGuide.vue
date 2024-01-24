<template>
    <div class="empty-guide-container">
        <div class="empty-guide-header flex-center flex-column">
            <div class="mb10 empty-guide-tip">{{$t('emptyGuide')}}</div>
            <div class="empty-guide-subtip">
                <span>{{$t('emptyGuideTip')}}</span>
                <template v-if="showRepoConfigRoute">
                    <span>，{{$t('or')}}</span>
                    <router-link :to="{ name: 'repoConfig', query: { repoName: this.$route.query.repoName } }">{{ $t('configureProxy') }}</router-link>
                    <span>{{ $t('emptyGuideProxyMsg') }}</span>
                </template>
            </div>
        </div>
        <div class="empty-guide-main">
            <div class="empty-guide-title">{{$t('quickSet')}}</div>
            <div class="empty-guide-item">
                <div class="guide-step">
                    <span class="step-count">step</span>
                </div>
                <header class="empty-guide-item-title">{{ $t('token') }}</header>
                <div class="empty-guide-item-main flex-between-center">
                    <div class="ml20 empty-guide-item-subtitle">
                        <bk-button text theme="primary" @click="createToken">{{ $t('createToken') }}</bk-button>
                        {{ $t('tokenSubTitle') }}
                        <router-link :to="{ name: 'repoToken' }">{{ $t('token') }}</router-link>
                    </div>
                </div>
                <create-token-dialog ref="createToken"></create-token-dialog>
            </div>
            <div class="empty-guide-item" v-for="(section, index) in article" :key="`section${index}`">
                <div class="guide-step">
                    <span class="step-count">step</span>
                </div>
                <header v-if="section.title" class="empty-guide-item-title">{{ section.title }}</header>
                <div class="empty-guide-item-main">
                    <div v-for="block in section.main" :key="block.subTitle">
                        <div v-if="block.subTitle" class="ml20 empty-guide-item-subtitle" :style="block.subTitleStyle">{{ block.subTitle }}</div>
                        <code-area class="mt15" v-if="block.codeList && block.codeList.length" :code-list="block.codeList"></code-area>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>
<script>
    import CodeArea from '@repository/components/CodeArea'
    import createTokenDialog from '@repository/views/repoToken/createTokenDialog'
    import { mapState } from 'vuex'
    export default {
        name: 'emptyGuide',
        components: { CodeArea, createTokenDialog },
        props: {
            article: {
                type: Array,
                default: () => []
            }
        },
        computed: {
            ...mapState(['userInfo']),
            showRepoConfigRoute () {
                return ['maven', 'pypi', 'npm', 'composer', 'nuget'].includes(this.$route.params.repoType)
            }
        },
        methods: {
            createToken () {
                this.$refs.createToken.userName = this.userInfo.name
                this.$refs.createToken.showDialogHandler()
            }
        }
    }
</script>
<style lang="scss" scoped>
.empty-guide-container {
    padding: 10px 60px 40px;
    position: relative;
    .empty-guide-header {
        position: sticky;
        top: -137px;
        padding: 40px 0 80px;
        z-index: 1;
        color: var(--fontPrimaryColor);
        background-color: white;
        .empty-guide-tip {
            font-size: 26px;
            font-weight: bold;
            color: var(--fontPrimaryColor);
        }
        .empty-guide-subtip {
            font-size: 12px;
            color: var(--fontSubsidiaryColor);
        }
    }
    .empty-guide-main {
        padding: 0 50px;
        border: 1px dashed var(--borderWeightColor);
        border-radius: 4px;
        counter-reset: step;
        .empty-guide-title {
            margin-left: 80px;
            padding: 40px 0 30px;
            font-size: 18px;
            font-weight: bold;
        }
        .empty-guide-item {
            --marginBottom: 20px;
            position: relative;
            margin-left: 80px;
            margin-bottom: var(--marginBottom);
            padding: 20px;
            background-color: var(--bgLighterColor);
            .guide-step {
                position: absolute;
                left: -80px;
                top: 30px;
                height: calc(100% + var(--marginBottom));
                border-left: 1px dashed var(--primaryColor);
                &:before {
                    content: '';
                    position: absolute;
                    width: 10px;
                    height: 10px;
                    margin: -12px 0 0 -12px;
                    border: 6px solid #d8e6ff;
                    background-color: var(--primaryColor);
                    border-radius: 50%;
                }
                .step-count {
                    position: absolute;
                    margin-left: 30px;
                    margin-top: 10px;
                    &:before {
                        position: absolute;
                        content: '0';
                        margin-top: -27px;
                        font-size: 20px;
                    }
                    &:after {
                        position: absolute;
                        counter-increment: step;
                        content: counter(step);
                        margin-top: -27px;
                        margin-left: -12px;
                        font-size: 20px;
                    }
                }
            }
            &:last-child {
                .guide-step {
                    height: 0;
                }
            }
        }
        .empty-guide-item-title {
            position: relative;
            color: var(--fontPrimaryColor);
            font-size: 16px;
            font-weight: bold;
        }
        .empty-guide-item-main {
            .empty-guide-item-subtitle {
                position: relative;
                padding-top: 15px;
            }
        }
    }
}
</style>
