<template>
    <div class="repo-guide-container">
        <!-- 当前选择的操作是 设置凭证 时，需要显示 访问令牌 相关元素 -->
        <template v-if="optionType === 'setCredentials'">
            <div class="section-header">
                {{ $t('token') }}
            </div>
            <div class="pt10 pb10">
                {{ $t('clickInfo') + $t('space') }}
                <bk-button text theme="primary" @click="createToken">{{ $t('createToken') }}</bk-button>
                {{ $t('tokenDependSubTitle') }}
                <router-link :to="{ name: 'repoToken' }">{{ $t('token') }}</router-link>
            </div>
            <div v-if="repoType !== 'npm'">
                <div>{{$t('accessTokenPlaceholder')}}</div>
                <bk-input
                    class="mt10"
                    v-model.trim="tokenInput"
                    :placeholder="$t('accessTokenPlaceholder')"
                    clearable
                    @change="onChangeAccessToken">
                </bk-input>
            </div>
            <create-token-dialog ref="createToken"></create-token-dialog>
        </template>
        <template v-if="currentArticleList.length > 0">
            <div v-for="(currentArticle,cIndex) in currentArticleList" :key="cIndex">
                <div v-if="currentArticle.title" class="section-header pt10">
                    {{ currentArticle.title }}
                </div>
                <div class="section-main flex-column" v-for="block in currentArticle.main" :key="block.subTitle">
                    <!--    传参的 constructType === 当前json对象的 constructType时、
                            当前json对象不存在 constructType 这个字段、
                            当前json对象的 constructType 字段的值为 common，表示公用，
                            这三种情况下需要显示下面具体指引
                    -->
                    <template v-if="(constructType === block.constructType) || !block.constructType || (block.constructType === 'common') ">
                        <span v-if="block.title" class="section-header pt10">{{ block.title }}</span>
                        <span v-if="block.subTitle" class="sub-title pt10" :style="block.subTitleStyle">{{ block.subTitle }}</span>
                        <code-area class="mt15" v-if="block.codeList && block.codeList.length" :code-list="block.codeList"></code-area>
                    </template>
                </div>
            </div>
        </template>
    </div>
</template>
<script>
    import CodeArea from '@repository/components/CodeArea'
    import createTokenDialog from '@repository/views/repoToken/createTokenDialog'
    import { mapState, mapMutations } from 'vuex'
    export default {
        name: 'repoGuide',
        components: { CodeArea, createTokenDialog },
        props: {
            article: {
                type: Array,
                default: () => []
            },
            optionType: {
                type: String,
                required: true,
                describe: '当前选择的操作类型,例如设置凭证()、推送(push)、拉取(pull)、删除(delete)等'
            },
            constructType: {
                type: String,
                default: '',
                describe: '当前选择的构建工具类型，目前只限于maven和npm仓库中才有此类型，其他的为空字符串'
            }
        },
        data () {
            return {
                activeName: ['token', ...[0, 1, 2, 3, 4].map(v => `section${v}`)],
                tokenInput: ''
            }
        },
        computed: {
            ...mapState(['userInfo']),
            currentArticleList () {
                return this.article.map(item => {
                    return (item.optionType === this.optionType) ? item : ''
                }).filter(Boolean) || []
            },
            repoType () {
                return this.$route.params.repoType || ''
            }
        },
        methods: {
            ...mapMutations(['SET_DEPEND_ACCESS_TOKEN_VALUE']),
            createToken () {
                this.$refs.createToken.userName = this.userInfo.name
                this.$refs.createToken.showDialogHandler()
            },
            onChangeAccessToken () {
                this.SET_DEPEND_ACCESS_TOKEN_VALUE(this.tokenInput)
            }
        }
    }
</script>
<style lang="scss" scoped>
.repo-guide-container {
    position: relative;
    .section-header {
        padding-left: 10px;
        color: var(--fontPrimaryColor);
        font-weight: bold;
        font-size: 14px;
        &:before {
            position: absolute;
            left: 20px;
            content: "";
            width: 3px;
            height: 12px;
            background-color: var(--primaryColor);
            margin-top: 5px;
        }
    }
}
</style>
