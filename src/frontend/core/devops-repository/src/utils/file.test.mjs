import test from 'node:test'
import assert from 'node:assert/strict'
import { isCode, isOutDisplayType, isText } from './file.js'

test('isCode recognizes backend CODE suffixes', () => {
    assert.ok(isCode('src/Main.java'))
    assert.ok(isCode('app.py'))
    assert.ok(isCode('main.go'))
    assert.ok(isCode('index.js'))
    assert.ok(isCode('styles.css'))
    assert.ok(isCode('lib.cpp'))
    assert.ok(isCode('App.cs'))
    assert.ok(isCode('header.h'))
    assert.ok(isCode('main.c'))
    assert.ok(isCode('config.yaml'))
    assert.ok(isCode('data.json'))
    assert.ok(isCode('script.sh'))
    assert.ok(isCode('query.sql'))
    assert.ok(isCode('java'))
    assert.equal(isCode('styles.css'), 'css')
    assert.equal(isCode('lib.cpp'), 'cpp')
    assert.equal(isCode('App.cs'), 'cs')
    assert.equal(isCode('readme.txt'), undefined)
    assert.equal(isCode('photo.png'), undefined)
    assert.equal(isCode('report.doc'), undefined)
    assert.equal(isCode('foo.path'), undefined)
})

test('isOutDisplayType includes code files', () => {
    assert.ok(isOutDisplayType('Main.java'))
    assert.ok(isOutDisplayType('app.py'))
})

test('isText still recognizes overlapping suffixes for non-community fallback', () => {
    assert.ok(isText('data.json'))
    assert.ok(isText('script.sh'))
})
