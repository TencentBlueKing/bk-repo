import test from 'node:test'
import assert from 'node:assert/strict'
import {
    isPurePreviewEnabled,
    buildImageViewerOptions
} from './imagePreview.js'

test('isPurePreviewEnabled is true for purePreview=1 or true', () => {
    assert.equal(isPurePreviewEnabled({ purePreview: '1' }), true)
    assert.equal(isPurePreviewEnabled({ purePreview: 'true' }), true)
    assert.equal(isPurePreviewEnabled({ purePreview: 'TRUE' }), true)
    assert.equal(isPurePreviewEnabled({ purePreview: 1 }), true)
    assert.equal(isPurePreviewEnabled({ purePreview: true }), true)
})

test('isPurePreviewEnabled is false when missing or other values', () => {
    assert.equal(isPurePreviewEnabled({}), false)
    assert.equal(isPurePreviewEnabled(undefined), false)
    assert.equal(isPurePreviewEnabled({ purePreview: '0' }), false)
    assert.equal(isPurePreviewEnabled({ purePreview: 'false' }), false)
    assert.equal(isPurePreviewEnabled({ purePreview: '' }), false)
})

test('buildImageViewerOptions keeps default chrome when pure preview is off', () => {
    assert.deepEqual(buildImageViewerOptions(), {
        inline: true,
        initialCoverage: 0.9
    })
    assert.deepEqual(buildImageViewerOptions({ purePreview: false }), {
        inline: true,
        initialCoverage: 0.9
    })
})

test('buildImageViewerOptions hides viewer chrome in pure preview mode', () => {
    assert.deepEqual(buildImageViewerOptions({ purePreview: true }), {
        inline: true,
        initialCoverage: 0.9,
        toolbar: false,
        button: false,
        title: false,
        navbar: false,
        tooltip: false,
        zoomable: true,
        movable: true,
        keyboard: true
    })
})
