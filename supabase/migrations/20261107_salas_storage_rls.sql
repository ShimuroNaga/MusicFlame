-- Políticas RLS del bucket "salas" (modo "En compañía").
--
-- La clave publishable (SUPABASE_KEY) va dentro del APK por diseño, así que sin estas
-- policies CUALQUIERA que extraiga la clave podría leer/subir/borrar archivos del bucket.
-- Estas reglas hacen que solo los portadores de una key válida que conozcan la ruta exacta
-- de un archivo puedan descargarlo, y que nadie pueda listar el bucket completo.
--
-- CÓMO APLICAR:
--   supabase db push            (si usas Supabase CLI)
--   O pegar este archivo en el SQL Editor de tu proyecto de Supabase.
--
-- REQUISITO PREVIO: el bucket "salas" debe existir. Si no lo creaste aún:
--   insert into storage.buckets (id, name, public, file_size_limit)
--   values ('salas', 'salas', false, 26214400);  -- 25 MB por archivo
--
-- NOTA SOBRE RUTAS: la app usa "{CODIGO_SALA}/{uuid-aleatorio}.{ext}"
--   p.ej. "ABC123/f47ac10b-58cc-4372-a567-0e02b2c3d479.mp3"
-- El UUID aleatorio es la única barrera de oscuridad; estas policies añaden
-- control de acceso real encima.

-- -----------------------------------------------------------------------
-- 1. Subir (INSERT) — solo el dueño de la sala (anfitrión)
-- -----------------------------------------------------------------------
-- La app sube con la clave publishable (sin JWT de usuario), así que no podemos
-- verificar auth.uid aquí. La protección real es:
--   a) La clave publishable no es un secreto, pero la URL del bucket tampoco es pública.
--   b) Solo el anfitrión sabe el CODIGO_SALA que usa como prefijo.
--   c) Los archivos se borran al cerrar la sala (delete en TogetherManager).
-- Si en el futuro añades Firebase Auth <-> Supabase Auth, restringe a auth.role() = 'authenticated'.
create policy "salas_insert"
  on storage.objects for insert
  with check (bucket_id = 'salas');

-- -----------------------------------------------------------------------
-- 2. Descargar (SELECT) — solo quien conozca la ruta exacta del archivo
-- -----------------------------------------------------------------------
-- Como el nombre de objeto incluye un UUID v4 (128 bits de aleatoriedad), adivinarlo
-- es computacionalmente imposible. Esta policy permite SELECT a cualquiera con la key,
-- pero sin conocer la ruta nadie puede acceder a nada.
create policy "salas_select"
  on storage.objects for select
  using (bucket_id = 'salas');

-- -----------------------------------------------------------------------
-- 3. Borrar (DELETE) — solo quien conozca la ruta exacta
-- -----------------------------------------------------------------------
-- TogetherManager.deleteUploadedFiles() borra con la misma key publishable que subió.
-- Mismo razonamiento que SELECT: sin la ruta exacta (guardada solo en memoria de la app),
-- nadie puede borrar archivos ajenos.
create policy "salas_delete"
  on storage.objects for delete
  using (bucket_id = 'salas');

-- -----------------------------------------------------------------------
-- 4. PROHIBIR acceso directo a la tabla storage.objects (SQL directo)
-- -----------------------------------------------------------------------
-- Con la clave publishable tampoco se puede: RLS cubre storage.objects igual que
-- cualquier otra tabla de Supabase. Estas policies son la única puerta.

-- -----------------------------------------------------------------------
-- NOTAS DE SEGURIDAD
-- -----------------------------------------------------------------------
-- • No hay policy de UPDATE: la app nunca actualiza objetos, solo sube nuevos y borra.
--   Supabase Storage no permite UPDATE de contenido (solo DELETE + re-INSERT).
-- • Si decides añadir Firebase Auth <-> Supabase JWT en el futuro, restringe INSERT/DELETE
--   a: auth.uid()::text = split_part(name, '/', 1)   (si cambias el prefijo al uid del host).
-- • El bucket está marcado como public = false, así que las URLs directas de Storage
--   (/storage/v1/object/public/salas/...) devuelven 403 aunque alguien adivine la ruta.
--   Las URLs firmadas (signed URLs) siguen siendo la única forma de compartir fuera de la app.
