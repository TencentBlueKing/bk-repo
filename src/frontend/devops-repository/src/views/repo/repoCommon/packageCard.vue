<template>
    <div class="package-card-container flex-align-center">
        <div class="package-card-main flex-column">
            <div class="package-card-name flex-align-center">
                <icon class="mr10" size="20" :name="cardIcon" />
                {{ cardData.name }}
                <span class="ml10 package-card-data" v-if="cardData.type === 'MAVEN'"> (Group ID: {{ cardData.key.replace(/^.*\/\/(.+):.*$/, '$1') }})</span>
            </div>
            <div class="package-card-data flex-align-center">
                <div>{{ `${$t('latestVersion')}: ${cardData.latest}` }}</div>
                <div>{{ `${$t('versionCount')}: ${cardData.versions}` }}</div>
                <div>{{ `${$t('downloadCount')}: ${cardData.downloads}` }}</div>
                <div>{{ `${$t('lastModifiedDate')}: ${new Date(cardData.lastModifiedDate).toLocaleString()}` }}</div>
                <div>{{ `${$t('lastModifiedBy')}: ${cardData.lastModifiedBy}` }}</div>
            </div>
        </div>
        <i class="devops-icon icon-delete package-card-delete hover-btn" @click.stop="deleteCard"></i>
    </div>
</template>
<script>
    export default {
        name: 'packageCard',
        props: {
            cardData: {
                type: Object,
                default: {}
            },
            cardIcon: {
                type: String,
                default: 'default-docker'
            }
        },
        methods: {
            deleteCard () {
                this.$emit('delete-card')
            }
        }
    }
</script>
<style lang="scss" scoped>
@import '@/scss/conf';
.package-card-container {
    height: 60px;
    padding-left: 15px;
    border: 1px solid $borderWeightColor;
    border-radius: 5px;
    background-color: #fdfdfe;
    cursor: pointer;
    &:hover {
        border-color: $iconPrimaryColor;
    }
    .package-card-main {
        flex: 1;
        height: 100%;
        justify-content: space-around;
        .package-card-name {
            font-size: 16px;
            font-weight: bold;
        }
        .package-card-data {
            font-size: 14px;
            font-weight: normal;
            div {
                flex: 1;
                padding-right: 60px;
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
            }
        }
    }
    .package-card-delete {
        flex-basis: 50px;
        font-size: 16px;
    }
}
</style>
