<template>
    <div class="repo-docker-card flex-align-center">
        <img v-if="cardData.logoUrl" :src="cardData.logoUrl" width="60" height="60" />
        <icon v-else size="60" name="default-docker" />
        <div class="ml20 docker-card-title flex-column">
            <span class="title" :title="cardData.name">{{ cardData.name }}</span>
            <div class="mt15 subtitle" :title="cardData.description">{{ cardData.description || $t('noDescription') }}</div>
        </div>
        <div class="docker-card-info flex-column">
            <span>{{ cardData.lastModifiedBy }}</span>
            <span class="mt5">更新于 {{ new Date(cardData.lastModifiedDate).toLocaleString() }}</span>
        </div>
        <div class="docker-card-download">
            <i class="devops-icon icon-download"></i>
            <span>{{ cardData.downloadCount }}</span>
        </div>
        <i class="devops-icon icon-delete docker-card-delete hover-btn" @click.stop="deleteDocker"></i>
    </div>
</template>
<script>
    export default {
        name: 'dockerCard',
        props: {
            cardData: {
                type: Object,
                default: {}
            }
        },
        methods: {
            deleteDocker () {
                this.$emit('delete-docker')
            }
        }
    }
</script>
<style lang="scss" scoped>
@import '@/scss/conf';
.repo-docker-card {
    height: 80px;
    padding: 10px 15px;
    border: 1px solid $borderWeightColor;
    border-radius: 5px;
    cursor: pointer;
    &:hover {
        border-color: $iconPrimaryColor;
    }
    .docker-card-title {
        flex: 1;
        .title {
            font-size: 16px;
            font-weight: bold;
        }
        .title, .subtitle {
            max-width: 500px;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }
    }
    .docker-card-info {
        flex-basis: 300px;
    }
    .docker-card-download {
        flex-basis: 100px;
    }
    .docker-card-delete {
        flex-basis: 50px;
        font-size: 16px;
    }
}
</style>
