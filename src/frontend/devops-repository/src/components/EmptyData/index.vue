<template>
    <div class="empty-data-container flex-center" :class="{ 'hidden': isLoading }" :style="exStyle">
        <div class="flex-column flex-center">
            <img :src="search ? '/ui/no-search.png' : '/ui/no-data.png'" width="250" />
            <span class="mt5 empty-data-title">{{ search ? searchTitle : title }}</span>
            <span class="mt5 empty-data-subtitle">{{ subTitle }}</span>
        </div>
    </div>
</template>
<script>
    export default {
        name: 'emptyData',
        props: {
            search: {
                type: Boolean,
                default: false
            },
            exStyle: {
                type: String,
                default: ''
            },
            title: {
                type: String,
                default: '暂无数据'
            },
            searchTitle: {
                type: String,
                default: '搜索结果为空'
            },
            subTitle: String,
            isLoading: {
                type: Boolean,
                default: false
            }
        }
    }
</script>
<style lang="scss" scoped>
.empty-data-container {
    &.hidden {
        visibility: hidden;
    }
    .empty-data-title {
        font-size: 14px;
        font-weight: bold;
        color: var(--fontPrimaryColor);
    }
    .empty-data-subtitle {
        color: var(--fontSubsidiaryColor);
    }
}
</style>
