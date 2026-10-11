@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package ee.schimke.cadence.browser

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.JsAny
import kotlinx.coroutines.suspendCancellableCoroutine

@JsFun(
  "(op,args,ok,fail) => globalThis.cadencePlatform.call(op,JSON.parse(args)).then(r=>ok(JSON.stringify(r))).catch(e=>fail((e.name || 'Error') + ': ' + e.message))"
)
private external fun callAsync(
  op: String,
  args: String,
  ok: (String) -> Unit,
  fail: (String) -> Unit,
)

suspend fun browserCall(op: String, args: String): String = suspendCancellableCoroutine { c ->
  callAsync(
    op,
    args,
    { if (c.isActive) c.resume(it) },
    { if (c.isActive) c.resumeWithException(IllegalStateException(it)) },
  )
}

@JsFun("(ok,fail) => globalThis.cadencePlatform.pickDirectory(ok,fail)")
external fun pickBrowserDirectory(ok: (String) -> Unit, fail: (String) -> Unit)

@JsFun("() => globalThis.cadencePlatform.directoryMessage()")
external fun directoryMessage(): String

@JsFun("() => globalThis.cadencePlatform.directoryAvailable()")
external fun directoryAvailable(): Boolean

@JsFun("(callback) => globalThis.cadencePlatform.watchUsb(callback)")
external fun watchBrowserUsb(callback: (String) -> Unit): JsAny

@JsFun("(dispose) => dispose()") external fun disposeBrowserUsb(dispose: JsAny)

@JsFun("(ok,fail) => globalThis.cadencePlatform.connectUsb(ok,fail)")
external fun connectBrowserUsb(ok: (String) -> Unit, fail: (String) -> Unit)

@JsFun("() => globalThis.cadencePlatform.disconnectUsb()") external fun disconnectBrowserUsb()

@JsFun("() => localStorage.getItem('cadence-settings-v1')") external fun readSettings(): String?

@JsFun("(data) => localStorage.setItem('cadence-settings-v1',data)")
external fun writeSettings(data: String)

@JsFun(
  "(url) => { const u = new URL(url); if (u.protocol !== 'https:' && u.protocol !== 'http:') throw new Error('Unsupported URL'); window.open(u.href,'_blank','noopener,noreferrer'); }"
)
external fun openBrowser(url: String)

@JsFun("() => { document.getElementById('boot')?.remove(); }") external fun removeBootMessage()
