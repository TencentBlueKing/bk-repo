<template>
    <div class="empty-data-container flex-center" :class="{ 'hidden': isLoading }" style="background-color: white">
        <div class="flex-column flex-center">
            <img width="480" height="240" style="float: left;margin-right: 3px" src="/ui/440.svg" />
            <span class="mt5 empty-data-title">{{ $t('noBusinessSubTip') }}</span>
            <span class="mt5 empty-data-subtitle">{{ $route.params.msg }}</span>
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
        margin-top: 18px;
        font-size: 24px;
        line-height: 32px;
    }
    .empty-data-subtitle {
        margin-top: 16px;
        font-size: 14px;
        line-height: 22px;
        color: var(--fontSubsidiaryColor);
    }
}
</style>
