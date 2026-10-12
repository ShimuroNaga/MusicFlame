// Recibe el webhook de Ko-fi y guarda las compras de la tienda ("Shop Order").
//
// Ko-fi manda un POST application/x-www-form-urlencoded con un único campo "data"
// que contiene un JSON. Ese JSON trae "verification_token" (tu token de Ko-fi),
// "type" ("Shop Order"), "email", "message_id" y "shop_items" [{ direct_link_code, ... }].
//
// Despliegue (Ko-fi NO manda cabecera de autenticación de Supabase, por eso sin verificar JWT):
//   supabase secrets set KOFI_VERIFICATION_TOKEN=<tu token de Ko-fi>
//   supabase functions deploy kofi-webhook --no-verify-jwt
// URL para pegar en Ko-fi (More > API > Webhooks):
//   https://<tu-proyecto>.supabase.co/functions/v1/kofi-webhook

import { createClient } from "jsr:@supabase/supabase-js@2";

const KOFI_TOKEN = Deno.env.get("KOFI_VERIFICATION_TOKEN") ?? "";
const supabase = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
);

// Comparación de tiempo constante para no filtrar el token por diferencias de tiempo.
function safeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return new Response("Method not allowed", { status: 405 });

  let data: any;
  try {
    const raw = (await req.formData()).get("data");
    if (typeof raw !== "string") return new Response("Bad request", { status: 400 });
    data = JSON.parse(raw);
  } catch {
    return new Response("Bad request", { status: 400 });
  }

  if (!KOFI_TOKEN || !safeEqual(String(data?.verification_token ?? ""), KOFI_TOKEN)) {
    return new Response("Unauthorized", { status: 401 });
  }

  // Donaciones, suscripciones y comisiones se ignoran (pero se responde 200 para que Ko-fi no reintente).
  if (data.type !== "Shop Order") return new Response("ignored", { status: 200 });

  const email = String(data.email ?? "").trim().toLowerCase();
  const codes: string[] = (Array.isArray(data.shop_items) ? data.shop_items : [])
    .map((i: any) => String(i?.direct_link_code ?? "").trim())
    .filter((c: string) => c.length > 0);
  const messageId = String(data.message_id ?? "").trim();

  if (!email || codes.length === 0 || !messageId) return new Response("ignored", { status: 200 });

  const { error } = await supabase
    .from("kofi_purchases")
    .upsert({ message_id: messageId, email, item_codes: codes }, { onConflict: "message_id" });

  if (error) {
    console.error("kofi-webhook: no se pudo guardar la compra", error.message);
    return new Response("error", { status: 500 }); // Ko-fi reintenta
  }
  return new Response("ok", { status: 200 });
});
