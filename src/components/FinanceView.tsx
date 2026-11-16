import React, { useMemo, useRef, useState } from 'react';
import {
  Download,
  FileCheck2,
  Lock,
  Plus,
  RotateCcw,
  Shield,
  Trash2,
  TrendingUp,
  Upload,
  X,
} from 'lucide-react';
import {
  AtelierDatabase,
  AtelierLicenseState,
  AtelierSettings,
  EXPENSE_CATEGORIES,
  Employee,
  Expense,
  ExpenseCategory,
} from '../types';
import { EvaluatedLicense } from '../utils/license';
import { formatDateFr, formatMoney } from '../utils/formatters';
import { exportDatabaseAsZip, importDatabaseFromFile } from '../utils/backup';
import { NumericKeypadSheet } from './NumericKeypadSheet';
import { PhotoCaptureInput, ResilientImage } from './PhotoCaptureInput';
import { PinLockGate } from './PinLockModal';
import { StaffView } from './StaffView';
import { SubscriptionView } from './SubscriptionView';

interface FinanceViewProps {
  db: AtelierDatabase;
  licenseState: AtelierLicenseState;
  evaluatedLicense: EvaluatedLicense;
  isUnlocked: boolean;
  initialSubTab?: 'dashboard' | 'depenses' | 'equipe' | 'sauvegarde' | 'abonnement';
  onUnlock: () => void;
  onLock: () => void;
  onSaveExpense: (expense: Expense) => void;
  onDeleteExpense: (expenseId: string) => void;
  onSaveEmployee: (emp: Employee) => void;
  onDeleteEmployee: (empId: string) => void;
  onUpdateSettings: (settings: AtelierSettings) => void;
  onActivateLicenseCode: (
    rawCode: string
  ) => Promise<{ ok: true; nextLicense: AtelierLicenseState } | { ok: false; error: string }>;
  onImportDatabase: (newDb: AtelierDatabase) => void;
  onResetDatabase: () => void;
  onNotify: (msg: string) => void;
}

