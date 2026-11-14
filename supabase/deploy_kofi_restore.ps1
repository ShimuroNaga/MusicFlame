# Despliega el restore de Ko-fi con cuenta de Google. Ejecutar en la raiz del repo (PowerShell).
# Requisitos: Supabase CLI instalado, `supabase login` y `supabase link --project-ref <tu-ref>` ya hechos.
$ErrorActionPreference = "Stop"
supabase secrets set GOOGLE_WEB_CLIENT_ID=176181653925-etnugbpe1mqhu1gl3lu1njbu9iihcn1k.apps.googleusercontent.com
supabase functions deploy kofi-restore --no-verify-jwt
supabase db push
Write-Host "Listo: kofi-restore desplegada y RPC vieja eliminada."
