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
                <div>{{ `${$t('downloads')}: ${cardData.downloads}` }}</div>
                <div>{{ `${$t('lastModifiedDate')}: ${formatDate(cardData.lastModifiedDate)}` }}</div>
                <div>{{ `${$t('lastModifiedBy')}: ${cardData.lastModifiedBy}` }}</div>
            </div>
        </div>
        <i class="devops-icon icon-delete package-card-delete hover-btn" @click.stop="deleteCard"></i>
    </div>
</template>
<script>
    import { formatDate } from '@/utils'
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
            formatDate,
            deleteCard () {
                this.$emit('delete-card')
            }
        }
    }
</script>
<style lang="scss" scoped>
@import '@/scss/conf';
.package-card-container {
    height: 70px;
    padding: 5px 20px;
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
                padding-right: 40px;
                width: 140px;
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
                &:nth-child(1) {
                    width: 200px;
                }
                &:nth-child(4) {
                    width: 300px;
                }
                &:nth-child(5) {
                    width: auto;
                }
            }
        }
    }
    .package-card-delete {
        flex-basis: 30px;
        font-size: 16px;
    }
}
</style>
