<template>
    <div class="empty-data-container flex-center" :class="{ 'hidden': isLoading }" style="background-color: white">
        <div class="flex-column flex-center">
            <img width="480" height="240" style="float: left;margin-right: 3px" src="/ui/440.svg" />
            <span class="mt5 empty-data-title">{{ $t('WRONG PATH') }}</span>
        </div>
    </div>
</template>
<script>
    export default {
        name: 'Web404Component'
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
}
</style>
