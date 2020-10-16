<template>
    <bk-collapse class="repo-guide-container">
        <bk-collapse-item name="token">
            <h2 class="section-header">{{ $t('token') }}</h2>
            <div slot="content" class="section-main">
                <div class="sub-section flex-column">
                    <span class="mb10">
                        {{ $t('tokenSubTitle') }}
                        <router-link class="token-link" :to="{ name: 'repoToken' }">个人访问令牌</router-link>
                    </span>
                    <div class="token-main">
                        <bk-button theme="primary" @click="createToken">{{ $t('createToken') }}</bk-button>
                    </div>
                </div>
            </div>
            <create-token-dialog ref="createToken"></create-token-dialog>
        </bk-collapse-item>
        <bk-collapse-item v-for="(section, index) in article" :key="`section${index}`" :name="`section${index}`">
            <h2 v-if="section.title" class="section-header">{{ section.title }}</h2>
            <div slot="content" class="section-main">
                <div class="sub-section flex-column" v-for="block in section.main" :key="block.subTitle">
                    <span class="mb10">{{ block.subTitle }}</span>
                    <code-area v-if="block.codeList && block.codeList.length" :code-list="block.codeList"></code-area>
                </div>
            </div>
        </bk-collapse-item>
    </bk-collapse>
</template>
<script>
    import CodeArea from '@/components/CodeArea'
    import createTokenDialog from '@/views/repoToken/createTokenDialog'
    export default {
        name: 'repoGuide',
        components: { CodeArea, createTokenDialog },
        props: {
            article: {
                type: Array,
                default: []
            }
        },
        methods: {
            createToken () {
                this.$refs.createToken.showDialogHandler()
            }
        }
    }
</script>
<style lang="scss" scoped>
@import '@/scss/conf';
.repo-guide-container {
    /deep/ .bk-collapse-item {
        margin-bottom: 20px;
    }
    section + section {
        margin-top: 20px;
    }
    .section-header {
        padding-left: 10px;
        background-color: $bgLightColor;
    }
    .section-main {
        margin-top: 10px;
        padding: 20px;
        border: 2px dashed $borderWeightColor;
        border-radius: 5px;
        .sub-section {
            & + .sub-section {
                margin-top: 20px;
            }
        }
        .token-link {
            color: #3a84ff;
            &:hover {
                color: #699df4;
            }
        }
    }
}
</style>
