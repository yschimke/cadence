-- Optional, operator-applied setup. This file is never deployed by the app.
-- One metadata document per authenticated user. Audio and credentials are not stored here.
begin;
create table if not exists public.cadence_libraries (
  user_id uuid primary key references auth.users(id) on delete cascade,
  revision bigint not null default 1 check (revision > 0),
  document jsonb not null check (jsonb_typeof(document) = 'object'),
  updated_at timestamptz not null default now()
);
alter table public.cadence_libraries enable row level security;
revoke all on public.cadence_libraries from anon, authenticated;
grant select, insert, update on public.cadence_libraries to authenticated;
drop policy if exists cadence_owner on public.cadence_libraries;
create policy cadence_owner on public.cadence_libraries to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

create or replace function public.cadence_get() returns jsonb
language sql stable security invoker set search_path = '' as $$
  select jsonb_build_object('revision', revision, 'document', document)
  from public.cadence_libraries where user_id = auth.uid();
$$;

create or replace function public.cadence_put(expected_revision bigint, new_document jsonb)
returns bigint language plpgsql security invoker set search_path = '' as $$
declare next_revision bigint;
begin
  if auth.uid() is null then raise sqlstate 'PT401' using message = 'Authentication required'; end if;
  if expected_revision < 0 or new_document->>'schema' is distinct from '1'
      or octet_length(new_document::text) > 8000000 then
    raise sqlstate 'PT400' using message = 'Unsupported or oversized document';
  end if;
  if expected_revision = 0 then
    insert into public.cadence_libraries(user_id, revision, document)
    values (auth.uid(), 1, new_document)
    on conflict (user_id) do nothing
    returning revision into next_revision;
  else
    update public.cadence_libraries
    set revision = revision + 1, document = new_document, updated_at = now()
    where user_id = auth.uid() and revision = expected_revision
    returning revision into next_revision;
  end if;
  if next_revision is null then
    raise sqlstate 'PT409' using message = 'Revision conflict: pull before saving';
  end if;
  return next_revision;
end;
$$;
revoke all on function public.cadence_get() from public, anon;
revoke all on function public.cadence_put(bigint, jsonb) from public, anon;
grant execute on function public.cadence_get() to authenticated;
grant execute on function public.cadence_put(bigint, jsonb) to authenticated;
commit;
