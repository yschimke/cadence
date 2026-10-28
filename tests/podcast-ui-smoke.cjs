// Optional production browser smoke. See docs/podcast-sync.md for dependencies and server setup.
const { chromium } = require('playwright');
const fs = require('node:fs');
const assert = require('node:assert/strict');
(async () => {
 const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
 const page = await browser.newPage({ viewport: { width: 1100, height: 2000 } });
 page.setDefaultTimeout(20000);
 async function tap(locator) {
  await locator.waitFor();
  const box = await locator.boundingBox();
  assert.ok(box && box.y >= 0 && box.y + box.height <= page.viewportSize().height, 'Control must be visible in the test viewport');
  // Compose renders into canvas; its semantic DOM nodes are covered by that canvas.
  await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
  await page.waitForTimeout(250);
 }
 const errors = []; page.on('pageerror', error => errors.push(error.message));
 try {
  await page.route('https://cadence.invalid/tone.mp3', route => { console.log('Serving generated tone'); return route.fulfill({
   status: 200, contentType: 'audio/mpeg', body: fs.readFileSync('tests/fixtures/podcast-tone.mp3'),
   headers: { 'access-control-allow-origin': '*' }
  }); });
  await page.goto(process.env.CADENCE_WEB_URL || 'http://127.0.0.1:8765');
  await page.getByText('Add your first source', { exact: true }).waitFor();
  await page.screenshot({ path: '/tmp/cadence-after-home.png', clip: {x:0,y:0,width:1100,height:800} });
  await tap(page.getByText('Podcasts', { exact: true }).first());
  await tap(page.getByText('Overcast or a saved export', { exact: true }));
  await page.waitForTimeout(500);
  const snapshot = { schema: 1, name: 'Headphone test', episodes: [{
   backend: 'fixture', id: 'tone', title: 'Two second test tone', audioUrl: 'https://cadence.invalid/tone.mp3',
   durationMs: 2000, positionMs: 1000, revision: 'position-v1', audioVersion: 'tone-v1', extension: 'mp3'
  }] };
  await tap(page.getByRole('textbox', { name: 'Export' }).first());
  await page.keyboard.press('ControlOrMeta+A'); await page.keyboard.insertText(JSON.stringify(snapshot));
  await page.screenshot({path:'/tmp/cadence-input-inspect.png'});
  await tap(page.getByRole('button', { name: 'Add', exact: true }));
  await page.waitForFunction(() => JSON.parse(localStorage.getItem('cadence-podcasts-v1'))?.library.playlist?.name === 'Headphone test');
  // Adding returns to the library, which now lists the episode.
  await tap(page.getByText('Headphone test', { exact: true }));
  await page.screenshot({ path: '/tmp/cadence-after-podcast.png' });
  await tap(page.getByRole('button', { name: 'Download', exact: true }));
  await page.waitForFunction(() => JSON.parse(localStorage.getItem('cadence-podcasts-v1'))?.readySectionIds?.length === 1);
  const state = await page.evaluate(() => JSON.parse(localStorage.getItem('cadence-podcasts-v1')));
  assert.equal(state.library.manifests[0].sections[0].startMs, 0);
  assert.equal(state.library.manifests[0].sections[0].endMs, 2000);
  await tap(page.getByRole('button', { name: 'Mark played', exact: true }));
  await page.waitForFunction(() => JSON.parse(localStorage.getItem('cadence-podcasts-v1'))?.library.completions?.length === 1);
  await page.screenshot({ path: '/tmp/cadence-after-sections.png' });
  await page.reload();
  await tap(page.getByText('Headphone test', { exact: true }));
  await page.waitForFunction(() => JSON.parse(localStorage.getItem('cadence-podcasts-v1'))?.readySectionIds?.length === 1);
  assert.equal((await page.evaluate(() => JSON.parse(localStorage.getItem('cadence-podcasts-v1')))).library.completions.length, 1);
  await page.emulateMedia({ colorScheme: 'dark' });
  await page.waitForTimeout(300);
  await page.screenshot({ path: '/tmp/cadence-after-dark.png' });
  await page.emulateMedia({ colorScheme: 'light' });
  await page.waitForTimeout(300);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: '/tmp/cadence-after-mobile.png' });
  assert.deepEqual(errors, []);
  console.log('Podcast UI passed: import, real OPFS download, unsplit manifest, mark played, reload and mobile-width render.');
 } catch (error) { console.log(await page.locator('body').ariaSnapshot()); await page.mouse.move(20,400);await page.mouse.wheel(0,-10000);await page.waitForTimeout(500); await page.screenshot({path:'/tmp/cadence-ui-failure.png'}); throw error; } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
