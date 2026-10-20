import { google } from "npm:googleapis@144";
import { createClient } from "npm:@supabase/supabase-js@2";

// ── Configuración por categoría ──────────────────────────────────────
// Cada pestaña de equipos tiene su propio layout. La última columna es id_interno.
type Categoria = "CPU" | "MONITORES" | "PROYECTORES";

const CONFIG: Record<Categoria, { etiquetaEquipo: string; etiquetaKardex: string; rango: string }> = {
  CPU:         { etiquetaEquipo: "ALL IN ONE", etiquetaKardex: "CPU",        rango: "CPU!A:F" },
  MONITORES:   { etiquetaEquipo: "MONITOR",    etiquetaKardex: "MONITOR",    rango: "MONITORES!A:G" },
  PROYECTORES: { etiquetaEquipo: "PROYECTOR",  etiquetaKardex: "PROYECTOR",  rango: "PROYECTORES!A:G" },
};
const CATEGORIAS = Object.keys(CONFIG) as Categoria[];
const METODOS = ["MANUAL", "CAMARA", "MIXTO"];
const ESTADOS_MONITOR = ["NUEVO", "USADO"];
const TIPO_MOVIMIENTO = "ALTA_REGULARIZACION";
const DOMINIO_PERMITIDO = "@autonoma.pe";

// ── Normalización (misma lógica que buscar-equipo + relleno a 8 dígitos) ──
function normCodigo(v: unknown): string | null {
  if (v == null) return null;
  const digits = String(v).replace(/\D/g, "");
  if (digits === "") return null;
  return digits.padStart(8, "0");
}
function normSerie(v: unknown): string | null {
  if (v == null) return null;
  const s = String(v).trim().toUpperCase();
  return s === "" || s === "S/N" ? null : s;
}
function normTexto(v: unknown): string {
  return String(v ?? "").trim().toUpperCase();
}

// ── Usuario: devuelve el correo si es válido, o null ─────────────────
async function obtenerUsuario(req: Request): Promise<{ email: string; nombre: string } | null> {
  const token = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
  if (!token) return null;
  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_ANON_KEY")!,
  );
  const { data, error } = await supabase.auth.getUser(token);
  if (error || !data.user) return null;
  const email = (data.user.email ?? "").toLowerCase();
  if (!email.endsWith(DOMINIO_PERMITIDO)) return null;
  const nombre = email
    .split("@")[0]
    .split(/[._-]+/)
    .filter(Boolean)
    .map((p) => p.charAt(0).toUpperCase() + p.slice(1))
    .join(" ");
  return { email, nombre };
}

// ── Google Sheets (con permiso de escritura) ─────────────────────────
function getSheetsClient() {
  const credsRaw = Deno.env.get("GOOGLE_SERVICE_ACCOUNT_KEY");
  if (!credsRaw) throw new Error("Falta GOOGLE_SERVICE_ACCOUNT_KEY");
  const creds = JSON.parse(credsRaw);
  const auth = new google.auth.JWT({
    email: creds.client_email,
    key: creds.private_key,
    scopes: ["https://www.googleapis.com/auth/spreadsheets"],
  });
  return google.sheets({ version: "v4", auth });
}

async function leerPestana(sheets: any, sheetId: string, nombre: string): Promise<unknown[][]> {
  const res = await sheets.spreadsheets.values.get({
    spreadsheetId: sheetId,
    range: `${nombre}!A2:F`,
  });
  return res.data.values ?? [];
}

async function agregarFila(sheets: any, sheetId: string, rango: string, fila: (string | number)[]) {
  await sheets.spreadsheets.values.append({
    spreadsheetId: sheetId,
    range: rango,
    valueInputOption: "RAW", // RAW conserva "00016860" como texto (USER_ENTERED lo volvería número)
    insertDataOption: "INSERT_ROWS",
    requestBody: { values: [fila] },
  });
}

// Fecha y hora de Lima (UTC-5, sin horario de verano), formato "2026-10-19 14:03:05"
function ahoraLima(): string {
  return new Date().toLocaleString("sv-SE", { timeZone: "America/Lima" });
}

function json(body: unknown, status = 200) {
  return Response.json(body, { status });
}

