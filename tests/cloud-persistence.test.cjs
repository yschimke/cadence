// Run with NODE_PATH pointing at an installation of @electric-sql/pglite@0.5.11.
const { PGlite } = require('@electric-sql/pglite');
const fs = require('node:fs');
const assert = require('node:assert/strict');
(async () => {
 const db = new PGlite();
 try {
  await db.exec(`create role anon; create role authenticated;
    create schema auth; create table auth.users(id uuid primary key);
    create function auth.uid() returns uuid language sql stable as
      $$ select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
    grant usage on schema auth to anon, authenticated;
    insert into auth.users values ('11111111-1111-1111-1111-111111111111'), ('22222222-2222-2222-2222-222222222222');`);
  await db.exec(fs.readFileSync('cloud/supabase.sql', 'utf8'));
  await db.exec(`set role anon;`);
  await assert.rejects(db.query('select public.cadence_get()'), /permission denied/);
  await assert.rejects(db.query('select * from public.cadence_libraries'), /permission denied/);
  await db.exec(`reset role; set role authenticated; set request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111'`);
  assert.equal((await db.query('select public.cadence_get() as doc')).rows[0].doc, null);
  const document = { schema: 1, config: { chunkMinutes: 5 }, completions: [] };
  const put = revision => db.query('select public.cadence_put($1, $2::jsonb) as revision', [revision, JSON.stringify(document)]);
  assert.equal((await put(0)).rows[0].revision, 1);
  await assert.rejects(put(0), /Revision conflict/);
  assert.equal((await put(1)).rows[0].revision, 2);
  await assert.rejects(put(1), /Revision conflict/);
  await assert.rejects(db.query(`select public.cadence_put(2, '{"schema":2}'::jsonb)`), /Unsupported/);
  assert.equal((await db.query('select public.cadence_get() as doc')).rows[0].doc.revision, 2);
  await db.exec(`set request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222'`);
  assert.equal((await db.query('select public.cadence_get() as doc')).rows[0].doc, null);
  assert.equal((await db.query('select * from public.cadence_libraries')).rows.length, 0);
  await assert.rejects(db.query(`insert into public.cadence_libraries(user_id, document) values ('11111111-1111-1111-1111-111111111111', '{}')`), /row-level security/);
  assert.equal((await put(0)).rows[0].revision, 1);
  await db.exec(`set request.jwt.claim.sub = ''`);
  await assert.rejects(put(0), /Authentication required/);
  console.log('Cloud SQL passed: anonymous denial, two-user isolation, initial insert, revision conflicts, invalid schema and missing identity.');
 } finally { await db.close(); }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
