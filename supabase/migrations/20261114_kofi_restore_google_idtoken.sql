-- Las compras de Ko-fi ahora se consultan con la Edge Function "kofi-restore" (ID token de Google
-- verificado en el servidor). Se eliminan las funciones RPC anteriores: ya no se usan, dependian de
-- un secreto HMAC dentro del APK y de pgcrypto (hmac vive en el esquema "extensions").
-- La tabla kofi_purchases sigue con RLS y SIN politicas: solo la service role (Edge Functions) entra.

drop function if exists public.get_kofi_purchases(text, text);
drop function if exists public.get_kofi_purchases(text);
