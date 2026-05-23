// ╔══════════════════════════════════════════════════════════════════════════╗
// ║  Edge Function: bulk-create-lecturers                                    ║
// ║                                                                          ║
// ║  AMAÇ                                                                    ║
// ║  ─────                                                                   ║
// ║  Mobile uygulamanın 350+ hocayı Excel'den toplu import etmesini         ║
// ║  saniyeler içinde mümkün kılar. Mobile signUpWith varsayılan GoTrue     ║
// ║  rate-limit'ine (30 req/dk per IP) takılırken, bu fonksiyon            ║
// ║  service_role ile auth.admin.createUser çağırır → rate-limit YOK.       ║
// ║                                                                          ║
// ║  GÜVENLİK                                                                ║
// ║  ───────                                                                 ║
// ║  • service_role anahtarı Edge Function'ın ortamında gömülü; mobile      ║
// ║    asla görmez. Mobile sadece kendi admin JWT'sini gönderir.             ║
// ║  • Fonksiyon başında JWT doğrulanır + caller'ın role='admin'             ║
// ║    olduğu kontrol edilir + sadece kendi org'una insert yapabildiği      ║
// ║    enforce edilir → cross-org saldırı imkansız.                          ║
// ║  • Anonim biri çağıramaz; lecturer rolündeki kullanıcı çağıramaz.       ║
// ║                                                                          ║
// ║  ATOMICITY                                                               ║
// ║  ─────────                                                               ║
// ║  Her satır kendi içinde 3-aşamalı (auth → users → lecturers); bir       ║
// ║  aşama fail olursa önceki aşamalar rollback edilir (auth.deleteUser,    ║
// ║  users.delete). Yani orphan auth user kalmaz.                           ║
// ║                                                                          ║
// ║  DEPLOY                                                                  ║
// ║  ──────                                                                  ║
// ║  Supabase Dashboard → Edge Functions → "bulk-create-lecturers" adıyla   ║
// ║  yeni fonksiyon oluştur → bu dosyanın içeriğini yapıştır → Deploy.      ║
// ║  Veya CLI: supabase functions deploy bulk-create-lecturers              ║
// ╚══════════════════════════════════════════════════════════════════════════╝