// ── Handler principal ────────────────────────────────────────────────
Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json({ error: "Método no permitido" }, 405);

  const usuario = await obtenerUsuario(req);
  if (!usuario) return json({ error: "No autorizado" }, 401);

  // 1) Leer y validar el body
  let body: Record<string, unknown>;
  try {
    body = await req.json();
  } catch {
    return json({ error: "Body inválido" }, 400);
  }

  const categoria = body.categoria as Categoria;
  if (!CATEGORIAS.includes(categoria)) {
    return json({ error: "categoria debe ser CPU, MONITORES o PROYECTORES" }, 400);
  }

  const marca = normTexto(body.marca);
  const modelo = normTexto(body.modelo);
  const serie = normSerie(body.serie);
  const ubicacion = String(body.ubicacion ?? "").trim();
  const observaciones = String(body.observaciones ?? "").trim();
  const metodo = normTexto(body.metodoCaptura || "MANUAL");

  let codigo: string | null = null;
  const codigoRaw = String(body.codigoPatrimonial ?? "").replace(/\D/g, "");
  if (codigoRaw.length > 8) {
    return json({ error: "El código patrimonial no puede tener más de 8 dígitos" }, 400);
  }
  codigo = normCodigo(body.codigoPatrimonial);

  if (!METODOS.includes(metodo)) {
    return json({ error: "metodoCaptura debe ser MANUAL, CAMARA o MIXTO" }, 400);
  }
  if (!codigo && !serie && !observaciones) {
    return json({ error: "Sin serie ni código patrimonial, las observaciones son obligatorias" }, 400);
  }

  let estadoMonitor = "";
  if (categoria === "MONITORES") {
    estadoMonitor = normTexto(body.estado);
    if (!ESTADOS_MONITOR.includes(estadoMonitor)) {
      return json({ error: "estado del monitor debe ser NUEVO o USADO" }, 400);
    }
  }

  let horasUso: number | "" = "";
  if (categoria === "PROYECTORES" && body.horasUso != null && String(body.horasUso).trim() !== "") {
    const h = Number(body.horasUso);
    if (!Number.isFinite(h) || h < 0) return json({ error: "horasUso inválido" }, 400);
    horasUso = h;
  }

  const sheetId = Deno.env.get("GOOGLE_SHEET_ID");
  if (!sheetId) return json({ error: "Falta GOOGLE_SHEET_ID" }, 500);

  try {
    const sheets = getSheetsClient();

    // 2) Duplicados: RAE primero, luego las pestañas activas
    const pestanas: { nombre: string; estadoGeneral: string }[] = [
      { nombre: "RAE", estadoGeneral: "RAE" },
      ...CATEGORIAS.map((c) => ({ nombre: c, estadoGeneral: "OPERATIVO" })),
    ];
    for (const p of pestanas) {
      const filas = await leerPestana(sheets, sheetId, p.nombre);
      for (const row of filas) {
        const filaCodigo = normCodigo(row[4]);
        const filaSerie = normSerie(row[3]);
        const mismoCodigo = codigo && filaCodigo && codigo === filaCodigo;
        const mismaSerie = serie && filaSerie && serie === filaSerie;
        if (mismoCodigo || mismaSerie) {
          return json({
            error: "El equipo ya existe",
            codigo: "DUPLICADO",
            estadoGeneral: p.estadoGeneral,
            categoria: p.estadoGeneral === "RAE" ? null : p.nombre,
            existente: {
              equipo: String(row[0] ?? "").trim().toUpperCase(),
              marca: String(row[1] ?? "").trim(),
              modelo: row[2] ? String(row[2]).trim() : null,
              serie: filaSerie,
              codigoPatrimonial: filaCodigo,
            },
          }, 409);
        }
      }
    }

    // 3) Armar las filas
    const cfg = CONFIG[categoria];
    const idEquipo = crypto.randomUUID();
    const idMovimiento = crypto.randomUUID();
    const serieTxt = serie ?? "S/N";
    const codigoTxt = codigo ?? "S/N";

    const filaEquipo: (string | number)[] = [cfg.etiquetaEquipo, marca, modelo, serieTxt, codigoTxt];
    if (categoria === "MONITORES") filaEquipo.push(estadoMonitor);
    if (categoria === "PROYECTORES") filaEquipo.push(horasUso);
    filaEquipo.push(idEquipo);

    const filaKardex: (string | number)[] = [
      idMovimiento, ahoraLima(), TIPO_MOVIMIENTO, idEquipo, cfg.etiquetaKardex,
      marca, modelo, serieTxt, codigoTxt, ubicacion, metodo, observaciones,
      usuario.email, usuario.nombre,
    ];

    // 4) Escribir: primero el equipo, luego el Kardex.
    //    Sheets no permite una transacción entre pestañas; si el Kardex fallara,
    //    se responde con codigo KARDEX_PENDIENTE e idEquipo para reconciliar a mano.
    //    (La app NO debe reintentar el alta: el equipo ya está escrito.)
    await agregarFila(sheets, sheetId, cfg.rango, filaEquipo);
    try {
      await agregarFila(sheets, sheetId, "KARDEX!A:N", filaKardex);
    } catch (e) {
      console.error("Kardex falló tras agregar equipo", idEquipo, e);
      return json(
        { error: "El equipo se guardó, pero falló el registro en el Kardex", codigo: "KARDEX_PENDIENTE", idEquipo },
        500,
      );
    }

    return json({ ok: true, idEquipo, idMovimiento, categoria }, 201);
  } catch (e) {
    console.error("registrar-equipo falló", e);
    return json({ error: "Error interno al registrar el equipo" }, 500);
  }
});