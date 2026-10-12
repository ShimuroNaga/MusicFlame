-- Compras de la tienda de Ko-fi. Las escribe SOLO la Edge Function "kofi-webhook"
-- (con la service role). La app no toca la tabla: solo llama a get_kofi_purchases().

create table if not exists public.kofi_purchases (
  message_id  text primary key,                 -- id único del aviso de Ko-fi (evita duplicados)
  email       text not null,                    -- correo del comprador, en minúsculas
  item_codes  text[] not null,                  -- direct_link_code de cada producto comprado
  created_at  timestamptz not null default now()
);

create index if not exists kofi_purchases_email_idx on public.kofi_purchases (email);

-- RLS activo y SIN políticas: con la clave publishable nadie puede leer ni escribir la tabla directo.
alter table public.kofi_purchases enable row level security;

-- Única puerta de lectura: dado un correo, devuelve solo los códigos de producto comprados.
create or replace function public.get_kofi_purchases(p_email text)
returns text[]
language sql
security definer
set search_path = public
stable
as $$
  select coalesce(array_agg(distinct code), '{}'::text[])
  from public.kofi_purchases kp
  cross join lateral unnest(kp.item_codes) as code
  where kp.email = lower(trim(p_email));
$$;

revoke all on function public.get_kofi_purchases(text) from public;
grant execute on function public.get_kofi_purchases(text) to anon, authenticated;