export const FinanceView: React.FC<FinanceViewProps> = ({
  db,
  licenseState,
  evaluatedLicense,
  isUnlocked,
  initialSubTab = 'dashboard',
  onUnlock,
  onLock,
  onSaveExpense,
  onDeleteExpense,
  onSaveEmployee,
  onDeleteEmployee,
  onUpdateSettings,
  onActivateLicenseCode,
  onImportDatabase,
  onResetDatabase,
  onNotify,
}) => {
  const [subTab, setSubTab] = useState<'dashboard' | 'depenses' | 'equipe' | 'sauvegarde' | 'abonnement'>(
    initialSubTab
  );

  React.useEffect(() => {
    setSubTab(initialSubTab);
  }, [initialSubTab]);

  // Expense modal state
  const [expenseModalOpen, setExpenseModalOpen] = useState(false);
  const [expCategory, setExpCategory] = useState<ExpenseCategory>('Achat Mercerie');
  const [expAmount, setExpAmount] = useState(5000);
  const [expDate, setExpDate] = useState(new Date().toISOString().split('T')[0]);
  const [expDesc, setExpDesc] = useState('');
  const [expPhoto, setExpPhoto] = useState('');
  const [keypadOpen, setKeypadOpen] = useState(false);

  // Settings form state
  const [settingsDraft, setSettingsDraft] = useState<AtelierSettings>(db.settings);
  const [confirmReset, setConfirmReset] = useState(false);
  const importInputRef = useRef<HTMLInputElement>(null);

  React.useEffect(() => {
    setSettingsDraft(db.settings);
  }, [db.settings]);

  // Financial Metrics
  const metrics = useMemo(() => {
    const encaisse = db.orders.reduce((sum, o) => sum + o.advancePaid, 0);
    const creances = db.orders
      .filter((o) => o.status !== 'Livré')
      .reduce((sum, o) => sum + Math.max(0, o.totalPrice - o.advancePaid), 0);

    const expensesTotal = db.expenses.reduce((sum, e) => sum + e.amount, 0);

    // Estimated active payroll (fixed monthly + earned piece/commission)
    const payrollTotal = db.employees
      .filter((e) => e.active)
      .reduce((sum, emp) => {
        if (emp.payMode === 'fixe') return sum + emp.payRate;
        const done = db.orders.filter(
          (o) =>
            o.assignedEmployeeId === emp.id &&
            (o.status === 'Prêt' || o.status === 'Livré')
        );
        if (emp.payMode === 'piece') return sum + done.length * emp.payRate;
        return (
          sum +
          done.reduce((s, o) => s + Math.round((o.totalPrice * emp.payRate) / 100), 0)
        );
      }, 0);

    const totalCharges = expensesTotal + payrollTotal;
    const netProfit = encaisse - totalCharges;

    // Profitability by Model
    const modelProfitability = db.models.map((model) => {
      const modelOrders = db.orders.filter((o) => o.modelId === model.id);
      const avgSalePrice =
        modelOrders.length > 0
          ? Math.round(
              modelOrders.reduce((s, o) => s + o.totalPrice, 0) / modelOrders.length
            )
          : model.defaultPrice;
      const mercerieCost = model.recipe.reduce((s, r) => {
        const item = db.inventory.find((i) => i.id === r.itemId);
        return s + (item ? item.unitPrice * r.quantity : 0);
      }, 0);
      const avgMargin = avgSalePrice - mercerieCost;
      const marginPct = avgSalePrice > 0 ? Math.round((avgMargin / avgSalePrice) * 100) : 0;
      return {
        model,
        ordersCount: modelOrders.length,
        avgSalePrice,
        mercerieCost,
        avgMargin,
        marginPct,
      };
    });

    return {
      encaisse,
      creances,
      expensesTotal,
      payrollTotal,
      totalCharges,
      netProfit,
      modelProfitability,
    };
  }, [db.orders, db.expenses, db.employees, db.models, db.inventory]);

  if (db.settings.pinEnabled && !isUnlocked && subTab !== 'abonnement') {
    return <PinLockGate correctPin={db.settings.pinCode} onUnlock={onUnlock} />;
  }

  const handleCreateExpense = (e: React.FormEvent) => {
    e.preventDefault();
    if (expAmount <= 0 || !expDesc.trim()) return;
    const expense: Expense = {
      id: `exp-${Date.now()}`,
      category: expCategory,
      amount: expAmount,
      date: expDate,
      description: expDesc.trim(),
      receiptPhotoUrl: expPhoto || undefined,
      createdAt: new Date().toISOString().split('T')[0],
    };
    onSaveExpense(expense);
    setExpenseModalOpen(false);
    setExpDesc('');
    setExpPhoto('');
  };

  const handleImportFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      const imported = await importDatabaseFromFile(file);
      onImportDatabase(imported);
      onNotify('Sauvegarde restaurée avec succès.');
    } catch (err) {
      onNotify(
        err instanceof Error ? err.message : 'Erreur lors de l’importation de la sauvegarde.'
      );
    } finally {
      e.target.value = '';
    }
  };

  const handleSaveSettings = (e: React.FormEvent) => {
    e.preventDefault();
    if (settingsDraft.pinEnabled && !/^\d{4}$/.test(settingsDraft.pinCode)) {
      onNotify('Le code PIN doit contenir exactement 4 chiffres.');
      return;
    }
    onUpdateSettings(settingsDraft);
    onNotify('Paramètres de l’atelier enregistrés.');
  };

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="font-display text-2xl font-semibold text-stone-900">
            Finances, RH &amp; Sauvegarde
          </h1>
          <p className="text-xs text-stone-500">
            Rentabilité nette de l’atelier, dépenses et gestion des accès
          </p>
        </div>

        <div className="flex items-center gap-2">
          {db.settings.pinEnabled && (
            <button
              type="button"
              onClick={onLock}
              className="min-h-[44px] px-3.5 rounded-xl border border-stone-300 bg-white text-xs font-semibold text-stone-800 hover:bg-stone-50 inline-flex items-center gap-1.5"
            >
              <Lock className="h-4 w-4 text-[#1E3A5F]" />
              <span>Verrouiller</span>
            </button>
          )}
          <button
            type="button"
            onClick={() => setExpenseModalOpen(true)}
            className="min-h-[44px] px-4 rounded-xl bg-[#1E3A5F] text-white text-sm font-semibold inline-flex items-center gap-2 hover:bg-[#162C49]"
          >
            <Plus className="h-4 w-4" />
            <span>Nouvelle dépense</span>
          </button>
        </div>
      </div>

      {/* Sub-navigation */}
      <div className="flex items-center gap-1.5 overflow-x-auto pb-1">
        {[
          { id: 'dashboard', label: 'Tableau de bord' },
          { id: 'depenses', label: 'Dépenses' },
          { id: 'equipe', label: 'Équipe & Salaires' },
          { id: 'sauvegarde', label: 'Sauvegarde & PIN' },
          { id: 'abonnement', label: 'Abonnement' },
        ].map((t) => (
          <button
            key={t.id}
            type="button"
            onClick={() => setSubTab(t.id as typeof subTab)}
            className={`min-h-[40px] px-3.5 rounded-xl text-xs font-semibold whitespace-nowrap shrink-0 transition-colors ${
              subTab === t.id
                ? 'bg-[#1E3A5F] text-white'
                : 'bg-stone-100 text-stone-700 hover:bg-stone-200'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {subTab === 'dashboard' && (
        <div className="space-y-6">
          {/* 4 KPI Cards */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
            <div className="rounded-2xl border border-stone-200 bg-white p-4">
              <p className="text-xs font-medium text-stone-500">Chiffre d’affaires encaissé</p>
              <p className="font-mono-num text-2xl font-bold text-emerald-700 mt-1.5">
                {formatMoney(metrics.encaisse, db.settings.currency)}
              </p>
              <p className="text-xs text-stone-500 mt-1">Avances + soldes réglés</p>
            </div>

            <div className="rounded-2xl border border-stone-200 bg-white p-4">
              <p className="text-xs font-medium text-stone-500">Créances clients</p>
              <p className="font-mono-num text-2xl font-bold text-amber-700 mt-1.5">
                {formatMoney(metrics.creances, db.settings.currency)}
              </p>
              <p className="text-xs text-stone-500 mt-1">Restes à payer en cours</p>
            </div>

            <div className="rounded-2xl border border-stone-200 bg-white p-4">
              <p className="text-xs font-medium text-stone-500">Dépenses &amp; Salaires</p>
              <p className="font-mono-num text-2xl font-bold text-red-700 mt-1.5">
                {formatMoney(metrics.totalCharges, db.settings.currency)}
              </p>
              <p className="text-xs text-stone-500 mt-1 font-mono-num">
                {formatMoney(metrics.expensesTotal, db.settings.currency)} frais +{' '}
                {formatMoney(metrics.payrollTotal, db.settings.currency)} RH
              </p>
            </div>

            <div className="rounded-2xl border border-stone-200 bg-white p-4">
              <p className="text-xs font-medium text-stone-500">Bénéfice Net Réel</p>
              <p
                className={`font-mono-num text-2xl font-bold mt-1.5 ${
                  metrics.netProfit >= 0 ? 'text-[#1E3A5F]' : 'text-red-700'
                }`}
              >
                {formatMoney(metrics.netProfit, db.settings.currency)}
              </p>
              <p className="text-xs text-stone-500 mt-1">Encaissements - Charges</p>
            </div>
          </div>

          {/* Profitability by Model */}
          <section className="rounded-2xl border border-stone-200 bg-white p-4 sm:p-5 space-y-3">
            <div className="flex items-center gap-2">
              <TrendingUp className="h-5 w-5 text-[#1E3A5F]" />
              <h2 className="font-display text-lg font-semibold text-stone-900">
                Rentabilité par modèle
              </h2>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-stone-200 text-xs text-stone-500">
                    <th className="py-2.5 pr-4 font-semibold">Modèle</th>
                    <th className="py-2.5 px-3 font-semibold text-right">Cmd</th>
                    <th className="py-2.5 px-3 font-semibold text-right">Prix moyen</th>
                    <th className="py-2.5 px-3 font-semibold text-right">Mercerie</th>
                    <th className="py-2.5 pl-3 font-semibold text-right">Marge brute</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-stone-100">
                  {metrics.modelProfitability.map((row) => (
                    <tr key={row.model.id} className="hover:bg-stone-50/70">
                      <td className="py-3 pr-4">
                        <p className="font-semibold text-stone-900">{row.model.name}</p>
                        <p className="text-xs text-stone-500">{row.model.category}</p>
                      </td>
                      <td className="py-3 px-3 text-right font-mono-num text-stone-700">
                        {row.ordersCount}
                      </td>
                      <td className="py-3 px-3 text-right font-mono-num text-stone-900">
                        {formatMoney(row.avgSalePrice, db.settings.currency)}
                      </td>
                      <td className="py-3 px-3 text-right font-mono-num text-stone-600">
                        {formatMoney(row.mercerieCost, db.settings.currency)}
                      </td>
                      <td className="py-3 pl-3 text-right font-mono-num font-bold text-emerald-700">
                        {formatMoney(row.avgMargin, db.settings.currency)} ({row.marginPct}%)
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        </div>
      )}

      {subTab === 'depenses' && (
        <section className="rounded-2xl border border-stone-200 bg-white divide-y divide-stone-100">
          {db.expenses.length === 0 ? (
            <div className="p-8 text-center text-sm text-stone-500">
              Aucune dépense enregistrée.
            </div>
          ) : (
            db.expenses.map((exp) => (
              <div
                key={exp.id}
                className="p-4 flex items-center justify-between gap-3"
              >
                <div className="flex items-center gap-3 min-w-0">
                  {exp.receiptPhotoUrl && (
                    <ResilientImage
                      src={exp.receiptPhotoUrl}
                      alt={exp.description}
                      className="h-12 w-12 rounded-xl object-cover border border-stone-200 shrink-0"
                    />
                  )}
                  <div className="min-w-0">
                    <p className="text-sm font-semibold text-stone-900 truncate">
                      {exp.description}
                    </p>
                    <p className="text-xs text-stone-500 font-mono-num">
                      {exp.category} · {formatDateFr(exp.date)}
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-3 shrink-0">
                  <span className="font-mono-num text-sm font-bold text-red-700">
                    -{formatMoney(exp.amount, db.settings.currency)}
                  </span>
                  <button
                    type="button"
                    onClick={() => onDeleteExpense(exp.id)}
                    className="min-h-[40px] min-w-[40px] flex items-center justify-center rounded-xl border border-stone-200 text-stone-500 hover:text-red-700 hover:bg-red-50"
                    aria-label="Supprimer la dépense"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                </div>
              </div>
            ))
          )}
        </section>
      )}

      {subTab === 'equipe' && (
        <StaffView
          db={db}
          onSaveEmployee={onSaveEmployee}
          onDeleteEmployee={onDeleteEmployee}
        />
      )}

      {subTab === 'abonnement' && (
        <SubscriptionView
          licenseState={licenseState}
          evaluated={evaluatedLicense}
          onActivateCode={onActivateLicenseCode}
        />
      )}

      {subTab === 'sauvegarde' && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
          {/* Workshop & PIN Settings */}
          <form
            onSubmit={handleSaveSettings}
            className="rounded-2xl border border-stone-200 bg-white p-5 space-y-4"
          >
            <div className="flex items-center gap-2">
              <Shield className="h-5 w-5 text-[#1E3A5F]" />
              <h2 className="font-display text-lg font-semibold text-stone-900">
                Atelier &amp; Sécurité PIN
              </h2>
            </div>

            <div>
              <label className="block text-xs font-semibold text-stone-700 mb-1">
                Nom de l’atelier (affiché sur les reçus PDF)
              </label>
              <input
                type="text"
                value={settingsDraft.workshopName}
                onChange={(e) =>
                  setSettingsDraft({ ...settingsDraft, workshopName: e.target.value })
                }
                className="w-full min-h-[44px] rounded-xl border border-stone-300 px-3 text-sm"
              />
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-semibold text-stone-700 mb-1">
                  Téléphone atelier
                </label>
                <input
                  type="tel"
                  value={settingsDraft.workshopPhone}
                  onChange={(e) =>
                    setSettingsDraft({ ...settingsDraft, workshopPhone: e.target.value })
                  }
                  className="w-full min-h-[44px] rounded-xl border border-stone-300 px-3 text-sm font-mono-num"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-stone-700 mb-1">Devise</label>
                <select
                  value={settingsDraft.currency}
                  onChange={(e) =>
                    setSettingsDraft({
                      ...settingsDraft,
                      currency: e.target.value as AtelierSettings['currency'],
                    })
                  }
                  className="w-full min-h-[44px] rounded-xl border border-stone-300 px-3 text-sm bg-white"
                >
                  <option value="FCFA">FCFA</option>
                  <option value="€">Euro (€)</option>
                  <option value="MAD">Dirham (MAD)</option>
                </select>
              </div>
            </div>

            <div>
              <label className="block text-xs font-semibold text-stone-700 mb-1">
                Adresse de l’atelier
              </label>
              <input
                type="text"
                value={settingsDraft.workshopAddress}
                onChange={(e) =>
                  setSettingsDraft({ ...settingsDraft, workshopAddress: e.target.value })
                }
                className="w-full min-h-[44px] rounded-xl border border-stone-300 px-3 text-sm"
              />
            </div>

            <PhotoCaptureInput
              label="Logo de l’atelier (imprimé sur les reçus PDF)"
              value={settingsDraft.workshopLogoUrl}
              onChange={(url) =>
                setSettingsDraft({ ...settingsDraft, workshopLogoUrl: url || undefined })
              }
              aspectClass="aspect-[16/9]"
            />

            <div className="border-t border-stone-200 pt-4 space-y-3">
              <label className="flex items-center justify-between cursor-pointer">
                <span className="text-sm font-semibold text-stone-900">
                  Verrouiller Finances &amp; Salaires par code PIN
                </span>
                <input
                  type="checkbox"
                  checked={settingsDraft.pinEnabled}
                  onChange={(e) =>
                    setSettingsDraft({ ...settingsDraft, pinEnabled: e.target.checked })
                  }
                  className="h-5 w-5 accent-[#1E3A5F]"
                />
              </label>
              {settingsDraft.pinEnabled && (
                <div>
                  <label className="block text-xs font-semibold text-stone-700 mb-1">
                    Code PIN (4 chiffres)
                  </label>
                  <input
                    type="password"
                    inputMode="numeric"
                    maxLength={4}
                    value={settingsDraft.pinCode}
                    onChange={(e) =>
                      setSettingsDraft({
                        ...settingsDraft,
                        pinCode: e.target.value.replace(/\D/g, '').slice(0, 4),
                      })
                    }
                    className="w-36 min-h-[44px] rounded-xl border border-stone-300 px-3 text-center text-lg font-mono-num tracking-widest"
                  />
                </div>
              )}
            </div>

            <button
              type="submit"
              className="w-full min-h-[48px] rounded-xl bg-[#1E3A5F] text-white text-sm font-semibold"
            >
              Enregistrer les paramètres
            </button>
          </form>

          {/* Local Backup ZIP Export / Import */}
          <div className="rounded-2xl border border-stone-200 bg-white p-5 space-y-4">
            <div className="flex items-center gap-2">
              <FileCheck2 className="h-5 w-5 text-[#1E3A5F]" />
              <h2 className="font-display text-lg font-semibold text-stone-900">
                Sauvegarde locale (.zip)
              </h2>
            </div>
            <p className="text-sm text-stone-600 leading-relaxed">
              Exportez l’intégralité de la base locale (clients, mensurations, commandes, photos compressées, mercerie, finances) dans une archive <strong>.zip</strong> vers votre téléphone, clé USB OTG ou Google Drive.
            </p>

            <div className="space-y-3 pt-2">
              <button
                type="button"
                onClick={async () => {
                  await exportDatabaseAsZip(db);
                  onNotify('Archive .zip exportée avec succès.');
                }}
                className="w-full min-h-[48px] rounded-xl bg-[#1E3A5F] text-white text-sm font-semibold inline-flex items-center justify-center gap-2 hover:bg-[#162C49]"
              >
                <Download className="h-4 w-4" />
                <span>Exporter la sauvegarde (.zip)</span>
              </button>

              <button
                type="button"
                onClick={() => importInputRef.current?.click()}
                className="w-full min-h-[48px] rounded-xl border border-stone-300 bg-stone-50 text-stone-900 text-sm font-semibold inline-flex items-center justify-center gap-2 hover:bg-stone-100"
              >
                <Upload className="h-4 w-4" />
                <span>Importer une sauvegarde (.zip / .json)</span>
              </button>
              <input
                ref={importInputRef}
                type="file"
                accept=".zip,.json"
                onChange={handleImportFile}
                className="hidden"
              />
            </div>

            <div className="border-t border-stone-200 pt-4">
              {confirmReset ? (
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => {
                      onResetDatabase();
                      setConfirmReset(false);
                      onNotify('Données de démonstration réinitialisées.');
                    }}
                    className="flex-1 min-h-[44px] rounded-xl bg-red-700 text-white text-xs font-semibold"
                  >
                    Confirmer la réinitialisation
                  </button>
                  <button
                    type="button"
                    onClick={() => setConfirmReset(false)}
                    className="min-h-[44px] px-4 rounded-xl border border-stone-300 text-xs font-semibold text-stone-700"
                  >
                    Annuler
                  </button>
                </div>
              ) : (
                <button
                  type="button"
                  onClick={() => setConfirmReset(true)}
                  className="min-h-[44px] text-xs font-semibold text-red-700 hover:underline inline-flex items-center gap-1.5"
                >
                  <RotateCcw className="h-3.5 w-3.5" />
                  <span>Réinitialiser avec les données de démonstration</span>
                </button>
              )}
            </div>

            <div className="border-t border-stone-200 pt-4 text-xs text-stone-600 space-y-1">
              <p className="font-semibold text-stone-900">
                Code source Android natif (Kotlin &amp; Jetpack Compose)
              </p>
              <p>
                Le projet Android Studio complet (API 26+, SQLite Room, PdfDocument, Intent WhatsApp, compression WebP/JPEG 70 %) est disponible dans le dossier <code className="font-mono-num text-stone-800">/android/</code> du dépôt pour compiler l’APK directement.
              </p>
            </div>
          </div>
        </div>
      )}

      {/* New Expense Modal */}
      {expenseModalOpen && (
        <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/50 backdrop-blur-xs">
          <div className="w-full max-w-md max-h-[90vh] flex flex-col rounded-t-3xl sm:rounded-2xl bg-white shadow-2xl overflow-hidden">
            <div className="flex items-center justify-between px-5 py-4 border-b border-stone-200">
              <h2 className="font-display text-xl font-semibold text-stone-900">
                Saisir une dépense
              </h2>
              <button
                type="button"
                onClick={() => setExpenseModalOpen(false)}
                className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-xl text-stone-500"
              >
                <X className="h-5 w-5" />
              </button>
            </div>
            <form onSubmit={handleCreateExpense} className="overflow-y-auto p-5 space-y-4">
              <div>
                <label className="block text-xs font-semibold text-stone-700 mb-1">
                  Catégorie
                </label>
                <select
                  value={expCategory}
                  onChange={(e) => setExpCategory(e.target.value as ExpenseCategory)}
                  className="w-full min-h-[44px] rounded-xl border border-stone-300 px-3 text-sm bg-white"
                >
                  {EXPENSE_CATEGORIES.map((c) => (
                    <option key={c} value={c}>
                      {c}
                    </option>
                  ))}
                </select>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <button
                  type="button"
                  onClick={() => setKeypadOpen(true)}
                  className="min-h-[60px] rounded-xl border border-stone-200 bg-stone-50 px-3 py-1.5 text-left"
                >
                  <span className="block text-[11px] text-stone-500">Montant</span>
                  <span className="font-mono-num text-base font-bold text-stone-900">
                    {formatMoney(expAmount, db.settings.currency)}
                  </span>
                </button>
                <div>
                  <label className="block text-xs font-semibold text-stone-700 mb-1">Date</label>
                  <input
                    type="date"
                    value={expDate}
                    onChange={(e) => setExpDate(e.target.value)}
                    className="w-full min-h-[44px] rounded-xl border border-stone-300 px-3 text-sm font-mono-num"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-stone-700 mb-1">
                  Libellé / Description
                </label>
                <input
                  type="text"
                  required
                  value={expDesc}
                  onChange={(e) => setExpDesc(e.target.value)}
                  placeholder="Ex: Loyer mois de novembre"
                  className="w-full min-h-[44px] rounded-xl border border-stone-300 px-3 text-sm"
                />
              </div>

              <PhotoCaptureInput
                label="Justificatif photo (optionnel)"
                value={expPhoto}
                onChange={setExpPhoto}
              />

              <button
                type="submit"
                className="w-full min-h-[48px] rounded-xl bg-[#1E3A5F] text-white text-sm font-semibold"
              >
                Enregistrer la dépense
              </button>
            </form>
          </div>
        </div>
      )}

      <NumericKeypadSheet
        isOpen={keypadOpen}
        title="Montant de la dépense"
        unit={db.settings.currency}
        initialValue={expAmount}
        allowDecimal={db.settings.currency === '€'}
        step={db.settings.currency === 'FCFA' ? 500 : 5}
        onConfirm={setExpAmount}
        onClose={() => setKeypadOpen(false)}
      />
    </div>
  );
};
