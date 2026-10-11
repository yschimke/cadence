/* Browser adapters for the observed mass-storage device; never claim a protected USB interface. */
(() => {
  const handles = new Map();
  const known = { vendorId: 0x05e3, productId: 0x0761 };
  let selectedDevice = null;
  let usbUpdate = null;
  let generation = 0;
  let connectionMessage = 'Connect USB to discover the storage device. Select its mounted folder with Manage New Device.';
  const blocked = new Set([0x01, 0x03, 0x08, 0x0b, 0x0e, 0x10, 0xe0]);
  const secure = () => globalThis.isSecureContext;
  const usbAvailable = () => secure() && !!navigator.usb;
  const directoryAvailable = () => secure() && typeof globalThis.showDirectoryPicker === 'function';
  const errorText = e => e.name === 'NotFoundError' || e.name === 'AbortError' ? 'Connection cancelled.' : `${e.name || 'Error'}: ${e.message || e}`;
  const db = () => new Promise((resolve, reject) => {
    const request = indexedDB.open('cadence-directories', 1);
    request.onupgradeneeded = () => request.result.createObjectStore('handles');
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
  async function storedHandle(key, value) {
    const database = await db();
    try {
      return await new Promise((resolve, reject) => {
        const tx = database.transaction('handles', value ? 'readwrite' : 'readonly');
        const request = value ? tx.objectStore('handles').put(value, key) : tx.objectStore('handles').get(key);
        tx.oncomplete = () => resolve(request.result);
        tx.onerror = () => reject(tx.error);
        tx.onabort = () => reject(tx.error || new Error('Directory handle storage aborted'));
      });
    } finally { database.close(); }
  }
  async function knownHandles() {
    const database = await db();
    try {
      return await new Promise((resolve, reject) => {
        const tx = database.transaction('handles', 'readonly');
        const store = tx.objectStore('handles');
        const keys = store.getAllKeys(); const values = store.getAll();
        tx.oncomplete = () => resolve(keys.result.map((key, index) => [key, values.result[index]]));
        tx.onerror = () => reject(tx.error);
        tx.onabort = () => reject(tx.error || new Error('Directory handle storage aborted'));
      });
    } finally { database.close(); }
  }
  function parts(path) {
    const [key, ...segments] = path.split('/');
    if (segments.some(s => !s || s === '.' || s === '..' || /[\\:]/.test(s))) throw new Error('Invalid relative path');
    return { key, segments };
  }
  async function rootHandle(key, write = false) {
    if (key === 'opfs:staging') return navigator.storage.getDirectory();
    let handle = handles.get(key) || await storedHandle(key);
    if (!handle) throw new Error('Directory is unavailable. Choose it again in the directory picker.');
    handles.set(key, handle);
    const mode = write ? 'readwrite' : 'read';
    // Re-authorize using the directory picker, which always runs under a user gesture.
    if (await handle.queryPermission({ mode }) !== 'granted') throw new Error('Directory permission expired. Choose the directory again before refreshing or syncing.');
    return handle;
  }
  async function directory(path, create = false) {
    const { key, segments } = parts(path);
    let dir = await rootHandle(key, create);
    for (const segment of segments) dir = await dir.getDirectoryHandle(segment, { create });
    return dir;
  }
  async function fileHandle(path, create = false) {
    const { key, segments } = parts(path);
    if (!segments.length) throw new Error('Expected a file path');
    const name = segments.pop();
    let dir = await rootHandle(key, create);
    for (const segment of segments) dir = await dir.getDirectoryHandle(segment, { create });
    return dir.getFileHandle(name, { create });
  }
  async function list(path, recursive) {
    let dir;
    try { dir = await directory(path); }
    catch (e) { if (path.startsWith('opfs:staging/') && e.name === 'NotFoundError') return []; throw e; }
    const result = [];
    async function walk(current, prefix) {
      for await (const [name, handle] of current.entries()) {
        const relative = prefix + name;
        if (handle.kind === 'directory' && recursive) await walk(handle, relative + '/');
        else result.push({ location: path + '/' + relative, relativePath: relative, size: handle.kind === 'file' ? (await handle.getFile()).size : 0, directory: handle.kind === 'directory' });
      }
    }
    await walk(dir, '');
    return result.sort((a, b) => a.relativePath.localeCompare(b.relativePath));
  }
  async function usbSnapshot() {
    const devices = usbAvailable() ? await navigator.usb.getDevices() : [];
    return { available: usbAvailable(), message: !secure() ? 'USB requires HTTPS or localhost.' : !navigator.usb ? 'WebUSB is unavailable in this browser. Use Chrome or Edge, or use the desktop/Android app.' : connectionMessage,
      devices: devices.filter(d => d.vendorId === known.vendorId && d.productId === known.productId).map((d, index) => ({ id: index, name: d.productName || 'USB Storage', vendorId: d.vendorId, productId: d.productId, manufacturerName: d.manufacturerName || null, productName: d.productName || null, deviceClass: 8 })) };
  }
  async function publishUsb() { if (usbUpdate) { try { usbUpdate(JSON.stringify(await usbSnapshot())); } catch (e) { usbUpdate(JSON.stringify({ available: false, message: errorText(e), devices: [] })); } } }
  globalThis.cadencePlatform = {
    directoryAvailable,
    directoryMessage: () => !secure() ? 'Directory access requires HTTPS or localhost.' : !directoryAvailable() ? 'This browser cannot select writable mounted directories. Use desktop/Android or Chrome/Edge.' : 'Select the mounted USB volume. WebUSB cannot access its protected mass-storage interface.',
    watchUsb(callback) {
      usbUpdate = callback;
      const connected = () => publishUsb();
      const disconnected = event => { if (selectedDevice === event.device) { generation++; selectedDevice = null; connectionMessage = 'USB disconnected. Reconnect the device and verify its mounted directory before syncing.'; } publishUsb(); };
      if (usbAvailable()) { navigator.usb.addEventListener('connect', connected); navigator.usb.addEventListener('disconnect', disconnected); }
      publishUsb();
      return () => { usbUpdate = null; if (navigator.usb) { navigator.usb.removeEventListener('connect', connected); navigator.usb.removeEventListener('disconnect', disconnected); } };
    },
    connectUsb(done, fail) {
      if (!usbAvailable()) { fail('WebUSB is unavailable. Use HTTPS and a browser with WebUSB support.'); return; }
      const token = ++generation;
      // Call requestDevice synchronously in the click handler, before any asynchronous boundary.
      navigator.usb.requestDevice({ filters: [known] }).then(async device => {
        if (token !== generation) return;
        selectedDevice = device;
        const interfaces = device.configurations.flatMap(c => c.interfaces.flatMap(i => i.alternates));
        if (interfaces.some(a => blocked.has(a.interfaceClass)) || (device.vendorId === known.vendorId && device.productId === known.productId)) {
          connectionMessage = 'USB storage detected. WebUSB blocks mass-storage interfaces (class 0x08). Select its mounted folder with Manage New Device to browse and sync audio.';
        } else {
          await device.open();
          connectionMessage = 'USB device opened. No vendor transfer protocol is defined for this device.';
        }
        const snapshot = await usbSnapshot();
        if (token === generation) done(JSON.stringify(snapshot));
      }).catch(e => { if (token === generation) { selectedDevice = null; fail(errorText(e)); } });
    },
    disconnectUsb() { generation++; const device = selectedDevice; selectedDevice = null; if (device?.opened) device.close().catch(() => {}); connectionMessage = 'USB disconnected from Cadence. Browser permission is retained; revoke it in site settings if needed.'; publishUsb(); },
    pickDirectory(done, fail) {
      if (!directoryAvailable()) { fail(this.directoryMessage()); return; }
      // Directory permissions also require an actual user gesture.
      globalThis.showDirectoryPicker({ mode: 'readwrite' }).then(async handle => {
        let key;
        for (const [savedKey, saved] of await knownHandles()) {
          if (typeof handle.isSameEntry === 'function' && await handle.isSameEntry(saved)) { key = savedKey; break; }
        }
        key ||= 'browser:' + crypto.randomUUID();
        await storedHandle(key, handle); handles.set(key, handle);
        done(JSON.stringify({ name: handle.name, location: key }));
      }).catch(e => fail(errorText(e)));
    },
    async call(op, args) {
      switch (op) {
        case 'files': return (await list(args.root, true)).filter(f => !f.directory);
        case 'entries': return list(args.root, false);
        case 'copy': {
          const source = await (await fileHandle(args.source)).getFile();
          const target = await fileHandle(args.target, true);
          const writable = await target.createWritable();
          try { await source.stream().pipeTo(writable); } catch (e) { try { await writable.abort(); } catch (_) {} throw e; }
          return true;
        }
        case 'clear': {
          const { key, segments } = parts(args.root);
          if (key !== 'opfs:staging' || !segments.length) throw new Error('Can only clear an app staging subdirectory');
          const name = segments.pop();
          let parent = await rootHandle(key, true);
          for (const segment of segments) parent = await parent.getDirectoryHandle(segment, { create: true });
          try { await parent.removeEntry(name, { recursive: true }); } catch (e) { if (e.name !== 'NotFoundError') throw e; }
          await parent.getDirectoryHandle(name, { create: true }); return true;
        }
        default: throw new Error('Unknown browser operation: ' + op);
      }
    }
  };
})();
