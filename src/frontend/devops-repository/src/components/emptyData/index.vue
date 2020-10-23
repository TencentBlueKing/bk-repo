<template>
    <div class="empty-data-container flex-column flex-center">
        <i class="devops-icon icon-empty"></i>
        <span class="empty-tip">{{ $t('noData') }}</span>
    </div>
</template>
<script>
    export default {
        name: 'emptyData'
    }
</script>
<style lang="scss" scoped>
@import '@/scss/conf';
.empty-data-container {
    margin-top: 100px;
    .icon-empty {
        font-size: 65px;
        color: $borderLightColor;
    }
    .empty-tip {
        margin-top: 10px;
        font-size: 12px;
        color: $fontBoldColor;
    }
}
</style>
