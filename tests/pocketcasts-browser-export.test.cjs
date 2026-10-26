const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const { webcrypto } = require('node:crypto');
const source = fs.readFileSync('tools/pocketcasts-browser-export.js', 'utf8');
function install(responses) {
 const calls = [];
 const original = async (...args) => {
  calls.push(args);
  const value = responses[calls.length - 1];
  return new Response(value.body, { status: 200, headers: { 'content-type': value.type || 'application/json' } });
 };
 const context = { location: new URL('https://pocketcasts.com/'), URL, TextEncoder, TextDecoder, Uint8Array, crypto: webcrypto,
  fetch: original, setTimeout, Blob, localStorage: { getItem() { assert.fail('Must not read credentials'); } },
  document: { get cookie() { assert.fail('Must not read cookies'); } } };
 vm.createContext(context); vm.runInContext(source, context);
 return { context, calls, original };
}
const json = value => ({ body: JSON.stringify(value) });
async function request(context, path) {
 const input = { url: 'https://api.pocketcasts.com' + path, get headers() { assert.fail('Must not read request headers'); } };
 const response = await context.fetch(input, { method: 'POST' });
 assert.equal(response.status, 200); // Original response stays consumable by Pocket Casts.
 await response.arrayBuffer();
}

test('passively exports manual membership, order and positions without credential access or extra requests', async () => {
 const { context, calls, original } = install([
  json({ playlists: [{ uuid: 'wanted', title: 'Headphones', manual: true, episodeOrder: ['two', 'one'], episodes: [
   { episode: 'one', title: 'One', url: 'https://publisher.example/one.mp3' },
   { episode: 'two', title: 'Two', url: 'https://publisher.example/two.mp3' }
  ] }] }),
  json({ episodes: [{ uuid: 'one', duration: 120, playedUpTo: 30 }, { uuid: 'two', duration: 60, playedUpTo: 0 }], token: 'must-not-export' }),
  json({ token: 'auth-response-must-not-be-captured' })
 ]);
 await request(context, '/user/playlist/list');
 await request(context, '/user/episodes');
 await request(context, '/user/login_pocket_casts');
 const snapshot = await context.cadencePocket.snapshot('wanted');
 assert.deepEqual(Array.from(snapshot.episodes, e => e.id), ['two', 'one']);
 assert.equal(snapshot.episodes[0].positionMs, 0);
 assert.equal(snapshot.episodes[1].positionMs, 30000);
 assert.equal(snapshot.episodes[1].durationMs, 120000);
 assert.equal(snapshot.episodes[1].extension, 'mp3');
 assert.match(snapshot.episodes[0].audioVersion, /^[a-f0-9]{64}$/);
 assert.equal(calls.length, 3);
 assert.doesNotMatch(JSON.stringify(snapshot), /must-not-export|auth-response/);
 assert.equal(context.cadencePocket.status().readResponses['/user/login_pocket_casts'], undefined);
 context.cadencePocket.stop(); assert.equal(context.fetch, original);
});

function integer(value) { let n = BigInt(value); const bytes = []; do { let byte = Number(n & 127n); n >>= 7n; bytes.push(byte | (n ? 128 : 0)); } while (n); return Buffer.from(bytes); }
function bytes(number, value) { const body = typeof value === 'string' ? Buffer.from(value) : value; return Buffer.concat([integer(number * 8 + 2), integer(body.length), body]); }
function number(field, value) { return Buffer.concat([integer(field * 8), integer(value)]); }

test('decodes source-verified protobuf membership and legitimate zero playback position', async () => {
 const playlist = Buffer.concat([bytes(1, 'wanted'), bytes(3, 'Headphones'), bytes(12, number(1, 1)), bytes(24, 'episode')]);
 const episode = Buffer.concat([bytes(1, 'episode'), bytes(2, 'https://publisher.example/episode.mp3'), number(4, 3600), bytes(5, 'audio/mpeg'), bytes(6, 'An hour')]);
 const { context, calls } = install([
  { body: bytes(1, playlist), type: 'application/octet-stream' },
  { body: bytes(2, episode), type: 'application/octet-stream' }
 ]);
 await request(context, '/user/playlist/list'); await request(context, '/user/episodes');
 const snapshot = await context.cadencePocket.snapshot('wanted');
 assert.equal(snapshot.episodes[0].durationMs, 3600000);
 assert.equal(snapshot.episodes[0].positionMs, 0);
 assert.equal(calls.length, 2);
});

test('refuses smart rules as membership and refuses missing current positions', async () => {
 for (const manual of [false, true]) {
  const { context } = install([
   json({ playlists: [{ uuid: 'wanted', title: 'Wanted', manual, episodeOrder: ['episode'] }] }),
   json({ episodes: [{ uuid: 'episode', title: 'Episode', url: 'https://publisher.example/episode.mp3', duration: 60 }] })
  ]);
  await request(context, '/user/playlist/list'); await request(context, '/user/episodes');
  await assert.rejects(context.cadencePocket.snapshot('wanted'), manual ? /current position/ : /Smart playlist/);
 }
});

test('a malformed or unrecognized response leaves original network behavior intact', async () => {
 const { context } = install([{ body: Buffer.from([10, 100, 1]), type: 'application/octet-stream' }]);
 await request(context, '/user/playlist/list');
 await assert.rejects(context.cadencePocket.snapshot('unknown'), /not captured/);
 assert.equal(context.cadencePocket.status().unsupportedResponses, 1);
});
