<template>
    <div class="empty-data-container flex-center" :class="{ 'hidden': isLoading }" :style="exStyle">
        <div class="flex-column flex-center">
            <img :src="search ? '/ui/no-search.png' : '/ui/no-data.png'" width="200" />
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
                default () {
                    return this.$t('noData')
                }
            },
            searchTitle: {
                type: String,
                default () {
                    return this.$t('noSearchData')
                }
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
