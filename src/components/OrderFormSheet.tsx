import React, { useEffect, useMemo, useState } from 'react';
import { Check, Plus, Ruler, UserPlus, X } from 'lucide-react';
import {
  AtelierDatabase,
  Client,
  GarmentModel,
  Order,
  STANDARD_MEASUREMENTS,
} from '../types';
import { formatMoney } from '../utils/formatters';
import { PhotoCaptureInput, ResilientImage } from './PhotoCaptureInput';
import { NumericKeypadSheet } from './NumericKeypadSheet';

interface OrderFormSheetProps {
  isOpen: boolean;
  db: AtelierDatabase;
  initialOrder?: Order | null;
  onSaveOrder: (order: Order, updatedClientMeasurements?: Record<string, number>) => void;
  onQuickAddClient: (client: Client) => void;
  onClose: () => void;
}

function addDays(baseIso: string, days: number): string {
  const [y, m, d] = baseIso.split('-').map(Number);
  const dt = new Date(y, m - 1, d);
  dt.setDate(dt.getDate() + days);
  return dt.toISOString().split('T')[0];
}

export const OrderFormSheet: React.FC<OrderFormSheetProps> = ({
  isOpen,
  db,
  initialOrder,
  onSaveOrder,
  onQuickAddClient,
  onClose,
}) => {
  const todayISO = new Date().toISOString().split('T')[0];

  const [clientId, setClientId] = useState<string>('');
  const [modelId, setModelId] = useState<string>('');
  const [fabricPhotoUrl, setFabricPhotoUrl] = useState<string>('');
  const [fabricNotes, setFabricNotes] = useState<string>('');
  const [measurements, setMeasurements] = useState<Record<string, number>>({});
  const [prefilledFromClient, setPrefilledFromClient] = useState(false);
  const [orderDate, setOrderDate] = useState<string>(todayISO);
  const [fittingDate, setFittingDate] = useState<string>('');
  const [deliveryDate, setDeliveryDate] = useState<string>(todayISO);
  const [assignedEmployeeId, setAssignedEmployeeId] = useState<string>('');
  const [totalPrice, setTotalPrice] = useState<number>(0);
  const [advancePaid, setAdvancePaid] = useState<number>(0);
  const [notes, setNotes] = useState<string>('');

  // Quick add client inline state
  const [showQuickClient, setShowQuickClient] = useState(false);
  const [newClientName, setNewClientName] = useState('');
  const [newClientPhone, setNewClientPhone] = useState('+223');
  const [newClientAddress, setNewClientAddress] = useState('');

  // Tactile numeric keypad state
  const [keypadConfig, setKeypadConfig] = useState<{
    open: boolean;
    title: string;
    unit: string;
    value: number;
    allowDecimal: boolean;
    step: number;
    onApply: (v: number) => void;
  }>({
    open: false,
    title: '',
    unit: '',
    value: 0,
    allowDecimal: true,
    step: 1,
    onApply: () => {},
  });

  const selectedModel: GarmentModel | undefined = useMemo(
    () => db.models.find((m) => m.id === modelId),
    [db.models, modelId]
  );

  const selectedClient: Client | undefined = useMemo(
    () => db.clients.find((c) => c.id === clientId),
    [db.clients, clientId]
  );

  useEffect(() => {
    if (!isOpen) return;
    if (initialOrder) {
      setClientId(initialOrder.clientId);
      setModelId(initialOrder.modelId);
      setFabricPhotoUrl(initialOrder.fabricPhotoUrl || '');
      setFabricNotes(initialOrder.fabricNotes || '');
      setMeasurements({ ...initialOrder.measurements });
      setOrderDate(initialOrder.orderDate);
      setFittingDate(initialOrder.fittingDate || '');
      setDeliveryDate(initialOrder.deliveryDate);
      setAssignedEmployeeId(initialOrder.assignedEmployeeId || '');
      setTotalPrice(initialOrder.totalPrice);
      setAdvancePaid(initialOrder.advancePaid);
      setNotes(initialOrder.notes || '');
      setPrefilledFromClient(false);
    } else {
      const defaultModel = db.models[0];
      const defaultClient = db.clients[0];
      setClientId(defaultClient?.id || '');
      setModelId(defaultModel?.id || '');
      setFabricPhotoUrl('');
      setFabricNotes('');
      setOrderDate(todayISO);
      setDeliveryDate(addDays(todayISO, defaultModel?.estimatedDays || 5));
      setFittingDate(addDays(todayISO, Math.max(1, (defaultModel?.estimatedDays || 5) - 2)));
      setAssignedEmployeeId(db.employees[0]?.id || '');
      setTotalPrice(defaultModel?.defaultPrice || 0);
      setAdvancePaid(0);
      setNotes('');
    }
  }, [isOpen, initialOrder, db.models, db.clients, db.employees, todayISO]);

  // Dynamically sync required measurements & pre-fill from client history when client/model changes (for new orders)
  useEffect(() => {
    if (!isOpen || initialOrder || !selectedModel) return;
    const nextMeas: Record<string, number> = {};
    let hadSaved = false;
    selectedModel.measurementKeys.forEach((key) => {
      const savedVal = selectedClient?.latestMeasurements?.[key];
      if (typeof savedVal === 'number' && savedVal > 0) {
        nextMeas[key] = savedVal;
        hadSaved = true;
      } else {
        nextMeas[key] = measurements[key] || 0;
      }
    });
    setMeasurements(nextMeas);
    setPrefilledFromClient(hadSaved);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clientId, modelId, isOpen]);

  const handleModelChange = (nextModelId: string) => {
    setModelId(nextModelId);
    const mod = db.models.find((m) => m.id === nextModelId);
    if (mod && !initialOrder) {
      setTotalPrice(mod.defaultPrice);
      setDeliveryDate(addDays(orderDate, mod.estimatedDays));
      setFittingDate(addDays(orderDate, Math.max(1, mod.estimatedDays - 2)));
    }
  };

  const handleCreateQuickClient = () => {
    if (!newClientName.trim() || !newClientPhone.trim()) return;
    const created: Client = {
      id: `cli-${Date.now()}`,
      fullName: newClientName.trim(),
      phone: newClientPhone.trim(),
      address: newClientAddress.trim() || 'Adresse non renseignée',
      latestMeasurements: {},
      measurementHistory: [],
      createdAt: todayISO,
    };
    onQuickAddClient(created);
    setClientId(created.id);
    setShowQuickClient(false);
    setNewClientName('');
    setNewClientPhone('+223');
    setNewClientAddress('');
  };

  if (!isOpen) return null;

  const balanceDue = Math.max(0, totalPrice - advancePaid);

  const requiredMeasurementDefs = (selectedModel?.measurementKeys || []).map((key) => {
    const std = STANDARD_MEASUREMENTS.find((s) => s.key === key);
    if (std) return std;
    const custom = selectedModel?.customMeasurements?.find((c) => c.key === key);
    return custom || { key, label: key, unit: 'cm' };
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!clientId || !modelId) return;

    const orderNumber = String(db.orders.length + 45).padStart(3, '0');
    const order: Order = {
      id: initialOrder?.id || `ord-${Date.now()}`,
      reference: initialOrder?.reference || `CMD-2026-${orderNumber}`,
      clientId,
      modelId,
      fabricPhotoUrl: fabricPhotoUrl || undefined,
      fabricNotes: fabricNotes.trim() || undefined,
      measurements,
      status: initialOrder?.status || 'En attente',
      stockDeducted: initialOrder?.stockDeducted || false,
      assignedEmployeeId: assignedEmployeeId || undefined,
      orderDate,
      fittingDate: fittingDate || undefined,
      deliveryDate,
      totalPrice,
      advancePaid,
      notes: notes.trim() || undefined,
      createdAt: initialOrder?.createdAt || todayISO,
    };
    onSaveOrder(order, measurements);
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/50 backdrop-blur-xs">
      <div className="w-full max-w-2xl max-h-[92vh] flex flex-col rounded-t-3xl sm:rounded-2xl bg-white shadow-2xl overflow-hidden">
        {/* Sheet Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-stone-200 shrink-0">
          <div>
            <h2 className="font-display text-xl font-semibold text-stone-900">
              {initialOrder ? `Modifier ${initialOrder.reference}` : 'Nouvelle commande'}
            </h2>
            <p className="text-xs text-stone-500">
              Mensurations dynamiques selon le modèle sélectionné
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-xl text-stone-500 hover:bg-stone-100"
            aria-label="Fermer"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Scrollable Body */}
        <form onSubmit={handleSubmit} className="overflow-y-auto p-5 space-y-6 flex-1">
          {/* Client selection + Quick Add */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <label className="text-sm font-semibold text-stone-900">1. Client</label>
              <button
                type="button"
                onClick={() => setShowQuickClient(!showQuickClient)}
                className="min-h-[36px] px-2.5 rounded-lg text-xs font-semibold text-[#1E3A5F] hover:bg-[#1E3A5F]/5 inline-flex items-center gap-1"
              >
                <UserPlus className="h-3.5 w-3.5" />
                <span>{showQuickClient ? 'Annuler' : 'Nouveau client'}</span>
              </button>
            </div>

            {showQuickClient ? (
              <div className="rounded-2xl border border-stone-200 bg-stone-50 p-4 space-y-3">
                <input
                  type="text"
                  placeholder="Nom complet du client"
                  value={newClientName}
                  onChange={(e) => setNewClientName(e.target.value)}
                  className="w-full min-h-[44px] rounded-xl border border-stone-300 bg-white px-3 text-sm"
                />
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                  <input
                    type="tel"
                    placeholder="Téléphone (+223...)"
                    value={newClientPhone}
                    onChange={(e) => setNewClientPhone(e.target.value)}
                    className="w-full min-h-[44px] rounded-xl border border-stone-300 bg-white px-3 text-sm font-mono-num"
                  />
                  <input
                    type="text"
                    placeholder="Quartier / Adresse"
                    value={newClientAddress}
                    onChange={(e) => setNewClientAddress(e.target.value)}
                    className="w-full min-h-[44px] rounded-xl border border-stone-300 bg-white px-3 text-sm"
                  />
                </div>
                <button
                  type="button"
                  onClick={handleCreateQuickClient}
                  className="w-full min-h-[44px] rounded-xl bg-[#1E3A5F] text-white text-xs font-semibold"
                >
                  Enregistrer et sélectionner ce client
                </button>
              </div>
            ) : (
              <select
                value={clientId}
                onChange={(e) => setClientId(e.target.value)}
                required
                className="w-full min-h-[48px] rounded-xl border border-stone-300 bg-white px-3.5 text-sm font-medium text-stone-900"
              >
                {db.clients.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.fullName} ({c.phone})
                  </option>
                ))}
              </select>
            )}
          </div>

          {/* Model Selection */}
          <div className="space-y-2">
            <label className="text-sm font-semibold text-stone-900">2. Modèle à confectionner</label>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
              {db.models.map((mod) => {
                const active = mod.id === modelId;
                return (
                  <button
                    key={mod.id}
                    type="button"
                    onClick={() => handleModelChange(mod.id)}
                    className={`flex items-center gap-3 p-2.5 rounded-2xl border text-left transition-all min-h-[68px] ${
                      active
                        ? 'border-[#1E3A5F] bg-[#1E3A5F]/5 ring-1 ring-[#1E3A5F]'
                        : 'border-stone-200 bg-white hover:bg-stone-50'
                    }`}
                  >
                    <ResilientImage
                      src={mod.photoUrl}
                      alt={mod.name}
                      className="h-12 w-12 rounded-xl object-cover shrink-0"
                    />
                    <div className="min-w-0 flex-1">
                      <p className="text-sm font-semibold text-stone-900 truncate">{mod.name}</p>
                      <p className="text-xs text-stone-500 font-mono-num">
                        {formatMoney(mod.defaultPrice, db.settings.currency)} · {mod.estimatedDays}j
                      </p>
                    </div>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Fabric Photo & Notes */}
          <div className="space-y-3 border-t border-stone-200 pt-5">
            <PhotoCaptureInput
              label="3. Photo du tissu apporté par le client"
              value={fabricPhotoUrl}
              onChange={setFabricPhotoUrl}
            />
            <input
              type="text"
              placeholder="Précisions tissu (ex: Bazin 5m, wax 3 pagnes, couleur...)"
              value={fabricNotes}
              onChange={(e) => setFabricNotes(e.target.value)}
              className="w-full min-h-[44px] rounded-xl border border-stone-300 bg-white px-3.5 text-sm"
            />
          </div>

          {/* Dynamic Measurements based on selected model */}
          <div className="space-y-3 border-t border-stone-200 pt-5">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                <Ruler className="h-4 w-4 text-[#1E3A5F]" />
                <h3 className="text-sm font-semibold text-stone-900">
                  4. Mensurations du modèle ({requiredMeasurementDefs.length})
                </h3>
              </div>
              {prefilledFromClient && (
                <span className="text-xs font-medium text-emerald-700">
                  Pré-rempli depuis la fiche client
                </span>
              )}
            </div>

            {requiredMeasurementDefs.length === 0 ? (
              <p className="text-xs text-stone-500">
                Ce modèle ne demande aucune mesure spécifique.
              </p>
            ) : (
              <div className="grid grid-cols-2 sm:grid-cols-3 gap-2.5">
                {requiredMeasurementDefs.map((field) => {
                  const val = measurements[field.key] ?? 0;
                  return (
                    <button
                      key={field.key}
                      type="button"
                      onClick={() =>
                        setKeypadConfig({
                          open: true,
                          title: field.label,
                          unit: field.unit,
                          value: val,
                          allowDecimal: true,
                          step: 0.5,
                          onApply: (nextVal) =>
                            setMeasurements((prev) => ({ ...prev, [field.key]: nextVal })),
                        })
                      }
                      className="min-h-[64px] rounded-2xl border border-stone-200 bg-stone-50 px-3 py-2 text-left hover:border-[#1E3A5F] transition-colors"
                    >
                      <span className="block text-[11px] font-medium text-stone-500 truncate">
                        {field.label}
                      </span>
                      <span className="block font-mono-num text-lg font-bold text-stone-900 mt-0.5">
                        {val > 0 ? `${val} ${field.unit}` : `— ${field.unit}`}
                      </span>
                    </button>
                  );
                })}
              </div>
            )}
          </div>

          {/* Planning & Tailor Assignment */}
          <div className="space-y-3 border-t border-stone-200 pt-5">
            <h3 className="text-sm font-semibold text-stone-900">5. Planning & Tailleur</h3>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <div>
                <label className="block text-xs font-medium text-stone-600 mb-1">
                  Date d’essayage
                </label>
                <input
                  type="date"
                  value={fittingDate}
                  onChange={(e) => setFittingDate(e.target.value)}
                  className="w-full min-h-[44px] rounded-xl border border-stone-300 bg-white px-3 text-sm font-mono-num"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-stone-600 mb-1">
                  Livraison prévue
                </label>
                <input
                  type="date"
                  value={deliveryDate}
                  onChange={(e) => setDeliveryDate(e.target.value)}
                  required
                  className="w-full min-h-[44px] rounded-xl border border-stone-300 bg-white px-3 text-sm font-mono-num"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-stone-600 mb-1">
                  Tailleur assigné
                </label>
                <select
                  value={assignedEmployeeId}
                  onChange={(e) => setAssignedEmployeeId(e.target.value)}
                  className="w-full min-h-[44px] rounded-xl border border-stone-300 bg-white px-3 text-sm"
                >
                  <option value="">Non assigné</option>
                  {db.employees
                    .filter((e) => e.active)
                    .map((emp) => (
                      <option key={emp.id} value={emp.id}>
                        {emp.fullName} ({emp.role})
                      </option>
                    ))}
                </select>
              </div>
            </div>
          </div>

          {/* Financial Calculation */}
          <div className="space-y-3 border-t border-stone-200 pt-5">
            <h3 className="text-sm font-semibold text-stone-900">6. Tarification & Avance</h3>
            <div className="grid grid-cols-3 gap-2.5">
              <button
                type="button"
                onClick={() =>
                  setKeypadConfig({
                    open: true,
                    title: 'Prix total convenu',
                    unit: db.settings.currency,
                    value: totalPrice,
                    allowDecimal: db.settings.currency === '€',
                    step: db.settings.currency === 'FCFA' ? 1000 : 5,
                    onApply: (v) => setTotalPrice(v),
                  })
                }
                className="min-h-[68px] rounded-2xl border border-stone-200 bg-stone-50 p-3 text-left hover:border-[#1E3A5F]"
              >
                <span className="block text-[11px] font-medium text-stone-500">Prix total</span>
                <span className="block font-mono-num text-base font-bold text-stone-900 mt-1">
                  {formatMoney(totalPrice, db.settings.currency)}
                </span>
              </button>

              <button
                type="button"
                onClick={() =>
                  setKeypadConfig({
                    open: true,
                    title: 'Avance versée',
                    unit: db.settings.currency,
                    value: advancePaid,
                    allowDecimal: db.settings.currency === '€',
                    step: db.settings.currency === 'FCFA' ? 1000 : 5,
                    onApply: (v) => setAdvancePaid(Math.min(totalPrice, v)),
                  })
                }
                className="min-h-[68px] rounded-2xl border border-stone-200 bg-emerald-50/60 p-3 text-left hover:border-emerald-600"
              >
                <span className="block text-[11px] font-medium text-emerald-800">Avance versée</span>
                <span className="block font-mono-num text-base font-bold text-emerald-800 mt-1">
                  {formatMoney(advancePaid, db.settings.currency)}
                </span>
              </button>

              <div className="min-h-[68px] rounded-2xl border border-stone-200 bg-white p-3">
                <span className="block text-[11px] font-medium text-stone-500">Reste à payer</span>
                <span
                  className={`block font-mono-num text-base font-bold mt-1 ${
                    balanceDue > 0 ? 'text-amber-800' : 'text-emerald-700'
                  }`}
                >
                  {formatMoney(balanceDue, db.settings.currency)}
                </span>
              </div>
            </div>

            <textarea
              rows={2}
              placeholder="Notes particulières pour l'atelier..."
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              className="w-full rounded-xl border border-stone-300 bg-white p-3 text-sm"
            />
          </div>

          <div className="sticky bottom-0 pt-3 pb-1 bg-white border-t border-stone-100 flex gap-3">
            <button
              type="button"
              onClick={onClose}
              className="min-h-[48px] px-4 rounded-xl border border-stone-300 text-sm font-semibold text-stone-700 hover:bg-stone-50"
            >
              Annuler
            </button>
            <button
              type="submit"
              className="flex-1 min-h-[48px] rounded-xl bg-[#1E3A5F] text-white text-sm font-semibold flex items-center justify-center gap-2 hover:bg-[#162C49] active:scale-[0.99]"
            >
              <Check className="h-4 w-4" />
              <span>{initialOrder ? 'Enregistrer les modifications' : 'Créer la commande'}</span>
            </button>
          </div>
        </form>
      </div>

      <NumericKeypadSheet
        isOpen={keypadConfig.open}
        title={keypadConfig.title}
        unit={keypadConfig.unit}
        initialValue={keypadConfig.value}
        allowDecimal={keypadConfig.allowDecimal}
        step={keypadConfig.step}
        onConfirm={keypadConfig.onApply}
        onClose={() => setKeypadConfig((prev) => ({ ...prev, open: false }))}
      />
    </div>
  );
};
