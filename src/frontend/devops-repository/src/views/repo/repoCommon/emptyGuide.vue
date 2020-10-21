<template>
    <div class="empty-guide-container">
        <div class="empty-guide-header">
            <div class="empty-guide-title">快速设置</div>
            <div class="empty-guide-subtitle">
                您还没有任何制品，参考下方使用指引来推送您的第一个制品或者
                <router-link class="router-link" :to="{ name: 'createRepo' }">
                    配置代理
                </router-link>
                以代理其他仓库的包。
            </div>
        </div>
        <div class="empty-guide-main">
            <div class="empty-guide-item">
                <header class="empty-guide-item-title">{{ $t('token') }}</header>
                <div class="empty-guide-item-main">
                    <div class="empty-guide-item-subtitle">
                        {{ $t('tokenSubTitle') }}
                        <router-link class="router-link" :to="{ name: 'repoToken' }">{{ $t('token') }}</router-link>
                    </div>
                    <bk-button theme="primary" @click="createToken">{{ $t('createToken') }}</bk-button>
                </div>
                <create-token-dialog ref="createToken"></create-token-dialog>
            </div>
            <div class="empty-guide-item" v-for="(section, index) in article" :key="`section${index}`">
                <header v-if="section.title" class="empty-guide-item-title">{{ section.title }}</header>
                <div class="empty-guide-item-main">
                    <div v-for="block in section.main" :key="block.subTitle">
                        <div class="empty-guide-item-subtitle">{{ block.subTitle }}</div>
                        <code-area bg-color="#f6f8fa" color="#63656E" v-if="block.codeList && block.codeList.length" :code-list="block.codeList"></code-area>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>
<script>
    import CodeArea from '@/components/CodeArea'
    import createTokenDialog from '@/views/repoToken/createTokenDialog'
    export default {
        name: 'emptyGuide',
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
$bgColor: #f1f8ff;
.empty-guide-container {
    position: relative;
    border: 1px solid $borderWeightColor;
    .empty-guide-header {
        position: sticky;
        top: 0;
        z-index: 2;
        padding: 15px 15px 20px;
        color: $fontBoldColor;
        border-bottom: 1px solid $borderWeightColor;
        background-color: $bgColor;
        .empty-guide-title {
            font-size: 16px;
            padding-bottom: 20px;
        }
        .empty-guide-subtitle {
            font-size: 12px;
            padding-left: 10px;
        }
    }
    .empty-guide-main {
        margin: 20px 30px;
        border-left: 2px solid $borderWeightColor;
        counter-reset: step;
        .empty-guide-item {
            padding-left: 30px;
            padding-bottom: 20px;
        }
        .empty-guide-item-title {
            position: relative;
            height: 40px;
            line-height: 40px;
            color: $fontBoldColor;
            font-weight: bold;
            &:before {
                counter-increment: step;
                content: counter(step);
                position: absolute;
                display: flex;
                justify-content: center;
                align-items: center;
                z-index: 1;
                width: 36px;
                height: 36px;
                margin-left: -51px;
                font-size: 12px;
                border: 1px solid $borderWeightColor;
                border-radius: 50%;
                color: $primaryColor;
                background-color: $bgColor;
            }
        }
        .empty-guide-item-main {
            padding-left: 15px;
            .empty-guide-item-subtitle {
                position: relative;
                margin-top: 15px;
                margin-bottom: 20px;
                &:before {
                    content: '';
                    position: absolute;
                    display: flex;
                    justify-content: center;
                    align-items: center;
                    z-index: 1;
                    width: 18px;
                    height: 18px;
                    margin-left: -57px;
                    font-size: 12px;
                    border: 1px solid $borderWeightColor;
                    border-radius: 50%;
                    color: $primaryColor;
                    background-color: $bgColor;
                }
            }
        }
    }
}
</style>
