/*
 * Experimental, passive Pocket Casts -> Cadence snapshot export.
 * Run as a DevTools Sources snippet in your own signed-in Pocket Casts tab.
 * This makes NO network requests, reads NO cookies/storage/request headers, and
 * never changes playlists/playback. It only observes subsequent read responses.
 * Protocol reference: Automattic/pocket-casts-android sync_api.proto (2026-10-26).
 */
(() => {
  'use strict';
  if (!/(^|\.)pocketcasts\.com$/.test(location.hostname)) throw new Error('Run in the Pocket Casts web player.');
  if (globalThis.cadencePocket) throw new Error('Recorder already installed; call cadencePocket.stop() first.');
  const playlists = new Map(), episodes = new Map(), pending = new Set();
  const observed = new Map(); let failures = 0;
  const decoder = new TextDecoder(), encoder = new TextEncoder();
  const readPaths = new Set(['/user/playlist/list', '/user/episodes', '/user/podcast/episodes']);
  function classify(value) {
    try {
      const url = new URL(value, location.href);
      if (url.origin === 'https://api.pocketcasts.com' && readPaths.has(url.pathname)) return { path: url.pathname, cache: false };
      if (/^https:\/\/(podcast-cache|podcast-api)\.pocketcasts\.com$/.test(url.origin) &&
          (/^\/mobile\/podcast\/full\//.test(url.pathname) || /^\/podcasts\//.test(url.pathname))) return { path: '/podcast-cache', cache: true };
    } catch (_) {}
    return null;
  }
  function protobuf(bytes) {
    const fields = new Map(); let offset = 0;
    function integer() {
      let result = 0n, shift = 0n;
      for (let count = 0; count < 10; count++) {
        if (offset >= bytes.length) throw new Error('Truncated protobuf');
        const byte = bytes[offset++]; result |= BigInt(byte & 127) << shift;
        if (!(byte & 128)) return result;
        shift += 7n;
      }
      throw new Error('Invalid protobuf integer');
    }
    while (offset < bytes.length) {
      const tag = Number(integer()), number = tag >>> 3, wire = tag & 7;
      if (!number) throw new Error('Invalid protobuf field');
      let value;
      if (wire === 0) value = Number(integer());
      else if (wire === 2) {
        const length = Number(integer());
        if (!Number.isSafeInteger(length) || length < 0 || offset + length > bytes.length) throw new Error('Invalid protobuf length');
        value = bytes.subarray(offset, offset + length); offset += length;
      } else if (wire === 1 || wire === 5) {
        offset += wire === 1 ? 8 : 4; if (offset > bytes.length) throw new Error('Truncated protobuf'); continue;
      } else throw new Error('Unsupported protobuf wire type');
      if (!fields.has(number)) fields.set(number, []);
      fields.get(number).push(value);
    }
    return fields;
  }
  const first = (fields, number) => fields.get(number)?.[0];
  const text = bytes => bytes === undefined ? undefined : decoder.decode(bytes);
  const wrapped = (fields, number, string = false) => {
    const raw = first(fields, number);
    if (raw === undefined) return undefined;
    const value = raw instanceof Uint8Array ? first(protobuf(raw), 1) : raw;
    return string ? text(value) : Boolean(value || 0);
  };
  const unwrap = value => value && typeof value === 'object' && 'value' in value ? value.value : value;
  function memberId(value) { return typeof value === 'string' ? value : value?.episode || value?.uuid || value?.episodeUuid; }
  function rememberPlaylist(value) {
    const id = value.uuid;
    if (!id || unwrap(value.isDeleted ?? value.is_deleted)) return;
    const members = Array.isArray(value.episodes) ? value.episodes : [];
    let order = value.episodeOrder ?? value.episode_order ?? value.episodeUuids;
    if (typeof order === 'string') order = order.split(',').map(x => x.trim()).filter(Boolean);
    if (!Array.isArray(order) || !order.length) order = members;
    const ids = order.map(memberId).filter(x => typeof x === 'string' && x);
    playlists.set(id, { id, title: value.title || 'Pocket Casts playlist', manual: unwrap(value.manual) === true, ids: [...new Set(ids)] });
    for (const member of members) rememberEpisode({ uuid: memberId(member), title: unwrap(member.title), url: unwrap(member.url) });
  }
  function rememberEpisode(value, cache = false) {
    const id = value.uuid;
    if (!id) return;
    const next = { ...episodes.get(id), id };
    for (const [key, candidate] of Object.entries({
      title: value.title, url: value.url, duration: value.duration,
      position: cache ? undefined : value.playedUpTo ?? value.played_up_to,
      fileType: value.fileType ?? value.file_type,
    })) if (candidate !== undefined && candidate !== null) next[key] = candidate;
    if (!cache && (value.playedUpTo !== undefined || value.played_up_to !== undefined)) next.positionObservedAt = new Date().toISOString();
    episodes.set(id, next);
  }
  function ingest(endpoint, bytes, contentType) {
    if (bytes.length > 8_000_000) throw new Error('Response too large');
    const prefix = decoder.decode(bytes.subarray(0, 32)).trimStart();
    const json = /json/i.test(contentType || '') || prefix.startsWith('{') || prefix.startsWith('[');
    if (json) {
      const body = JSON.parse(decoder.decode(bytes));
      if (endpoint.path === '/user/playlist/list') for (const playlist of body.playlists || []) rememberPlaylist(playlist);
      else for (const episode of body.episodes || body.podcast?.episodes || []) rememberEpisode(episode, endpoint.cache);
    } else {
      if (endpoint.cache || endpoint.path === '/user/podcast/episodes') throw new Error('Unknown binary response');
      const fields = protobuf(bytes);
      if (endpoint.path === '/user/playlist/list') {
        for (const raw of fields.get(1) || []) {
          const p = protobuf(raw);
          rememberPlaylist({ uuid: text(first(p, 1)), title: text(first(p, 3)), manual: wrapped(p, 12), isDeleted: wrapped(p, 2),
            episodeOrder: (p.get(24) || []).map(text), episodes: (p.get(25) || []).map(rawMember => {
              const member = protobuf(rawMember);
              return { episode: text(first(member, 1)), title: wrapped(member, 5, true), url: wrapped(member, 6, true) };
            }) });
        }
      } else for (const raw of fields.get(2) || []) {
        const e = protobuf(raw);
        rememberEpisode({ uuid: text(first(e, 1)), url: text(first(e, 2)), title: text(first(e, 6)),
          duration: first(e, 4) || 0, playedUpTo: first(e, 9) || 0, fileType: text(first(e, 5)) });
      }
    }
    observed.set(endpoint.path, (observed.get(endpoint.path) || 0) + 1);
  }
  function observe(endpoint, bytes, type) {
    try { ingest(endpoint, bytes, type); } catch (_) { failures++; }
  }
  const originalFetch = globalThis.fetch;
  const wrappedFetch = function(...args) {
    const endpoint = classify(typeof args[0] === 'string' || args[0] instanceof URL ? String(args[0]) : args[0]?.url);
    return originalFetch.apply(this, args).then(response => {
      if (endpoint && response.ok) {
        const job = response.clone().arrayBuffer().then(buffer => observe(endpoint, new Uint8Array(buffer), response.headers.get('content-type'))).catch(() => { failures++; });
        pending.add(job); job.finally(() => pending.delete(job));
      }
      return response;
    });
  };
  globalThis.fetch = wrappedFetch;
  const xhr = globalThis.XMLHttpRequest?.prototype, originalOpen = xhr?.open;
  const xhrEndpoints = new WeakMap();
  const wrappedOpen = function(method, url, ...rest) {
    if (!xhrEndpoints.has(this)) this.addEventListener('load', () => {
      const endpoint = xhrEndpoints.get(this);
      if (!endpoint || this.status < 200 || this.status > 299) return;
      try {
        const bytes = this.responseType === 'arraybuffer' ? new Uint8Array(this.response)
          : encoder.encode(this.responseType === 'json' ? JSON.stringify(this.response) : this.responseText);
        observe(endpoint, bytes, this.getResponseHeader('content-type'));
      } catch (_) { failures++; }
    });
    xhrEndpoints.set(this, classify(url));
    return originalOpen.call(this, method, url, ...rest);
  };
  if (xhr) xhr.open = wrappedOpen;
  async function hash(value) {
    return Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', encoder.encode(value))), byte => byte.toString(16).padStart(2, '0')).join('');
  }
  function extension(episode) {
    const mime = episode.fileType?.split(';')[0]?.toLowerCase();
    const known = { 'audio/mpeg': 'mp3', 'audio/mp3': 'mp3', 'audio/mp4': 'm4a', 'audio/x-m4a': 'm4a', 'audio/ogg': 'ogg', 'audio/flac': 'flac', 'audio/wav': 'wav', 'audio/opus': 'opus' };
    if (known[mime]) return known[mime];
    const ext = new URL(episode.url).pathname.split('.').pop().toLowerCase();
    if (['mp3','m4a','ogg','opus','flac','wav'].includes(ext)) return ext;
    throw new Error('An episode has no supported progressive audio format.');
  }
  async function snapshot(playlistId) {
    await Promise.allSettled([...pending]);
    const playlist = playlists.get(playlistId);
    if (!playlist) throw new Error('Playlist not captured. Open it in the web player after installing the recorder.');
    if (!playlist.manual) throw new Error('Smart playlist rules are not resolved episode membership. Choose a manual playlist for this exporter.');
    if (!playlist.ids.length || playlist.ids.length > 1000) throw new Error('Empty or oversized playlist.');
    const result = [];
    for (let index = 0; index < playlist.ids.length; index++) {
      const episode = episodes.get(playlist.ids[index]);
      const incomplete = !episode?.url || !episode?.title || !Number.isFinite(episode.duration) || episode.duration <= 0 || !Number.isFinite(episode.position) || !episode.positionObservedAt;
      if (incomplete) throw new Error(`Episode ${index + 1} needs metadata/current position. Open its details in Pocket Casts, then retry.`);
      const url = new URL(episode.url);
      if (url.protocol !== 'https:' || url.username || url.password) throw new Error('Unsupported enclosure URL.');
      const durationMs = Math.round(episode.duration * 1000), positionMs = Math.round(episode.position * 1000);
      if (durationMs < 1 || durationMs > 2592000000 || positionMs < 0 || positionMs > durationMs) throw new Error('Playback position is outside the media timeline.');
      result.push({ backend: 'pocket-casts', id: episode.id, title: episode.title, audioUrl: episode.url,
        durationMs, positionMs, revision: `browser:${episode.positionObservedAt}:${positionMs}`,
        audioVersion: await hash(`${episode.url}|${durationMs}`), extension: extension(episode) });
    }
    return { schema: 1, name: playlist.title, episodes: result };
  }
  globalThis.cadencePocket = {
    list: () => Array.from(playlists.values(), p => ({ id: p.id, title: p.title, manual: p.manual, episodes: p.ids.length })),
    status: () => ({ playlists: playlists.size, episodes: episodes.size, readResponses: Object.fromEntries(observed), unsupportedResponses: failures }),
    // Returning a snapshot is useful for local tests. Prefer download() in a real account to avoid printing media URLs.
    snapshot,
    async download(playlistId) {
      const data = await snapshot(playlistId);
      const url = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' }));
      const anchor = document.createElement('a'); anchor.href = url; anchor.download = 'cadence-pocketcasts.json'; anchor.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    },
    stop() {
      if (globalThis.fetch === wrappedFetch) globalThis.fetch = originalFetch;
      if (xhr?.open === wrappedOpen) xhr.open = originalOpen;
      playlists.clear(); episodes.clear(); delete globalThis.cadencePocket;
    }
  };
})();
