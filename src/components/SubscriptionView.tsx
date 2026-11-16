import React, { useState } from 'react';
import {
  Check,
  Clock,
  Copy,
  KeyRound,
  Lock,
  MessageCircle,
  RefreshCw,
  ShieldCheck,
} from 'lucide-react';
import { AtelierLicenseState } from '../types';
import {
  buildWhatsAppActivationUrl,
  EvaluatedLicense,
} from '../utils/license';
import { formatDateFr } from '../utils/formatters';

interface ClockTamperViewProps {
  license: EvaluatedLicense;
  onRecheckClock: () => void;
}

export const ClockTamperView: React.FC<ClockTamperViewProps> = ({
  license,
  onRecheckClock,
}) => {
  return (
    <div className="min-h-screen bg-[#FAF9F6] flex items-center justify-center p-6">
      <div className="max-w-md w-full rounded-2xl border border-stone-200 bg-white p-6 text-center space-y-4">
        <div className="mx-auto h-14 w-14 rounded-2xl bg-red-50 text-red-700 flex items-center justify-center">
          <Clock className="h-7 w-7" />
        </div>
        <h1 className="font-display text-2xl font-semibold text-stone-900">
          Date du téléphone incorrecte
        </h1>
        <p className="text-sm text-stone-600 leading-relaxed">
          La date système actuelle est antérieure à la dernière ouverture connue (
          <span className="font-mono-num font-semibold text-stone-800">
            {license.lastKnownIso.slice(0, 16).replace('T', ' ')} UTC
          </span>
          ). Veuillez régler la date et l’heure de l’appareil pour débloquer AtelierPro.
        </p>
        <button
          type="button"
          onClick={onRecheckClock}
          className="w-full min-h-[48px] rounded-xl bg-[#1E3A5F] text-white text-sm font-semibold inline-flex items-center justify-center gap-2 hover:bg-[#162C49]"
        >
          <RefreshCw className="h-4 w-4" />
          <span>Vérifier à nouveau</span>
        </button>
      </div>
    </div>
  );
};

interface SubscriptionViewProps {
  licenseState: AtelierLicenseState;
  evaluated: EvaluatedLicense;
  isBlocking?: boolean;
  onActivateCode: (
    rawCode: string
  ) => Promise<{ ok: true; nextLicense: AtelierLicenseState } | { ok: false; error: string }>;
}

