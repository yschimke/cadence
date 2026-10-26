const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const bridge = fs.readFileSync('shared/src/wasmJsMain/resources/cadence-platform.js', 'utf8');
function environment(overrides = {}) {
 const events = new Map();
 const usb = { getDevices: async () => [], addEventListener: (n, cb) => events.set(n, cb), removeEventListener: n => events.delete(n) };
 const context = { navigator: { usb }, isSecureContext: true, console, crypto: { randomUUID: () => 'fixture' }, ...overrides };
 vm.createContext(context); vm.runInContext(bridge, context);
 return { api: context.cadencePlatform, events, usb };
}
function connect(api) { return new Promise((resolve, reject) => api.connectUsb(s => resolve(JSON.parse(s)), reject)); }
const device = () => ({ vendorId: 0x05e3, productId: 0x0761, productName: 'USB Storage', configurations: [{ interfaces: [{ alternates: [{ interfaceClass: 8 }] }] }] });
test('unsupported and insecure browsers explain why USB is unavailable', async () => {
 for (const options of [{ navigator: {} }, { isSecureContext: false }]) {
  const { api } = environment(options);
  const state = await new Promise(resolve => api.watchUsb(s => resolve(JSON.parse(s))));
  assert.equal(state.available, false); assert.match(state.message, /unavailable|HTTPS/);
 }
});
test('USB picker starts within the click and never opens or claims mass storage', async () => {
 const { api, usb } = environment(); const selected = device(); let active = true; let called = false;
 selected.open = () => assert.fail('Protected mass storage must not be opened');
 selected.claimInterface = () => assert.fail('Protected mass storage must not be claimed');
 usb.requestDevice = ({ filters }) => { assert.equal(active, true); called = true; assert.equal(filters[0].vendorId, 0x05e3); return Promise.resolve(selected); };
 usb.getDevices = async () => [selected];
 const pending = connect(api); active = false;
 assert.equal(called, true);
 const state = await pending; assert.equal(state.devices.length, 1); assert.match(state.message, /blocks mass-storage/);
});
test('cancelled picker reports cancellation and physical detach updates status', async () => {
 const { api, usb, events } = environment();
 usb.requestDevice = () => Promise.reject({ name: 'NotFoundError', message: 'No device selected' });
 await assert.rejects(connect(api), e => e === 'Connection cancelled.');
 const selected = device(); usb.requestDevice = () => Promise.resolve(selected); usb.getDevices = async () => [selected];
 const updates = []; const dispose = api.watchUsb(s => updates.push(JSON.parse(s)));
 await connect(api);
 usb.getDevices = async () => []; events.get('disconnect')({ device: selected });
 await new Promise(resolve => setImmediate(resolve));
 assert.equal(updates.at(-1).devices.length, 0); assert.match(updates.at(-1).message, /disconnected/);
 dispose(); assert.equal(events.size, 0);
});
test('disconnect invalidates an outstanding device chooser', async () => {
 const { api, usb } = environment(); let resolveChoice; let updated = false;
 usb.requestDevice = () => new Promise(resolve => { resolveChoice = resolve; });
 api.connectUsb(() => { updated = true; }, () => {}); api.disconnectUsb(); resolveChoice(device());
 await new Promise(resolve => setImmediate(resolve)); assert.equal(updated, false);
});
test('directory picker runs directly in the click and unsupported browsers explain fallback', () => {
 const { api } = environment(); let error;
 api.pickDirectory(() => assert.fail(), e => { error = e; }); assert.match(error, /desktop\/Android/);
 let clicked = true, called = false;
 const env = environment({ showDirectoryPicker: () => { assert.equal(clicked, true); called = true; return Promise.reject({ name: 'AbortError' }); } });
 env.api.pickDirectory(() => assert.fail(), () => {}); clicked = false; assert.equal(called, true);
});
test('browser backend rejects traversal and cannot clear external storage', async () => {
 const { api } = environment();
 await assert.rejects(api.call('clear', { root: 'browser:fixture/music' }), /Can only clear/);
 await assert.rejects(api.call('files', { root: 'browser:fixture/../escape' }), /Invalid relative path/);
});
function directoryHandle(name) {
 const children = new Map();
 return { name, kind: 'directory', children,
  queryPermission: async () => 'granted',
  async isSameEntry(other) { return this === other; },
  async getDirectoryHandle(name, { create = false } = {}) {
   if (!children.has(name)) { if (!create) throw { name: 'NotFoundError' }; children.set(name, directoryHandle(name)); }
   return children.get(name);
  },
  async getFileHandle(name, { create = false } = {}) {
   if (!children.has(name)) { if (!create) throw { name: 'NotFoundError' }; children.set(name, fileHandle(name, '')); }
   return children.get(name);
  },
  async *entries() { yield* children.entries(); },
  async removeEntry(name) { if (!children.delete(name)) throw { name: 'NotFoundError' }; }
 };
}
function fileHandle(name, contents) {
 const handle = { name, kind: 'file', contents };
 handle.getFile = async () => new Blob([handle.contents]);
 handle.createWritable = async () => {
  const chunks = [];
  const stream = new WritableStream({ write(chunk) { chunks.push(chunk); }, close() { handle.contents = Buffer.concat(chunks.map(chunk => Buffer.from(chunk))).toString(); } });
  // FileSystemWritableFileStream supports direct write/close as well as pipeTo.
  stream.write = async chunk => { const writer = stream.getWriter(); try { await writer.write(chunk); } finally { writer.releaseLock(); } };
  stream.close = async () => { const writer = stream.getWriter(); try { await writer.close(); } finally { writer.releaseLock(); } };
  return stream;
 };
 return handle;
}
function memoryIndexedDb() {
 const values = new Map();
 return { open() {
  const request = {};
  setImmediate(() => { request.result = {
   close() {},
   transaction() {
    const tx = { objectStore: () => ({
     getAllKeys() { const req = { result: [...values.keys()] }; setImmediate(() => tx.oncomplete()); return req; },
     getAll() { const req = { result: [...values.values()] }; return req; },
     put(value, key) { const req = {}; values.set(key, value); setImmediate(() => tx.oncomplete()); return req; },
     get(key) { const req = { result: values.get(key) }; setImmediate(() => tx.oncomplete()); return req; }
    }) }; return tx;
   }
  }; request.onsuccess(); }); return request;
 } };
}
test('browser copies a staged snapshot through directory handles and preserves unrelated target files', async () => {
 const opfs = directoryHandle('opfs'); const source = directoryHandle('audio'); const target = directoryHandle('usb');
 source.children.set('track.mp3', fileHandle('track.mp3', 'audio fixture'));
 target.children.set('keep.mp3', fileHandle('keep.mp3', 'unrelated'));
 const indexedDB = memoryIndexedDb(); let chosen = source; let id = 0;
 const { api } = environment({ navigator: { storage: { getDirectory: async () => opfs } }, indexedDB,
  crypto: { randomUUID: () => String(++id) }, showDirectoryPicker: async () => chosen });
 const pick = () => new Promise((resolve, reject) => api.pickDirectory(s => resolve(JSON.parse(s)), reject));
 const sourceKey = (await pick()).location; assert.equal((await pick()).location, sourceKey); chosen = target; const targetKey = (await pick()).location;
 const files = await api.call('files', { root: sourceKey }); assert.equal(files.length, 1);
 await api.call('clear', { root: 'opfs:staging/podcasts' });
 await api.call('copy', { source: files[0].location, target: 'opfs:staging/podcasts/track.mp3' });
 source.children.get('track.mp3').contents = 'changed later';
 await api.call('copy', { source: 'opfs:staging/podcasts/track.mp3', target: targetKey + '/podcasts/track.mp3' });
 assert.equal(target.children.get('podcasts').children.get('track.mp3').contents, 'audio fixture');
 assert.equal(target.children.get('keep.mp3').contents, 'unrelated');
 // Expired permission must fail with a recoverable message, rather than prompt outside a click.
 source.queryPermission = async () => 'prompt';
 await assert.rejects(api.call('files', { root: sourceKey }), /permission expired/);
});

