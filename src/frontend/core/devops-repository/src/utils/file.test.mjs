import test from 'node:test'
import assert from 'node:assert/strict'
import { isCode, isHtmlFile, isMedia, isMediaAudio, isMediaVideo, isOutDisplayType, isText } from './file.js'

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
    assert.equal(isCode('readme.txt'), 'txt')
    assert.equal(isCode('photo.png'), undefined)
    assert.equal(isCode('report.doc'), undefined)
    assert.equal(isCode('foo.path'), undefined)
    assert.equal(isCode('index.html'), undefined)
    assert.equal(isCode('page.htm'), undefined)
})

test('isCode recognizes ini and toml as code preview', () => {
    assert.equal(isCode('config.ini'), 'ini')
    assert.equal(isCode('Cargo.toml'), 'toml')
    assert.equal(isCode('ini'), 'ini')
    assert.equal(isCode('toml'), 'toml')
    assert.equal(isText('config.ini'), undefined)
    assert.equal(isText('Cargo.toml'), undefined)
})

test('isCode recognizes former plain text suffixes for Monaco preview', () => {
    assert.equal(isCode('notes.txt'), 'txt')
    assert.equal(isCode('run.bat'), 'bat')
    assert.equal(isCode('app.log'), 'log')
    assert.equal(isCode('config.properties'), 'properties')
    assert.equal(isCode('payload.xml'), 'xml')
    assert.ok(isText('notes.txt'))
    assert.ok(isText('run.bat'))
    assert.ok(isText('app.log'))
    assert.ok(isText('config.properties'))
    assert.ok(isText('payload.xml'))
})

test('isHtmlFile recognizes html and htm', () => {
    assert.equal(isHtmlFile('report.html'), 'html')
    assert.equal(isHtmlFile('docs/page.HTM'), 'htm')
    assert.equal(isHtmlFile('html'), 'html')
    assert.equal(isHtmlFile('htm'), 'htm')
    assert.equal(isHtmlFile('index.js'), undefined)
    assert.equal(isHtmlFile('note.md'), undefined)
})

test('isOutDisplayType includes code and html files', () => {
    assert.ok(isOutDisplayType('Main.java'))
    assert.ok(isOutDisplayType('app.py'))
    assert.ok(isOutDisplayType('report.html'))
    assert.ok(isOutDisplayType('page.htm'))
    assert.ok(isOutDisplayType('config.ini'))
    assert.ok(isOutDisplayType('Cargo.toml'))
    assert.ok(isOutDisplayType('notes.txt'))
})

test('isText still recognizes overlapping suffixes for non-community fallback', () => {
    assert.ok(isText('data.json'))
    assert.ok(isText('script.sh'))
})

test('isMedia recognizes native preview whitelist and excludes flv', () => {
    assert.equal(isMediaVideo('clip.mp4'), true)
    assert.equal(isMediaVideo('clip.webm'), true)
    assert.equal(isMediaAudio('track.mp3'), true)
    assert.equal(isMediaAudio('track.wav'), true)
    assert.equal(isMediaAudio('track.ogg'), true)
    assert.equal(isMediaAudio('track.oga'), true)
    assert.equal(isMediaAudio('track.m4a'), true)
    assert.equal(isMedia('Demo.MP4'), true)
    assert.equal(isMedia('demo.flv'), false)
    assert.equal(isMedia('movie.mkv'), false)
})

test('isOutDisplayType does not treat media as generic preview types', () => {
    assert.equal(Boolean(isOutDisplayType('clip.mp4')), false)
    assert.equal(Boolean(isOutDisplayType('track.mp3')), false)
    assert.equal(Boolean(isOutDisplayType('demo.flv')), false)
})
