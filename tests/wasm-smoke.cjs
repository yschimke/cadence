// Optional production UI smoke: install Playwright, serve the built distribution on localhost,
// then set NODE_PATH to the installation's node_modules and run this file.
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
(async () => {
 const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
 try {
  for (const supported of [true, false]) {
   const page = await browser.newPage({ viewport: { width: 1100, height: 800 } });
   const errors = []; page.on('pageerror', error => errors.push(error.message));
   await page.addInitScript(({ supported }) => {
    Object.defineProperty(globalThis, 'cadencePlatform', { configurable: true, set(api) {
     const watch = api.watchUsb;
     api.watchUsb = callback => watch(snapshot => { window.usbSnapshot = JSON.parse(snapshot); callback(snapshot); });
     const connect = api.connectUsb;
     api.connectUsb = (done, fail) => connect(snapshot => { window.usbSnapshot = JSON.parse(snapshot); done(snapshot); }, fail);
     const message = api.directoryMessage;
     api.directoryMessage = () => { window.directoryFeedback = message(); return window.directoryFeedback; };
     Object.defineProperty(globalThis, 'cadencePlatform', { value: api });
    } });
    if (!supported) {
     Object.defineProperty(navigator, 'usb', { value: undefined });
     globalThis.showDirectoryPicker = undefined;
     return;
    }
    let granted = false;
    const device = { vendorId: 0x05e3, productId: 0x0761, productName: 'USB Storage', manufacturerName: 'Fixture',
     configurations: [{ interfaces: [{ alternates: [{ interfaceClass: 8 }] }] }], open() { throw new Error('Must never open protected storage'); } };
    Object.defineProperty(navigator, 'usb', { value: {
     getDevices: async () => granted ? [device] : [], addEventListener() {}, removeEventListener() {},
     requestDevice() { window.usbHadActivation = navigator.userActivation.isActive; granted = true; return Promise.resolve(device); }
    } });
   }, { supported });
   await page.goto(process.env.CADENCE_WEB_URL || 'http://127.0.0.1:8765');
   await page.waitForSelector('canvas');
   await page.getByRole('button', { name: 'Controls', exact: true }).waitFor();
   await page.waitForTimeout(750);
   // Canvas navigation coordinates at the fixed viewport; DOM semantics can lag transitions.
   await page.mouse.click(1060, 52);
   await page.waitForTimeout(500);
   await page.mouse.click(130, 204);
   await page.waitForTimeout(1200);
   if (supported) {
    await page.mouse.click(65, 112);
    await page.waitForFunction(() => window.usbHadActivation !== undefined);
    assert.equal(await page.evaluate(() => window.usbHadActivation), true);
    await page.waitForFunction(() => /USB storage detected.*WebUSB blocks/.test(window.usbSnapshot?.message));
   } else {
    await page.waitForFunction(() => /WebUSB is unavailable/.test(window.usbSnapshot?.message));
    assert.equal(await page.evaluate(() => window.usbSnapshot.available), false);
    await page.mouse.click(550, 190);
    await page.waitForFunction(() => /This browser cannot select writable mounted directories/.test(window.directoryFeedback));
   }
   await page.waitForTimeout(300);
   await page.screenshot({ path: `/tmp/cadence-wasm-${supported ? 'usb' : 'unsupported'}.png` });
   assert.deepEqual(errors, []);
   await page.close();
   console.log(`Wasm UI smoke passed: ${supported ? 'USB chooser user activation and mass-storage handling' : 'unsupported browser and directory fallback'}`);
  }
 } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