test('podcast requests require HTTPS and never send ambient cookies or follow redirects', async () => {
 const calls = [];
 const { api } = environment({ URL, fetch: async (url, options) => { calls.push({ url, options }); return { ok: true, text: async () => 'fixture' }; } });
 await assert.rejects(api.call('podcastRequest', { url: 'http://example.com', method: 'GET' }), /HTTPS/);
 await assert.rejects(api.call('podcastRequest', { url: 'https://user:password@example.com', method: 'GET' }), /HTTPS/);
 assert.equal(await api.call('podcastRequest', { url: 'https://example.com', method: 'GET' }), 'fixture');
 assert.equal(calls[0].options.credentials, 'omit');
 assert.equal(calls[0].options.redirect, 'error');
});

test('podcast export writes only the owned folder, refuses collisions and rejects traversal', async () => {
 const opfs = directoryHandle('opfs'); const target = directoryHandle('usb');
 target.children.set('keep.mp3', fileHandle('keep.mp3', 'unrelated'));
 const privateRoot = await opfs.getDirectoryHandle('.cadence-podcasts', { create: true });
 const podcasts = await privateRoot.getDirectoryHandle('audio', { create: true });
 const name = 'episode-000000000000-' + 'a'.repeat(64) + '.mp3'; podcasts.children.set(name, fileHandle(name, 'prepared audio'));
 const { api } = environment({ navigator: { storage: { getDirectory: async () => opfs } },
  indexedDB: memoryIndexedDb(), crypto: { randomUUID: () => 'usb' }, showDirectoryPicker: async () => target });
 const selected = await new Promise((resolve, reject) => api.pickDirectory(s => resolve(JSON.parse(s)), reject));
 const args = { target: selected.location, files: [name], manifest: '{"schema":1}' };
 await assert.rejects(api.call('clear', { root: 'opfs:staging/.cadence-podcasts' }), /Reserved/);
 await api.call('podcastExport', args);
 const folder = target.children.get('Cadence').children.get('podcasts');
 assert.equal(folder.children.get(name).contents, 'prepared audio');
 assert.equal(folder.children.get('.cadence-owner').contents, 'cadence-podcasts-v1');
 assert.equal(target.children.get('keep.mp3').contents, 'unrelated');
 await assert.rejects(api.call('podcastExport', { ...args, files: ['../escape.mp3'] }), /Invalid podcast filename/);
 folder.children.delete('.cadence-owner');
 await assert.rejects(api.call('podcastExport', args), /not owned/);
 assert.equal(target.children.get('keep.mp3').contents, 'unrelated');
});
