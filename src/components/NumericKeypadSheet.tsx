import React, { useState } from 'react';
import { Check, Delete, Minus, Plus, X } from 'lucide-react';

interface NumericKeypadSheetProps {
  isOpen: boolean;
  title: string;
  unit?: string;
  initialValue: number;
  allowDecimal?: boolean;
  step?: number;
  onConfirm: (val: number) => void;
  onClose: () => void;
}

export const NumericKeypadSheet: React.FC<NumericKeypadSheetProps> = ({
  isOpen,
  title,
  unit = '',
  initialValue,
  allowDecimal = true,
  step = 1,
  onConfirm,
  onClose,
}) => {
  const [raw, setRaw] = useState<string>(initialValue ? String(initialValue) : '0');

  React.useEffect(() => {
    if (isOpen) {
      setRaw(initialValue ? String(initialValue) : '0');
    }
  }, [isOpen, initialValue]);

  if (!isOpen) return null;

  const numericVal = parseFloat(raw) || 0;

  const appendDigit = (digit: string) => {
    setRaw((prev) => {
      if (digit === '.' && (!allowDecimal || prev.includes('.'))) return prev;
      if (prev === '0' && digit !== '.') return digit;
      if (prev.length >= 10) return prev;
      return prev + digit;
    });
  };

  const backspace = () => {
    setRaw((prev) => {
      if (prev.length <= 1) return '0';
      return prev.slice(0, -1);
    });
  };

  const adjust = (delta: number) => {
    const next = Math.max(0, Math.round((numericVal + delta) * 10) / 10);
    setRaw(String(next));
  };

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/50 backdrop-blur-xs">
      <div className="w-full max-w-sm rounded-t-3xl sm:rounded-2xl bg-white p-5 shadow-2xl border border-stone-200">
        <div className="w-10 h-1.5 bg-stone-300 rounded-full mx-auto mb-4 sm:hidden" />
        <div className="flex items-center justify-between mb-3">
          <div>
            <p className="text-xs font-medium text-stone-500">Pavé numérique tactile</p>
            <h3 className="text-base font-semibold text-stone-900">{title}</h3>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-xl text-stone-500 hover:bg-stone-100"
            aria-label="Annuler"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Display readout + quick +/- */}
        <div className="flex items-center justify-between gap-2 rounded-2xl bg-stone-100 px-3 py-3 mb-4">
          <button
            type="button"
            onClick={() => adjust(-step)}
            className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-xl bg-white border border-stone-200 text-stone-800 active:scale-95"
            aria-label="Diminuer"
          >
            <Minus className="h-5 w-5" />
          </button>
          <div className="text-center flex-1">
            <span className="font-mono-num text-3xl font-bold text-stone-900">{raw}</span>
            {unit && <span className="ml-1.5 text-sm font-medium text-stone-500">{unit}</span>}
          </div>
          <button
            type="button"
            onClick={() => adjust(step)}
            className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-xl bg-white border border-stone-200 text-stone-800 active:scale-95"
            aria-label="Augmenter"
          >
            <Plus className="h-5 w-5" />
          </button>
        </div>

        {/* Tactile 3x4 keypad */}
        <div className="grid grid-cols-3 gap-2 mb-4">
          {['1', '2', '3', '4', '5', '6', '7', '8', '9'].map((d) => (
            <button
              key={d}
              type="button"
              onClick={() => appendDigit(d)}
              className="min-h-[52px] rounded-xl bg-stone-50 border border-stone-200 font-mono-num text-xl font-semibold text-stone-900 hover:bg-stone-100 active:scale-95 transition-transform"
            >
              {d}
            </button>
          ))}
          <button
            type="button"
            onClick={() => (allowDecimal ? appendDigit('.') : setRaw('0'))}
            className="min-h-[52px] rounded-xl bg-stone-50 border border-stone-200 font-mono-num text-xl font-semibold text-stone-700 hover:bg-stone-100 active:scale-95"
          >
            {allowDecimal ? '.' : 'C'}
          </button>
          <button
            type="button"
            onClick={() => appendDigit('0')}
            className="min-h-[52px] rounded-xl bg-stone-50 border border-stone-200 font-mono-num text-xl font-semibold text-stone-900 hover:bg-stone-100 active:scale-95"
          >
            0
          </button>
          <button
            type="button"
            onClick={backspace}
            className="min-h-[52px] rounded-xl bg-stone-100 border border-stone-200 flex items-center justify-center text-stone-700 hover:bg-stone-200 active:scale-95"
            aria-label="Effacer"
          >
            <Delete className="h-5 w-5" />
          </button>
        </div>

        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => setRaw('0')}
            className="min-h-[48px] px-4 rounded-xl border border-stone-200 text-sm font-semibold text-stone-700 hover:bg-stone-50"
          >
            Effacer
          </button>
          <button
            type="button"
            onClick={() => {
              onConfirm(numericVal);
              onClose();
            }}
            className="flex-1 min-h-[48px] flex items-center justify-center gap-2 rounded-xl bg-[#1E3A5F] text-sm font-semibold text-white hover:bg-[#162C49] active:scale-[0.98]"
          >
            <Check className="h-4 w-4" />
            <span>Valider</span>
          </button>
        </div>
      </div>
    </div>
  );
};
