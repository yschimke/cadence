// Shares Cadence's settings and podcast library across the owner's browsers through the Wasmo
// install's key-value service (/api/wasmo/v1/kv). Only the .wasmo build loads this script, in place
// of cadence.js: it seeds localStorage from the install before the app starts, then writes each
// change back. A Wasmo that doesn't enable services for this install name keeps browser storage and
// says so. See docs/wasmo-app.md.
(() => {
  const SETTINGS = 'cadence-settings-v1';
  const PODCASTS = 'cadence-podcasts-v1';
  // Remote keys must match Wasmo's [a-z0-9][a-z0-9._-]{0,63}.
  const REMOTE = { [SETTINGS]: 'settings', [PODCASTS]: 'podcasts' };
  // Only the library and the gpodder server/user follow the owner. Folder targets, readiness,
  // positions sent from this device, Cadence cloud keys and Pocket Casts tokens stay per browser:
  // Wasmo's generic settings must not hold credentials.
  const SHARED_PODCAST_FIELDS = ['library', 'gpodder'];

  function request(path, options = {}) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), options.timeoutMs ?? 10000);
    const headers = new Headers(options.headers);
    headers.set('X-Wasmo-Api', '1');
    // A relative API path is deliberate: the owner's cookie must never leave the app origin.
    return fetch(`/api/wasmo/v1/${path}`, {
      method: options.method ?? 'GET', body: options.body, headers,
      credentials: 'same-origin', cache: 'no-store', redirect: 'error', signal: controller.signal,
    }).finally(() => clearTimeout(timeout));
  }

  const isJson = (response) => /^application\/json(?:\s*;|$)/i.test(response.headers.get('content-type') ?? '');

  // mode is 'kv' when the install has the service; otherwise why not: 'not-enabled' or
  // 'unavailable'. pocketCasts is whether the Wasmo keeps a Pocket Casts sign-in for it.
  async function probe(timeoutMs) {
    try {
      const response = await request('capabilities', { timeoutMs });
      if (response.status >= 500) return { mode: 'unavailable' };
      if (response.status !== 200 || !isJson(response)) return { mode: 'not-enabled' };
      const caps = await response.json().catch(() => null);
      if (caps?.api !== 1 || caps.kv !== true || caps.kvImport !== true) return { mode: 'not-enabled' };
      return { mode: 'kv', pocketCasts: caps.pocketCasts === true };
    } catch {
      return { mode: 'unavailable' };
    }
  }

  // Pocket Casts answers cross-origin calls only from its own site, so the Wasmo signs in and makes
  // a fixed set of read calls for the owner. Failures throw "Pocket Casts <status> <code>", which
  // the app turns into messages; status 0 means the Wasmo didn't answer.
  function pocketCasts() {
    async function send(method, path, body) {
      let response;
      try {
        response = await request(path, {
          method,
          headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
          body: body === undefined ? undefined : JSON.stringify(body),
          // A call may refresh the token and retry, each up to the Wasmo's 10 s deadline.
          timeoutMs: 35000,
        });
      } catch {
        throw new Error('Pocket Casts 0 unreachable');
      }
      const text = await response.text();
      if (!response.ok) {
        let code = 'error';
        try { code = JSON.parse(text).error ?? code; } catch {}
        throw new Error(`Pocket Casts ${response.status} ${code}`);
      }
      return text;
    }
    return {
      async session() {
        const status = JSON.parse(await send('GET', 'pocketcasts/session'));
        return status.signedIn ? (status.email ?? '') : null;
      },
      async signIn(email, password) {
        return JSON.parse(await send('PUT', 'pocketcasts/session', { email, password })).email ?? email;
      },
      call(path, method, body) {
        return send('POST', 'pocketcasts/call', body == null ? { path } : { path, body: JSON.parse(body) });
      },
      async signOut() {
        await send('DELETE', 'pocketcasts/session', {});
      },
    };
  }

  async function readRemote() {
    const response = await request('kv');
    if (!response.ok || !isJson(response)) throw new Error(`Settings read failed (${response.status})`);
    return (await response.json()) ?? {};
  }

  function sharedPodcasts(json) {
    const state = json ? JSON.parse(json) : null;
    if (!state || typeof state !== 'object') return null;
    return Object.fromEntries(SHARED_PODCAST_FIELDS.filter((f) => f in state).map((f) => [f, state[f]]));
  }

  function remoteValue(key, json) {
    return key === PODCASTS ? sharedPodcasts(json) : json;
  }

  // The local value with the remote one applied: settings are replaced, the podcast state keeps
  // this browser's own fields.
  function seeded(key, remote) {
    if (key !== PODCASTS) return remote;
    let local = {};
    try { local = JSON.parse(localStorage.getItem(PODCASTS) ?? '{}') ?? {}; } catch {}
    return JSON.stringify({ ...local, ...remote });
  }

  async function seed() {
    let remote = await readRemote();
    if (!Object.values(REMOTE).some((name) => remote[name] != null)) {
      // First browser on this install: upload what it has, atomically and only into an empty
      // install. Another browser that won the race answers 409; its values win.
      const initial = {};
      for (const [key, name] of Object.entries(REMOTE)) {
        const value = remoteValue(key, localStorage.getItem(key));
        if (value != null) initial[name] = value;
      }
      if (Object.keys(initial).length) {
        const response = await request('kv/import', {
          method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(initial),
        });
        if (response.status === 409) remote = await readRemote();
        else if (!response.ok) throw new Error(`Settings import failed (${response.status})`);
        else remote = initial;
      }
    }
    for (const [key, name] of Object.entries(REMOTE)) {
      if (remote[name] != null) localStorage.setItem(key, seeded(key, remote[name]));
    }
  }

  // Latest unsaved value per key; a failed write is retried with the next change or on reconnect.
  const pending = new Map();
  let flushing = null;
  let warned = false;

  async function flush() {
    for (const [key, json] of [...pending]) {
      const response = await request(`kv/${REMOTE[key]}`, {
        method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(remoteValue(key, json)),
      });
      if (response.status === 413) {
        pending.delete(key);
        showNotice(`Your ${key === PODCASTS ? 'podcast library is' : 'settings are'} too large to save on your Wasmo. `
          + 'Changes are saved in this browser only.');
        continue;
      }
      if (!response.ok) throw new Error(`Settings write failed (${response.status})`);
      // A newer change may have arrived while this one was in flight.
      if (pending.get(key) === json) pending.delete(key);
    }
  }

  function save() {
    flushing ??= flush()
      .then(() => { warned = false; })
      .catch(() => {
        if (warned) return;
        warned = true;
        showNotice('Changes couldn’t be saved to your Wasmo. They’re kept in this browser and retried with your next change.');
      })
      .finally(() => {
        flushing = null;
        if (pending.size && !warned) save();
      });
  }

  function showNotice(text) {
    const notice = document.createElement('div');
    notice.setAttribute('role', 'status');
    notice.style.cssText = 'position:fixed;inset:0 0 auto 0;z-index:2147483647;display:flex;gap:12px;align-items:center;'
      + 'padding:8px 12px;font:14px/1.4 system-ui,sans-serif;background:#fff4d6;color:#3d2e00;box-shadow:0 1px 4px #0003';
    const message = document.createElement('span');
    message.style.flex = '1';
    message.textContent = text;
    const close = document.createElement('button');
    close.textContent = 'Dismiss';
    close.addEventListener('click', () => notice.remove());
    notice.append(message, close);
    // Outside <body>: Compose replaces the body's children when it mounts.
    document.documentElement.append(notice);
  }

  function showStartupError() {
    const boot = document.getElementById('boot');
    if (!boot) return;
    const retry = document.createElement('button');
    retry.textContent = 'Reload';
    retry.addEventListener('click', () => location.reload());
    boot.replaceChildren('Cadence’s settings on your Wasmo could not be loaded. ', retry);
  }

  function startApp() {
    const script = document.createElement('script');
    script.src = 'cadence.js';
    document.body.append(script);
  }

  (async () => {
    let caps = await probe(3000);
    // A backend that is starting up or briefly overloaded gets one longer second chance.
    if (caps.mode === 'unavailable') caps = await probe(6000);
    const mode = caps.mode;
    if (mode === 'kv') {
      try {
        await seed();
      } catch {
        // Starting from this browser's copy would silently fork the owner's settings.
        showStartupError();
        return;
      }
      globalThis.cadenceStorage = {
        changed(key, json) {
          if (!(key in REMOTE)) return;
          pending.set(key, json);
          warned = false;
          save();
        },
      };
      addEventListener('online', () => { if (pending.size) save(); });
      if (caps.pocketCasts) globalThis.cadencePocketCasts = pocketCasts();
    } else {
      showNotice(mode === 'not-enabled'
        ? 'This Wasmo isn’t sharing Cadence’s settings between your browsers. Install the app under the name your Wasmo enables for it (usually “cadence”). Until then, they’re saved in this browser only.'
        : 'Your Wasmo didn’t answer, so settings are saved in this browser only for now. Reload to try again.');
    }
    startApp();
  })();
})();
