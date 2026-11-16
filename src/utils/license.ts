import { AtelierLicenseState, LicenseStatusLabel, UsedActivationCode } from '../types';

export const WHATSAPP_ADMIN_PHONE = '22365972000';
export const TRIAL_DAYS = 14;
const LICENSE_SECRET = 'AtelierPro_M@li_2026!HMAC#Key_v1';
const CODE_ALPHABET = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';
const CLOCK_TOLERANCE_MS = 60_000;

export function generateWorkshopCode(): string {
  const bytes = new Uint8Array(4);
  if (typeof crypto !== 'undefined' && crypto.getRandomValues) {
    crypto.getRandomValues(bytes);
  } else {
    for (let i = 0; i < 4; i++) bytes[i] = Math.floor(Math.random() * 256);
  }
  let suffix = '';
  for (let i = 0; i < 4; i++) {
    suffix += CODE_ALPHABET[bytes[i] % CODE_ALPHABET.length];
  }
  return `ATEL-${suffix}`;
}

export function createInitialLicenseState(now: Date = new Date()): AtelierLicenseState {
  const exp = new Date(now.getTime() + TRIAL_DAYS * 86_400_000);
  return {
    workshopCode: generateWorkshopCode(),
    planType: 'trial',
    expirationDate: exp.toISOString().split('T')[0],
    lastKnownEpochMs: now.getTime(),
    lastKnownIso: now.toISOString(),
    isSynced: 0,
    usedCodes: [],
  };
}

export function buildWhatsAppActivationUrl(workshopCode: string): string {
  const message = `Bonjour, je souhaite activer AtelierPro. ID : ${workshopCode}`;
  return `https://wa.me/${WHATSAPP_ADMIN_PHONE}?text=${encodeURIComponent(message)}`;
}

export function getCurrentYearMonth(now: Date = new Date()): string {
  const y = now.getUTCFullYear();
  const m = String(now.getUTCMonth() + 1).padStart(2, '0');
  return `${y}-${m}`;
}

export async function computeLicenseSignature(
  workshopCode: string,
  durationCode: string,
  yearMonth: string = getCurrentYearMonth()
): Promise<string> {
  const payload = `${workshopCode.trim().toUpperCase()}-${durationCode.trim().toUpperCase()}-${yearMonth}`;
  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey(
    'raw',
    encoder.encode(LICENSE_SECRET),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign']
  );
  const sigBuffer = await crypto.subtle.sign('HMAC', key, encoder.encode(payload));
  const hex = Array.from(new Uint8Array(sigBuffer))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
    .toUpperCase();
  return hex.slice(0, 6);
}

export interface EvaluatedLicense {
  workshopCode: string;
  status: LicenseStatusLabel;
  expirationDate: string;
  remainingDays: number;
  isClockTampered: boolean;
  lastKnownIso: string;
  isSynced: 0 | 1;
}

export function checkAndAdvanceClock(
  license: AtelierLicenseState,
  now: Date = new Date()
): { updated: AtelierLicenseState; isClockTampered: boolean } {
  const nowMs = now.getTime();
  if (nowMs + CLOCK_TOLERANCE_MS < license.lastKnownEpochMs) {
    return { updated: license, isClockTampered: true };
  }
  if (nowMs > license.lastKnownEpochMs) {
    return {
      updated: {
        ...license,
        lastKnownEpochMs: nowMs,
        lastKnownIso: now.toISOString(),
      },
      isClockTampered: false,
    };
  }
  return { updated: license, isClockTampered: false };
}

export function evaluateLicense(
  license: AtelierLicenseState,
  isClockTampered: boolean,
  now: Date = new Date()
): EvaluatedLicense {
  const todayIso = now.toISOString().split('T')[0];
  const todayMs = Date.parse(`${todayIso}T00:00:00Z`);
  const expMs = Date.parse(`${license.expirationDate}T00:00:00Z`);
  const diffDays = Number.isFinite(expMs) ? Math.floor((expMs - todayMs) / 86_400_000) : -1;

  let status: LicenseStatusLabel;
  if (todayIso > license.expirationDate) {
    status = 'Expiré';
  } else if (license.planType === 'trial') {
    status = 'Essai gratuit';
  } else {
    status = 'Actif';
  }

  return {
    workshopCode: license.workshopCode,
    status,
    expirationDate: license.expirationDate,
    remainingDays: Math.max(0, diffDays),
    isClockTampered,
    lastKnownIso: license.lastKnownIso,
    isSynced: license.isSynced,
  };
}

export async function activateLicenseCode(
  license: AtelierLicenseState,
  rawCode: string,
  now: Date = new Date()
): Promise<{ ok: true; nextLicense: AtelierLicenseState } | { ok: false; error: string }> {
  if (now.getTime() + CLOCK_TOLERANCE_MS < license.lastKnownEpochMs) {
    return {
      ok: false,
      error: "Horloge incorrecte. Réglez la date et l'heure du téléphone avant d'activer.",
    };
  }

  const normalized = rawCode.trim().toUpperCase();
  const match = /^(M1|M3|A1)-([A-Z0-9]{6})$/.exec(normalized);
  if (!match) {
    return {
      ok: false,
      error: 'Format invalide. Attendu : M1-XXXXXX (30j), M3-XXXXXX (90j) ou A1-XXXXXX (365j).',
    };
  }

  const durationCode = match[1] as 'M1' | 'M3' | 'A1';
  const providedSig = match[2];
  const daysAdded = durationCode === 'A1' ? 365 : durationCode === 'M3' ? 90 : 30;

  if (license.usedCodes.some((c) => c.code === normalized)) {
    return { ok: false, error: "Ce code d'activation a déjà été utilisé." };
  }

  const expectedSig = await computeLicenseSignature(
    license.workshopCode,
    durationCode,
    getCurrentYearMonth(now)
  );
  if (providedSig !== expectedSig) {
    return { ok: false, error: "Code d'activation invalide ou expiré pour ce mois." };
  }

  const todayIso = now.toISOString().split('T')[0];
  const baseIso = license.expirationDate > todayIso ? license.expirationDate : todayIso;
  const baseDate = new Date(`${baseIso}T00:00:00Z`);
  baseDate.setUTCDate(baseDate.getUTCDate() + daysAdded);
  const newExpIso = baseDate.toISOString().split('T')[0];

  const usedEntry: UsedActivationCode = {
    code: normalized,
    durationCode,
    daysAdded,
    usedAt: now.toISOString(),
    isSynced: 0,
  };

  return {
    ok: true,
    nextLicense: {
      ...license,
      planType: 'pro',
      expirationDate: newExpIso,
      lastKnownEpochMs: Math.max(license.lastKnownEpochMs, now.getTime()),
      lastKnownIso: now.toISOString(),
      isSynced: 0,
      usedCodes: [usedEntry, ...license.usedCodes],
    },
  };
}
