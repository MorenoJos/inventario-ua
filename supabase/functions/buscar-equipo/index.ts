import { google } from "npm:googleapis@144";
import { createClient } from "npm:@supabase/supabase-js@2";

// ── Tipos por categoría ──────────────────────────────────────────────
interface EquipoBase {
  equipo: string;
  marca: string;
  modelo: string | null;
  serie: string | null;
  codigoPatrimonial: string | null;
}
interface CpuData extends EquipoBase {}
interface MonitorData extends EquipoBase { estado: string }
interface ProyectorData extends EquipoBase { horasUso: number | null }
interface RaeData extends EquipoBase {}

type Categoria = "CPU" | "MONITORES" | "PROYECTORES";

// ── Normalización para comparar, sin importar cómo llegó el dato ────
function normCodigo(v: unknown): string | null {
  if (v == null) return null;
  const digits = String(v).replace(/\D/g, "");
  return digits === "" ? null : digits;
}
function normSerie(v: unknown): string | null {
  if (v == null) return null;
  const s = String(v).trim().toUpperCase();
  return s === "" || s === "S/N" ? null : s;
}
function coincide(identificador: string, codigo: string | null, serie: string | null): boolean {
  const idCodigo = normCodigo(identificador);
  const idSerie = normSerie(identificador);
  if (idCodigo && codigo && idCodigo === normCodigo(codigo)) return true;
  if (idSerie && serie && idSerie === normSerie(serie)) return true;
  return false;
}

// ── Lectura del Sheet ─────────────────────────────────────────────────
async function getSheetsClient() {
  const credsRaw = Deno.env.get("GOOGLE_SERVICE_ACCOUNT_KEY");
  if (!credsRaw) throw new Error("Falta GOOGLE_SERVICE_ACCOUNT_KEY");
  const creds = JSON.parse(credsRaw);
  const auth = new google.auth.JWT({
    email: creds.client_email,
    key: creds.private_key,
    scopes: ["https://www.googleapis.com/auth/spreadsheets.readonly"],
  });
  return google.sheets({ version: "v4", auth });
}

function filaAEquipoBase(row: unknown[]): EquipoBase {
  return {
    equipo: String(row[0] ?? "").trim().toUpperCase(),
    marca: String(row[1] ?? "").trim(),
    modelo: row[2] ? String(row[2]).trim() : null,
    serie: normSerie(row[3]),
    codigoPatrimonial: normCodigo(row[4]),
  };
}

async function leerPestana(sheets: any, sheetId: string, nombre: string): Promise<unknown[][]> {
  const res = await sheets.spreadsheets.values.get({
    spreadsheetId: sheetId,
    range: `${nombre}!A2:F`, // fila 1 es encabezado, se omite
  });
  return res.data.values ?? [];
}

const DOMINIO_PERMITIDO = "@autonoma.pe";

async function usuarioAutorizado(req: Request): Promise<boolean> {
  const token = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
  if (!token) return false;
  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_ANON_KEY")!,
  );
  const { data, error } = await supabase.auth.getUser(token);
  if (error || !data.user) return false;
  return (data.user.email ?? "").toLowerCase().endsWith(DOMINIO_PERMITIDO);
}

// ── Handler principal ──────────────────────────────────────────────
Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "Método no permitido" }), { status: 405 });
  }
  if (!(await usuarioAutorizado(req))) {
    return Response.json({ error: "No autorizado" }, { status: 401 });
  }

  let identificador: string;
  try {
    const body = await req.json();
    identificador = String(body.identificador ?? "").trim();
    if (!identificador) throw new Error("vacío");
  } catch {
    return new Response(JSON.stringify({ error: "Falta 'identificador' en el body" }), { status: 400 });
  }

  const sheetId = Deno.env.get("GOOGLE_SHEET_ID");
  if (!sheetId) {
    return new Response(JSON.stringify({ error: "Falta GOOGLE_SHEET_ID" }), { status: 500 });
  }

  const sheets = await getSheetsClient();

  // 1) RAE primero — gana sobre cualquier otra tabla
  const filasRae = await leerPestana(sheets, sheetId, "RAE");
  for (const row of filasRae) {
    const base = filaAEquipoBase(row);
    if (coincide(identificador, base.codigoPatrimonial, base.serie)) {
      return Response.json({
        encontrado: true,
        estadoGeneral: "RAE",
        categoria: null,
        datos: base satisfies RaeData,
      });
    }
  }

  // 2) Tablas activas
  const categorias: Categoria[] = ["CPU", "MONITORES", "PROYECTORES"];
  for (const categoria of categorias) {
    const filas = await leerPestana(sheets, sheetId, categoria);
    for (const row of filas) {
      const base = filaAEquipoBase(row);
      if (!coincide(identificador, base.codigoPatrimonial, base.serie)) continue;

      let datos: CpuData | MonitorData | ProyectorData = base;
      if (categoria === "MONITORES") {
        datos = { ...base, estado: String(row[5] ?? "").trim() } satisfies MonitorData;
      } else if (categoria === "PROYECTORES") {
        const horas = row[5];
        datos = { ...base, horasUso: horas ? Number(horas) : null } satisfies ProyectorData;
      }

      return Response.json({
        encontrado: true,
        estadoGeneral: "OPERATIVO",
        categoria,
        datos,
      });
    }
  }

  // 3) No encontrado en ningún lado
  return Response.json({ encontrado: false, estadoGeneral: null, categoria: null, datos: null });
});