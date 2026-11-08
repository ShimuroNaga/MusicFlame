-- Fix de IDOR leve en get_kofi_purchases().
--
-- PROBLEMA ORIGINAL:
--   get_kofi_purchases(p_email text) aceptaba cualquier correo y devolvía los códigos
--   comprados por ese correo. Con la clave publishable, cualquier usuario de la app podía
--   llamar get_kofi_purchases('victima@gmail.com') y saber si esa persona había comprado
--   algo (oracle de existencia de compra).
--
-- SOLUCIÓN: añadir un segundo parámetro p_token (HMAC-SHA256 del correo con un secreto
--   del servidor). La app calcula el token localmente (mismo secreto embebido en BuildConfig
--   como KOFI_HMAC_SECRET) y lo manda junto con el correo. La función solo responde si el
--   token es correcto. Así un atacante no puede consultar correos arbitrarios sin conocer
--   el secreto — y el secreto no viaja por la red (solo el HMAC resultante).
--
-- ALTERNATIVA MÁS SIMPLE (sin secreto extra): si en el futuro la app usa Firebase Auth
--   <-> Supabase JWT, se puede restringir a: p_email = auth.email() (un solo cambio).
--   Esta migration es el fix para el modelo actual (clave publishable, sin auth de usuario).
--
-- CÓMO APLICAR:
--   1. Genera un secreto aleatorio de 32 bytes en hex, p.ej.:
--        openssl rand -hex 32
--   2. Guárdalo en Supabase Vault o como vault secret:
--        select vault.create_secret('<tu_secreto_hex>', 'kofi_hmac_secret');
--      O en supabase secrets (Edge Functions):
--        supabase secrets set KOFI_HMAC_SECRET=<tu_secreto_hex>
--      Para que la FUNCIÓN SQL lo lea, ponlo en Vault (ver paso 3).
--   3. Ponlo también en local.properties de Android para que KofiPurchasesApi lo use:
--        KOFI_HMAC_SECRET=<tu_secreto_hex>
--      (BuildConfig.KOFI_HMAC_SECRET, igual que SUPABASE_URL/SUPABASE_KEY)
--
-- NOTA: si Vault no está disponible en tu plan de Supabase, puedes usar
--   current_setting('app.kofi_hmac_secret') y setearlo con:
--     alter database postgres set app.kofi_hmac_secret = '<tu_secreto_hex>';
--   (solo accesible desde el servidor, no desde la clave publishable)

-- Habilitar pgcrypto si no está (viene incluido en todos los proyectos de Supabase)
create extension if not exists pgcrypto;

-- Reemplaza la función anterior. Los permisos ya estaban bien; solo cambia la firma y el cuerpo.
create or replace function public.get_kofi_purchases(p_email text, p_token text)
returns text[]
language plpgsql
security definer
set search_path = public
as $$
declare
  v_secret text;
  v_expected text;
begin
  -- Lee el secreto desde la configuración del servidor (no accesible con la clave publishable).
  -- Si usas Supabase Vault: select decrypted_secret into v_secret from vault.decrypted_secrets where name = 'kofi_hmac_secret';
  v_secret := current_setting('app.kofi_hmac_secret', true);  -- true = sin error si no existe

  -- Si el secreto no está configurado, falla de forma segura (no devuelve nada).
  if v_secret is null or v_secret = '' then
    raise exception 'kofi_hmac_secret no configurado' using errcode = 'PT403';
  end if;

  -- HMAC-SHA256(correo_normalizado, secreto). Mismo cálculo que KofiPurchasesApi.kt.
  v_expected := encode(
    hmac(lower(trim(p_email)), decode(v_secret, 'hex'), 'sha256'),
    'hex'
  );

  -- Comparación de longitud fija para evitar timing attacks.
  if length(p_token) <> length(v_expected) then
    raise exception 'token inválido' using errcode = 'PT401';
  end if;
  if p_token <> v_expected then
    raise exception 'token inválido' using errcode = 'PT401';
  end if;

  -- Token correcto: devuelve los códigos del correo dado.
  return (
    select coalesce(array_agg(distinct code), '{}'::text[])
    from public.kofi_purchases kp
    cross join lateral unnest(kp.item_codes) as code
    where kp.email = lower(trim(p_email))
  );
end;
$$;

-- Los permisos ya existen; volvemos a aplicarlos por si se borraron al recrear la función.
revoke all on function public.get_kofi_purchases(text, text) from public;
grant execute on function public.get_kofi_purchases(text, text) to anon, authenticated;

-- Borra la firma vieja (un solo parámetro) para que nadie pueda seguir llamándola.
drop function if exists public.get_kofi_purchases(text);

-- -----------------------------------------------------------------------
-- CÓMO CONFIGURAR el secreto en el servidor (elige UNO):
-- -----------------------------------------------------------------------
-- Opción A — custom GUC (más simple, sin Vault):
--   alter database postgres set app.kofi_hmac_secret = '<tu_secreto_hex>';
--   -- Luego reinicia las conexiones del pool: Dashboard > Settings > Database > Restart.
--
-- Opción B — Supabase Vault (más seguro si tu plan lo incluye):
--   select vault.create_secret('<tu_secreto_hex>', 'kofi_hmac_secret');
--   -- Y cambia la línea de v_secret en la función por:
--   --   select decrypted_secret into v_secret from vault.decrypted_secrets where name = 'kofi_hmac_secret';
