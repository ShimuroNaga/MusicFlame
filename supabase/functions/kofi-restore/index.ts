// Devuelve los productos de Ko-fi que compró la cuenta de Google que lo pide.
//
// La app manda un ID token de Google (de la cuenta que el usuario ELIGIÓ en el selector de
// cuentas). Aquí se verifica con Google (firma, audiencia, vigencia y correo verificado) y SOLO
// entonces se leen las compras de ESE correo con la service role. No hay secretos en el APK y
// nadie puede pedir las compras de un correo que no es suyo.
//
// Despliegue:
//   supabase secrets set GOOGLE_WEB_CLIENT_ID=<tu Web client ID de Google>
//   supabase functions deploy kofi-restore --no-verify-jwt
// (--no-verify-jwt porque la seguridad la da el ID token de Google, no el JWT de Supabase)

import { createClient } from "jsr:@supabase/supabase-js@2";

const WEB_CLIENT_ID = Deno.env.get("GOOGLE_WEB_CLIENT_ID") ?? "";
const supabase = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
);

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  if (!WEB_CLIENT_ID) return json({ error: "not_configured" }, 500);

  let idToken = "";
  try {
    idToken = String((await req.json())?.id_token ?? "");
  } catch {
    return json({ error: "bad_request" }, 400);
  }
  if (idToken.length < 20 || idToken.length > 4096) return json({ error: "bad_request" }, 400);

  // Google valida la firma y la vigencia del token.
  let info: any;
  try {
    const r = await fetch("https://oauth2.googleapis.com/tokeninfo?id_token=" + encodeURIComponent(idToken));
    if (!r.ok) return json({ error: "invalid_token" }, 401);
    info = await r.json();
  } catch {
    return json({ error: "upstream" }, 502);
  }

  const issOk = info?.iss === "accounts.google.com" || info?.iss === "https://accounts.google.com";
  const audOk = info?.aud === WEB_CLIENT_ID;
  const verified = info?.email_verified === true || info?.email_verified === "true";
  const notExpired = Number(info?.exp ?? 0) > Math.floor(Date.now() / 1000);
  const email = String(info?.email ?? "").trim().toLowerCase();
  if (!issOk || !audOk || !verified || !notExpired || !email) return json({ error: "invalid_token" }, 401);

  const { data, error } = await supabase
    .from("kofi_purchases")
    .select("item_codes")
    .eq("email", email);
  if (error) {
    console.error("kofi-restore: no se pudo leer", error.message);
    return json({ error: "db" }, 500);
  }

  const codes = new Set<string>();
  for (const row of data ?? []) for (const c of row.item_codes ?? []) codes.add(String(c));
  return json({ codes: [...codes] });
});
