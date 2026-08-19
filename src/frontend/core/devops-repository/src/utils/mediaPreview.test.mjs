import test from 'node:test'
import assert from 'node:assert/strict'
import { formatMediaDuration, waveformBarsForSeed } from './mediaPreview.js'

test('formatMediaDuration renders mm:ss and hh:mm:ss', () => {
    assert.equal(formatMediaDuration(0), '0:00')
    assert.equal(formatMediaDuration(9), '0:09')
    assert.equal(formatMediaDuration(75), '1:15')
    assert.equal(formatMediaDuration(3661), '1:01:01')
    assert.equal(formatMediaDuration(undefined), '—')
    assert.equal(formatMediaDuration(-1), '—')
})

test('waveformBarsForSeed is stable for the same seed', () => {
    const a = waveformBarsForSeed('/drive/demo.mp3', 8)
    const b = waveformBarsForSeed('/drive/demo.mp3', 8)
    assert.equal(a.length, 8)
    assert.deepEqual(a, b)
    assert.ok(a.every(value => value >= 0.15 && value <= 1))
    assert.notDeepEqual(a, waveformBarsForSeed('/drive/other.mp3', 8))
})