import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { createClient, type SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2.45.4";

// ── Tipler ───────────────────────────────────────────────────────────────
interface LecturerRow {
    title?: string | null;
    firstName: string;
    lastName: string;
    email?: string | null;
    departmentName?: string | null;
}

interface RequestBody {
    orgId: number;
    rows: LecturerRow[];
    fallbackDepartmentId?: number | null;
}

interface Credential {
    username: string;
    password: string;
    firstName: string;
    lastName: string;
}

// ── CORS ─────────────────────────────────────────────────────────────────
const CORS_HEADERS = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const JSON_HEADERS = {
    ...CORS_HEADERS,
    "Content-Type": "application/json",
};

// ── Yardımcılar ──────────────────────────────────────────────────────────
function generatePassword(length = 8): string {
    const chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    let pass = "";
    const cryptoArray = new Uint32Array(length);
    crypto.getRandomValues(cryptoArray);
    for (let i = 0; i < length; i++) {
        pass += chars.charAt(cryptoArray[i] % chars.length);
    }
    return pass;
}

function usernameToEmail(username: string): string {
    return username.trim().toLowerCase().replace(/_/g, ".") + "@unischeduler.app";
}

function jsonError(message: string, status: number): Response {
    return new Response(JSON.stringify({ error: message }), {
        status,
        headers: JSON_HEADERS,
    });
}

// ── Ana handler ──────────────────────────────────────────────────────────
serve(async (req: Request): Promise<Response> => {
    // Preflight
    if (req.method === "OPTIONS") {
        return new Response(null, { headers: CORS_HEADERS });
    }
    if (req.method !== "POST") {
        return jsonError("Sadece POST kabul edilir.", 405);
    }

    // ── 1. Service-role client (rate-limit'ten muaf) ─────────────────────
    const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
    const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    if (!SUPABASE_URL || !SERVICE_KEY) {
        return jsonError("Edge Function ortam değişkenleri eksik (SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY).", 500);
    }
    const supabase: SupabaseClient = createClient(SUPABASE_URL, SERVICE_KEY, {
        auth: { autoRefreshToken: false, persistSession: false },
    });

    // ── 2. Caller JWT doğrula ────────────────────────────────────────────
    const authHeader = req.headers.get("Authorization");
    if (!authHeader || !authHeader.startsWith("Bearer ")) {
        return jsonError("Authorization header eksik.", 401);
    }
    const jwt = authHeader.slice("Bearer ".length).trim();
    const { data: userData, error: userErr } = await supabase.auth.getUser(jwt);
    if (userErr || !userData?.user) {
        return jsonError("Geçersiz veya süresi dolmuş JWT.", 401);
    }
    const callerId = userData.user.id;

    // ── 3. Caller'ın admin olduğunu doğrula ──────────────────────────────
    const { data: callerProfile, error: profErr } = await supabase
        .from("users")
        .select("role, org_id")
        .eq("id", callerId)
        .single();
    if (profErr || !callerProfile) {
        return jsonError("Kullanıcı profili bulunamadı.", 403);
    }
    if (callerProfile.role !== "admin") {
        return jsonError("Yetkisiz: sadece admin kullanıcılar bu fonksiyonu çağırabilir.", 403);
    }

    // ── 4. Request body parse + doğrulama ────────────────────────────────
    let body: RequestBody;
    try {
        body = await req.json();
    } catch (_e) {
        return jsonError("Geçersiz JSON gövdesi.", 400);
    }
    if (!body.orgId || typeof body.orgId !== "number") {
        return jsonError("orgId zorunlu (number).", 400);
    }
    if (!Array.isArray(body.rows) || body.rows.length === 0) {
        return jsonError("rows zorunlu ve dolu bir dizi olmalı.", 400);
    }
    // Çapraz org saldırısını engelle — admin sadece kendi org'una yazabilir
    if (callerProfile.org_id !== body.orgId) {
        return jsonError("Yetkisiz: sadece kendi organizasyonunuza hoca ekleyebilirsiniz.", 403);
    }

    const orgId = body.orgId;

    // ── 5. Department name → id map ──────────────────────────────────────
    const { data: depts } = await supabase
        .from("departments")
        .select("id, name")
        .eq("org_id", orgId);
    const deptByName = new Map<string, number>();
    for (const d of depts ?? []) {
        if (d.name) deptByName.set(String(d.name).toLowerCase().trim(), d.id as number);
    }

    // ── 6. Mevcut hocalar (idempotent skip için) ─────────────────────────
    const { data: existing } = await supabase
        .from("lecturers")
        .select("first_name, last_name")
        .eq("org_id", orgId);
    const existingKey = new Set<string>();
    for (const l of existing ?? []) {
        const k = `${(l.first_name || "").trim().toLowerCase()}|${(l.last_name || "").trim().toLowerCase()}`;
        existingKey.add(k);
    }

    // ── 7. Toplu işleme ──────────────────────────────────────────────────
    const credentials: Credential[] = [];
    const errors: string[] = [];
    const skipped: string[] = [];

    for (const row of body.rows) {
        const firstName = (row.firstName ?? "").toString().trim();
        const lastName = (row.lastName ?? "").toString().trim();
        if (!firstName || !lastName) {
            errors.push("Ad veya soyad boş — satır atlandı.");
            continue;
        }

        const key = `${firstName.toLowerCase()}|${lastName.toLowerCase()}`;
        if (existingKey.has(key)) {
            skipped.push(`${firstName} ${lastName}`);
            continue;
        }
        existingKey.add(key); // aynı dosyada iki Ahmet Yılmaz varsa ikincisini atla

        try {
            // a) RPC ile global-unique username (public.users + auth.users kontrolü)
            const { data: usernameData, error: rpcErr } = await supabase.rpc(
                "generate_unique_lecturer_username",
                { p_first_name: firstName, p_last_name: lastName }
            );
            if (rpcErr || !usernameData) {
                errors.push(`${firstName} ${lastName}: kullanıcı adı üretilemedi (${rpcErr?.message ?? "boş yanıt"})`);
                continue;
            }
            const username = String(usernameData);
            const email = usernameToEmail(username);
            const password = generatePassword(8);

            // b) Supabase Auth admin API — service_role, RATE-LIMIT YOK
            const { data: authData, error: authErr } = await supabase.auth.admin.createUser({
                email,
                password,
                email_confirm: true,
                user_metadata: {
                    first_name: firstName,
                    last_name: lastName,
                    role: "lecturer",
                },
            });
            if (authErr || !authData?.user) {
                errors.push(`${firstName} ${lastName}: Auth - ${authErr?.message ?? "createUser boş döndü"}`);
                continue;
            }
            const authUserId = authData.user.id;

            // c) public.users insert
            const { error: userInsertErr } = await supabase.from("users").insert({
                id: authUserId,
                org_id: orgId,
                username,
                role: "lecturer",
                must_change_password: true,
            });
            if (userInsertErr) {
                // Rollback — auth user'ı sil
                await supabase.auth.admin.deleteUser(authUserId).catch(() => {});
                errors.push(`${firstName} ${lastName}: users.insert - ${userInsertErr.message}`);
                continue;
            }

            // d) Department resolve (name → id, fallback varsa onu kullan)
            let deptId: number | null = null;
            const deptNameNorm = row.departmentName?.toString().toLowerCase().trim();
            if (deptNameNorm && deptByName.has(deptNameNorm)) {
                deptId = deptByName.get(deptNameNorm)!;
            } else if (body.fallbackDepartmentId && body.fallbackDepartmentId > 0) {
                deptId = body.fallbackDepartmentId;
            }

            // e) lecturers insert
            const { error: lecInsertErr } = await supabase.from("lecturers").insert({
                org_id: orgId,
                user_id: authUserId,
                title: row.title?.toString() ?? "",
                first_name: firstName,
                last_name: lastName,
                email: row.email?.toString() || null,
                department_id: deptId,
            });
            if (lecInsertErr) {
                // Tam rollback — users + auth
                await supabase.from("users").delete().eq("id", authUserId).catch(() => {});
                await supabase.auth.admin.deleteUser(authUserId).catch(() => {});
                errors.push(`${firstName} ${lastName}: lecturers.insert - ${lecInsertErr.message}`);
                continue;
            }

            credentials.push({ username, password, firstName, lastName });
        } catch (e) {
            const msg = (e instanceof Error) ? e.message : String(e);
            errors.push(`${firstName} ${lastName}: ${msg}`);
        }
    }

    // ── 8. Sonuç ──────────────────────────────────────────────────────────
    return new Response(
        JSON.stringify({
            imported: credentials.length,
            skipped: skipped.length,
            skippedNames: skipped,
            errors,
            credentials,
        }),
        { headers: JSON_HEADERS }
    );
});
