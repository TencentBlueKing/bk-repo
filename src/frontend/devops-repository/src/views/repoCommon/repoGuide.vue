<template>
    <bk-collapse v-model="activeName" class="repo-guide-container">
        <bk-collapse-item name="token">
            <header class="section-header">
                <icon size="20" class="section-title-icon" name="guide-h-left"></icon>
                {{ $t('token') }}
            </header>
            <template #content>
                <div class="section-main flex-column">
                    <span class="sub-title">
                        {{ $t('tokenSubTitle') }}
                        <router-link class="router-link" :to="{ name: 'repoToken' }">{{ $t('token') }}</router-link>
                    </span>
                    <div class="token-main">
                        <bk-button class="mt15" style="padding:0 8px;" theme="primary" @click="createToken">{{ $t('createToken') }}</bk-button>
                    </div>
                </div>
            </template>
            <create-token-dialog ref="createToken"></create-token-dialog>
        </bk-collapse-item>
        <bk-collapse-item v-for="(section, index) in article" :key="`section${index}`" :name="`section${index}`">
            <header v-if="section.title" class="section-header">
                <icon size="20" class="section-title-icon" name="guide-h-left"></icon>
                {{ section.title }}
            </header>
            <template #content>
                <div class="section-main flex-column" v-for="block in section.main" :key="block.subTitle">
                    <span v-if="block.subTitle" class="sub-title" :style="block.subTitleStyle">{{ block.subTitle }}</span>
                    <code-area class="mt15" v-if="block.codeList && block.codeList.length" :code-list="block.codeList"></code-area>
                </div>
            </template>
        </bk-collapse-item>
    </bk-collapse>
</template>
<script>
    import CodeArea from '@repository/components/CodeArea'
    import createTokenDialog from '@repository/views/repoToken/createTokenDialog'
    export default {
        name: 'repoGuide',
        components: { CodeArea, createTokenDialog },
        props: {
            article: {
                type: Array,
                default: () => []
            }
        },
        data () {
            return {
                activeName: ['token', ...[0, 1, 2, 3, 4].map(v => `section${v}`)]
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
.repo-guide-container {
    height: 100%;
    overflow-y: auto;
    ::v-deep .bk-collapse-item {
        margin-bottom: 20px;
        .bk-collapse-item-detail {
            color: inherit;
        }
        .bk-collapse-item-header {
            position: relative;
            height: 48px;
            line-height: 48px;
            .icon-angle-right {
                padding: 0 15px;
            }
        }
    }
    section + section {
        margin-top: 20px;
    }
    .section-header {
        padding-left: 15px;
        color: var(--fontPrimaryColor);
        background-color: var(--bgHoverColor);
        font-weight: bold;
        .section-title-icon {
            position: absolute;
            margin-left: -27px;
            margin-top: 14px;
        }
    }
    .section-main {
        margin-top: 15px;
        padding: 5px 20px 20px;
        border: 1px dashed var(--borderWeightColor);
        border-radius: 4px;
        .sub-title {
            margin-top: 15px;
        }
    }
}
</style>