export const SubscriptionView: React.FC<SubscriptionViewProps> = ({
  licenseState,
  evaluated,
  isBlocking = false,
  onActivateCode,
}) => {
  const [codeInput, setCodeInput] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const whatsappUrl = buildWhatsAppActivationUrl(evaluated.workshopCode);

  const statusColorClass =
    evaluated.status === 'Actif'
      ? 'text-emerald-700'
      : evaluated.status === 'Essai gratuit'
      ? 'text-amber-700'
      : 'text-red-700';

  const handleCopyId = async () => {
    try {
      await navigator.clipboard.writeText(evaluated.workshopCode);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      setCopied(false);
    }
  };

  const handleSubmitCode = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!codeInput.trim()) return;
    setErrorMsg(null);
    setSuccessMsg(null);
    setSubmitting(true);
    const res = await onActivateCode(codeInput);
    setSubmitting(false);
    if (res.ok) {
      setCodeInput('');
      setSuccessMsg(
        `Licence activée jusqu’au ${formatDateFr(res.nextLicense.expirationDate)}.`
      );
    } else {
      setErrorMsg(res.error);
    }
  };

  return (
    <div className={isBlocking ? 'min-h-screen bg-[#FAF9F6] py-8 px-4' : 'space-y-5'}>
      <div className={isBlocking ? 'max-w-2xl mx-auto space-y-5' : 'space-y-5'}>
        {isBlocking && (
          <div className="space-y-1">
            <h1 className="font-display text-2xl font-semibold text-stone-900">
              Abonnement AtelierPro expiré
            </h1>
            <p className="text-sm text-stone-600">
              Votre accès est temporairement verrouillé. Commandez ou saisissez un code d’activation pour débloquer l’atelier.
            </p>
          </div>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
          {/* Identifiant & Statut */}
          <section className="rounded-2xl border border-stone-200 bg-white p-5 space-y-4">
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="text-xs font-medium text-stone-500">ID unique de l’atelier</p>
                <p className="font-mono-num text-2xl font-bold text-[#1E3A5F] mt-0.5">
                  {evaluated.workshopCode}
                </p>
              </div>
              <button
                type="button"
                onClick={handleCopyId}
                className="min-h-[40px] px-3 rounded-xl border border-stone-200 text-xs font-semibold text-stone-700 hover:bg-stone-50 inline-flex items-center gap-1.5"
              >
                {copied ? <Check className="h-3.5 w-3.5 text-emerald-700" /> : <Copy className="h-3.5 w-3.5" />}
                <span>{copied ? 'Copié' : 'Copier'}</span>
              </button>
            </div>

            <div className="border-t border-stone-200 pt-4 flex items-center justify-between gap-4">
              <div className="flex items-center gap-2.5">
                {evaluated.status === 'Expiré' ? (
                  <Lock className="h-5 w-5 text-red-700 shrink-0" />
                ) : (
                  <ShieldCheck className={`h-5 w-5 shrink-0 ${statusColorClass}`} />
                )}
                <div>
                  <p className="text-xs text-stone-500">Statut actuel</p>
                  <p className={`text-base font-semibold ${statusColorClass}`}>
                    {evaluated.status}
                  </p>
                </div>
              </div>

              <div className="text-right">
                <p className="text-xs text-stone-500">Date d’expiration</p>
                <p className="font-mono-num text-sm font-semibold text-stone-900">
                  {formatDateFr(evaluated.expirationDate)}
                </p>
                <p className={`text-xs font-mono-num ${statusColorClass}`}>
                  {evaluated.status === 'Expiré'
                    ? 'Expiré'
                    : `${evaluated.remainingDays} jour(s) restant(s)`}
                </p>
              </div>
            </div>

            <div className="border-t border-stone-200 pt-4 space-y-2.5">
              <p className="text-xs text-stone-600 leading-relaxed">
                Envoyez votre identifiant sur WhatsApp pour obtenir un code mensuel (<strong>M1</strong> : 30 jours) ou annuel (<strong>A1</strong> : 365 jours).
              </p>
              <a
                href={whatsappUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="w-full min-h-[48px] rounded-xl bg-emerald-700 text-white text-sm font-semibold inline-flex items-center justify-center gap-2 hover:bg-emerald-800 transition-colors"
              >
                <MessageCircle className="h-4 w-4" />
                <span>Payer / Activer sur WhatsApp</span>
              </a>
            </div>
          </section>

          {/* Activation manuelle hors-ligne */}
          <section className="rounded-2xl border border-stone-200 bg-white p-5 space-y-4">
            <div className="flex items-center gap-2">
              <KeyRound className="h-5 w-5 text-[#1E3A5F]" />
              <h2 className="font-display text-lg font-semibold text-stone-900">
                Activation hors-ligne
              </h2>
            </div>

            <form onSubmit={handleSubmitCode} className="space-y-3">
              <div>
                <label className="block text-xs font-semibold text-stone-700 mb-1">
                  Entrer un code d’activation
                </label>
                <input
                  type="text"
                  value={codeInput}
                  onChange={(e) => {
                    setCodeInput(e.target.value.toUpperCase());
                    setErrorMsg(null);
                    setSuccessMsg(null);
                  }}
                  placeholder="Ex : M1-8F3A1B ou A1-4C9E2D"
                  className="w-full min-h-[46px] rounded-xl border border-stone-300 px-3 text-sm font-mono-num uppercase tracking-wider"
                />
              </div>

              {errorMsg && (
                <p className="text-xs font-medium text-red-700">{errorMsg}</p>
              )}
              {successMsg && (
                <p className="text-xs font-semibold text-emerald-700">{successMsg}</p>
              )}

              <button
                type="submit"
                disabled={submitting || !codeInput.trim()}
                className="w-full min-h-[48px] rounded-xl bg-[#1E3A5F] text-white text-sm font-semibold disabled:opacity-50 hover:bg-[#162C49]"
              >
                {submitting ? 'Vérification…' : 'Valider'}
              </button>
            </form>

            <div className="border-t border-stone-200 pt-3 space-y-2">
              <div className="flex items-center justify-between text-xs text-stone-500">
                <span>Synchronisation licence MySQL</span>
                <span className="font-mono-num font-semibold text-stone-700">
                  {licenseState.isSynced === 1 ? 'Synchronisé' : 'En attente (is_synced = 0)'}
                </span>
              </div>
              {licenseState.usedCodes.length > 0 && (
                <div className="space-y-1.5 pt-1">
                  <p className="text-xs font-semibold text-stone-700">
                    Codes déjà utilisés ({licenseState.usedCodes.length})
                  </p>
                  <div className="divide-y divide-stone-100 border border-stone-200 rounded-xl">
                    {licenseState.usedCodes.slice(0, 4).map((c) => (
                      <div
                        key={c.code}
                        className="px-3 py-2 flex items-center justify-between text-xs"
                      >
                        <span className="font-mono-num font-semibold text-stone-900">
                          {c.code}
                        </span>
                        <span className="text-stone-500 font-mono-num">
                          +{c.daysAdded} j · {formatDateFr(c.usedAt.slice(0, 10))}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </section>
        </div>
      </div>
    </div>
  );
};
