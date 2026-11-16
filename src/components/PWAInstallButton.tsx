import React, { useState } from 'react';
import { Download, Smartphone, WifiOff, X } from 'lucide-react';
import { usePWAInstall } from '../hooks/usePWAInstall';
import { useOnlineStatus } from '../hooks/useOnlineStatus';

export const PWAInstallButton: React.FC<{ compact?: boolean }> = ({ compact = false }) => {
  const { isInstallable, isInstalled, isIOS, install } = usePWAInstall();
  const [showIOSGuide, setShowIOSGuide] = useState(false);

  if (isInstalled) {
    return null;
  }

  if (isInstallable) {
    return (
      <button
        type="button"
        onClick={install}
        className="inline-flex min-h-[44px] items-center justify-center gap-2 rounded-xl bg-[#1E3A5F] px-3.5 py-2 text-xs font-semibold text-white transition-colors hover:bg-[#162C49] active:scale-[0.98] whitespace-nowrap shrink-0"
      >
        <Download className="h-4 w-4 shrink-0" />
        <span>{compact ? 'Installer' : 'Installer l’app'}</span>
      </button>
    );
  }

  if (isIOS) {
    return (
      <>
        <button
          type="button"
          onClick={() => setShowIOSGuide(true)}
          className="inline-flex min-h-[44px] items-center justify-center gap-2 rounded-xl border border-stone-300 bg-white px-3 py-2 text-xs font-semibold text-stone-800 transition-colors hover:bg-stone-50 whitespace-nowrap shrink-0"
        >
          <Smartphone className="h-4 w-4 shrink-0 text-[#1E3A5F]" />
          <span>Installer</span>
        </button>

        {showIOSGuide && (
          <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/50 p-0 sm:p-4 backdrop-blur-xs">
            <div className="w-full max-w-sm rounded-t-3xl sm:rounded-2xl bg-white p-6 shadow-xl">
              <div className="flex items-center justify-between mb-3">
                <h3 className="font-display text-lg font-semibold text-stone-900">
                  Installer sur iPhone / iPad
                </h3>
                <button
                  type="button"
                  onClick={() => setShowIOSGuide(false)}
                  className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-xl text-stone-500 hover:bg-stone-100"
                  aria-label="Fermer"
                >
                  <X className="h-5 w-5" />
                </button>
              </div>
              <ol className="space-y-2.5 text-sm text-stone-700 leading-relaxed">
                <li>1. Appuyez sur le bouton <strong>Partager</strong> dans la barre Safari.</li>
                <li>2. Faites défiler et sélectionnez <strong>Sur l’écran d’accueil</strong>.</li>
                <li>3. Confirmez pour utiliser AtelierPro hors-ligne.</li>
              </ol>
              <button
                type="button"
                onClick={() => setShowIOSGuide(false)}
                className="mt-5 w-full min-h-[48px] rounded-xl bg-[#1E3A5F] text-sm font-semibold text-white hover:bg-[#162C49]"
              >
                Compris
              </button>
            </div>
          </div>
        )}
      </>
    );
  }

  return null;
};

export const OfflineIndicator: React.FC = () => {
  const isOnline = useOnlineStatus();
  if (isOnline) return null;

  return (
    <div
      role="status"
      className="fixed bottom-20 lg:bottom-5 left-4 z-40 flex items-center gap-2 rounded-xl border border-amber-300 bg-amber-50 px-3.5 py-2 text-xs font-semibold text-amber-900 shadow-md"
    >
      <WifiOff className="h-4 w-4 text-amber-700 shrink-0" />
      <span>Mode hors-ligne actif — Données locales enregistrées</span>
    </div>
  );
};
