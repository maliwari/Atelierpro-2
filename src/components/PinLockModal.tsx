import React, { useState } from 'react';
import { Delete, Lock, ShieldCheck } from 'lucide-react';

interface PinLockGateProps {
  correctPin: string;
  onUnlock: () => void;
}

export const PinLockGate: React.FC<PinLockGateProps> = ({ correctPin, onUnlock }) => {
  const [entered, setEntered] = useState('');
  const [error, setError] = useState(false);

  const handleDigit = (d: string) => {
    setError(false);
    const next = entered + d;
    if (next.length === 4) {
      if (next === correctPin) {
        setEntered('');
        onUnlock();
      } else {
        setError(true);
        setTimeout(() => setEntered(''), 350);
      }
    } else {
      setEntered(next);
    }
  };

  const handleBackspace = () => {
    setError(false);
    setEntered((prev) => prev.slice(0, -1));
  };

  return (
    <div className="mx-auto max-w-sm rounded-3xl border border-stone-200 bg-white p-6 shadow-sm my-4">
      <div className="flex flex-col items-center text-center mb-6">
        <div className="h-12 w-12 rounded-2xl bg-[#1E3A5F]/10 text-[#1E3A5F] flex items-center justify-center mb-3">
          <Lock className="h-6 w-6" />
        </div>
        <h2 className="font-display text-xl font-semibold text-stone-900">
          Espace Patron Protégé
        </h2>
        <p className="text-sm text-stone-600 mt-1">
          Saisissez le code PIN à 4 chiffres pour consulter les finances et les salaires.
        </p>
        <p className="text-xs text-stone-500 mt-1.5 font-mono-num">
          Code par défaut : 1234
        </p>
      </div>

      {/* 4 PIN dots */}
      <div className="flex items-center justify-center gap-4 mb-6">
        {[0, 1, 2, 3].map((i) => (
          <div
            key={i}
            className={`h-4 w-4 rounded-full border transition-all ${
              error
                ? 'border-red-600 bg-red-600 scale-110'
                : i < entered.length
                ? 'border-[#1E3A5F] bg-[#1E3A5F]'
                : 'border-stone-300 bg-stone-100'
            }`}
          />
        ))}
      </div>

      {error && (
        <p className="text-center text-xs font-semibold text-red-700 mb-4">
          Code PIN incorrect. Veuillez réessayer.
        </p>
      )}

      <div className="grid grid-cols-3 gap-2.5">
        {['1', '2', '3', '4', '5', '6', '7', '8', '9'].map((d) => (
          <button
            key={d}
            type="button"
            onClick={() => handleDigit(d)}
            className="min-h-[54px] rounded-2xl border border-stone-200 bg-stone-50 font-mono-num text-xl font-semibold text-stone-900 hover:bg-stone-100 active:scale-95 transition-transform"
          >
            {d}
          </button>
        ))}
        <div className="flex items-center justify-center text-stone-400">
          <ShieldCheck className="h-5 w-5" />
        </div>
        <button
          type="button"
          onClick={() => handleDigit('0')}
          className="min-h-[54px] rounded-2xl border border-stone-200 bg-stone-50 font-mono-num text-xl font-semibold text-stone-900 hover:bg-stone-100 active:scale-95"
        >
          0
        </button>
        <button
          type="button"
          onClick={handleBackspace}
          className="min-h-[54px] rounded-2xl border border-stone-200 bg-stone-100 flex items-center justify-center text-stone-700 hover:bg-stone-200 active:scale-95"
          aria-label="Effacer"
        >
          <Delete className="h-5 w-5" />
        </button>
      </div>
    </div>
  );
};
