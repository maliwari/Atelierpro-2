import React, { useMemo, useState } from 'react';
import {
  AlertTriangle,
  ArrowDownLeft,
  ArrowUpRight,
  Edit3,
  Package,
  Plus,
  X,
} from 'lucide-react';
import { AtelierDatabase, HaberdasheryItem, HaberdasheryUnit } from '../types';
import { formatDateFr, formatMoney } from '../utils/formatters';
import { NumericKeypadSheet } from './NumericKeypadSheet';

interface InventoryViewProps {
  db: AtelierDatabase;
  onSaveItem: (item: HaberdasheryItem) => void;
  onAdjustStock: (
    itemId: string,
    delta: number,
    note: string,
    recordAsExpense: boolean
  ) => void;
}

export const InventoryView: React.FC<InventoryViewProps> = ({
  db,
  onSaveItem,
  onAdjustStock,
}) => {
  const [itemModalOpen, setItemModalOpen] = useState(false);
  const [editingItem, setEditingItem] = useState<HaberdasheryItem | null>(null);

  const [name, setName] = useState('');
  const [unit, setUnit] = useState<HaberdasheryUnit>('pièce');
  const [unitPrice, setUnitPrice] = useState(500);
  const [quantityInStock, setQuantityInStock] = useState(10);
  const [criticalThreshold, setCriticalThreshold] = useState(5);

  const [movementTarget, setMovementTarget] = useState<{
    item: HaberdasheryItem;
    type: 'entree' | 'sortie';
  } | null>(null);
  const [movementQty, setMovementQty] = useState(5);
  const [movementNote, setMovementNote] = useState('');
  const [logExpense, setLogExpense] = useState(true);
  const [priceKeypadOpen, setPriceKeypadOpen] = useState(false);

  const lowStockCount = useMemo(
    () => db.inventory.filter((i) => i.quantityInStock <= i.criticalThreshold).length,
    [db.inventory]
  );

  const openCreateItem = () => {
    setEditingItem(null);
    setName('');
    setUnit('pièce');
    setUnitPrice(500);
    setQuantityInStock(10);
    setCriticalThreshold(5);
    setItemModalOpen(true);
  };

  const openEditItem = (item: HaberdasheryItem) => {
    setEditingItem(item);
    setName(item.name);
    setUnit(item.unit);
    setUnitPrice(item.unitPrice);
    setQuantityInStock(item.quantityInStock);
    setCriticalThreshold(item.criticalThreshold);
    setItemModalOpen(true);
  };

  const handleSaveItem = (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;
    const item: HaberdasheryItem = {
      id: editingItem?.id || `merc-${Date.now()}`,
      name: name.trim(),
      unit,
      unitPrice,
      quantityInStock,
      criticalThreshold,
      updatedAt: new Date().toISOString().split('T')[0],
    };
    onSaveItem(item);
    setItemModalOpen(false);
  };

  const handleSubmitMovement = (e: React.FormEvent) => {
    e.preventDefault();
    if (!movementTarget || movementQty <= 0) return;
    const delta = movementTarget.type === 'entree' ? movementQty : -movementQty;
    onAdjustStock(
      movementTarget.item.id,
      delta,
      movementNote.trim() ||
        (movementTarget.type === 'entree' ? 'Achat / Entrée manuelle' : 'Sortie manuelle atelier'),
      movementTarget.type === 'entree' && logExpense
    );
    setMovementTarget(null);
    setMovementNote('');
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="font-display text-2xl font-semibold text-stone-900">
            Mercerie &amp; Stocks
          </h1>
          <p className="text-xs text-stone-500">
            Sortie automatique à l’étape Coupe · {lowStockCount} alerte(s) stock bas
          </p>
        </div>
        <button
          type="button"
          onClick={openCreateItem}
          className="min-h-[44px] px-4 rounded-xl bg-[#1E3A5F] text-white text-sm font-semibold inline-flex items-center gap-2 hover:bg-[#162C49]"
        >
          <Plus className="h-4 w-4" />
          <span>Ajouter fourniture</span>
        </button>
      </div>

      {/* Inventory Cards / List */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
        {db.inventory.map((item) => {
          const isLow = item.quantityInStock <= item.criticalThreshold;
          return (
            <div
              key={item.id}
              className={`rounded-2xl border p-4 bg-white flex flex-col justify-between gap-3 ${
                isLow ? 'border-amber-300' : 'border-stone-200'
              }`}
            >
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <Package className="h-4 w-4 text-[#1E3A5F] shrink-0" />
                    <h3 className="text-sm font-semibold text-stone-900 truncate">{item.name}</h3>
                  </div>
                  <p className="text-xs text-stone-500 font-mono-num mt-1">
                    {formatMoney(item.unitPrice, db.settings.currency)} / {item.unit} · Seuil min :{' '}
                    {item.criticalThreshold}
                  </p>
                  {isLow && (
                    <p className="inline-flex items-center gap-1 text-xs font-semibold text-amber-800 mt-1.5">
                      <AlertTriangle className="h-3.5 w-3.5 shrink-0" />
                      <span>Stock bas — Réapprovisionner</span>
                    </p>
                  )}
                </div>

                <div className="text-right shrink-0">
                  <span
                    className={`font-mono-num text-xl font-bold ${
                      isLow ? 'text-amber-800' : 'text-stone-900'
                    }`}
                  >
                    {item.quantityInStock}
                  </span>
                  <span className="block text-[11px] text-stone-500">{item.unit}(s)</span>
                </div>
              </div>

              <div className="flex items-center gap-2 border-t border-stone-100 pt-3">
                <button
                  type="button"
                  onClick={() => {
                    setMovementTarget({ item, type: 'entree' });
                    setMovementQty(5);
                    setLogExpense(true);
                  }}
                  className="flex-1 min-h-[42px] rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-900 text-xs font-semibold inline-flex items-center justify-center gap-1.5 hover:bg-emerald-100"
                >
                  <ArrowDownLeft className="h-4 w-4" />
                  <span>Entrée</span>
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setMovementTarget({ item, type: 'sortie' });
                    setMovementQty(1);
                    setLogExpense(false);
                  }}
                  className="flex-1 min-h-[42px] rounded-xl bg-stone-50 border border-stone-200 text-stone-800 text-xs font-semibold inline-flex items-center justify-center gap-1.5 hover:bg-stone-100"
                >
                  <ArrowUpRight className="h-4 w-4" />
                  <span>Sortie</span>
                </button>
                <button
                  type="button"
                  onClick={() => openEditItem(item)}
                  className="min-h-[42px] min-w-[42px] flex items-center justify-center rounded-xl border border-stone-200 text-stone-600 hover:bg-stone-50"
                  aria-label="Modifier l'article"
                >
                  <Edit3 className="h-4 w-4" />
                </button>
              </div>
            </div>
          );
        })}
      </div>

      {/* Recent Stock Movements */}
      <section className="rounded-2xl border border-stone-200 bg-white p-4 sm:p-5 space-y-3">
        <h2 className="font-display text-lg font-semibold text-stone-900">
          Historique des mouvements récents
        </h2>
        <div className="divide-y divide-stone-100">
          {db.stockMovements.slice(0, 12).map((mov) => {
            const item = db.inventory.find((i) => i.id === mov.itemId);
            const isEntry = mov.type === 'entree';
            return (
              <div key={mov.id} className="py-2.5 flex items-center justify-between gap-3 text-xs">
                <div className="min-w-0">
                  <p className="font-semibold text-stone-900 truncate">
                    {item?.name || 'Article'} — {mov.note}
                  </p>
                  <p className="text-stone-500 font-mono-num">
                    {formatDateFr(mov.date)} ·{' '}
                    {mov.type === 'entree'
                      ? 'Entrée stock'
                      : mov.type === 'sortie_auto'
                      ? 'Sortie auto (Coupe)'
                      : 'Sortie manuelle'}
                  </p>
                </div>
                <span
                  className={`font-mono-num font-bold shrink-0 ${
                    isEntry ? 'text-emerald-700' : 'text-red-700'
                  }`}
                >
                  {isEntry ? '+' : '-'}
                  {mov.quantity} {item?.unit || ''}
                </span>
              </div>
            );
          })}
        </div>
      </section>

      {/* Item Create / Edit Modal */}
      {itemModalOpen && (
        <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/50 backdrop-blur-xs">
          <div className="w-full max-w-md rounded-t-3xl sm:rounded-2xl bg-white p-5 shadow-2xl">
            <div className="flex items-center justify-between mb-4">
              <h2 className="font-display text-xl font-semibold text-stone-900">
                {editingItem ? 'Modifier la fourniture' : 'Nouvelle fourniture'}
              </h2>
              <button
                type="button"
                onClick={() => setItemModalOpen(false)}
                className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-xl text-stone-500"
              >
                <X className="h-5 w-5" />
              </button>
            </div>
            <form onSubmit={handleSaveItem} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-stone-700 mb-1">
                  Nom de l’article
                </label>
                <input
                  type="text"
                  required
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  className="w-full min-h-[44px] rounded-xl border border-stone-300 px-3 text-sm"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-stone-700 mb-1">Unité</label>
                  <select
                    value={unit}
                    onChange={(e) => setUnit(e.target.value as HaberdasheryUnit)}
                    className="w-full min-h-[44px] rounded-xl border border-stone-300 px-3 text-sm bg-white"
                  >
                    {(['pièce', 'bobine', 'mètre', 'paquet'] as HaberdasheryUnit[]).map((u) => (
                      <option key={u} value={u}>
                        {u}
                      </option>
                    ))}
                  </select>
                </div>
                <button
                  type="button"
                  onClick={() => setPriceKeypadOpen(true)}
                  className="min-h-[60px] rounded-xl border border-stone-200 bg-stone-50 px-3 py-1.5 text-left"
                >
                  <span className="block text-[11px] text-stone-500">Prix unitaire</span>
                  <span className="font-mono-num text-sm font-bold text-stone-900">
                    {formatMoney(unitPrice, db.settings.currency)}
                  </span>
                </button>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-stone-700 mb-1">
                    Quantité en stock
                  </label>
                  <input
                    type="number"
                    min={0}
                    value={quantityInStock}
                    onChange={(e) => setQuantityInStock(Number(e.target.value) || 0)}
                    className="w-full min-h-[44px] rounded-xl border border-stone-300 px-3 text-sm font-mono-num"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-stone-700 mb-1">
                    Seuil d’alerte
                  </label>
                  <input
                    type="number"
                    min={0}
                    value={criticalThreshold}
                    onChange={(e) => setCriticalThreshold(Number(e.target.value) || 0)}
                    className="w-full min-h-[44px] rounded-xl border border-stone-300 px-3 text-sm font-mono-num"
                  />
                </div>
              </div>

              <button
                type="submit"
                className="w-full min-h-[48px] rounded-xl bg-[#1E3A5F] text-white text-sm font-semibold"
              >
                Enregistrer l’article
              </button>
            </form>
          </div>
        </div>
      )}

      {/* Stock Movement Modal */}
      {movementTarget && (
        <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/50 backdrop-blur-xs">
          <div className="w-full max-w-md rounded-t-3xl sm:rounded-2xl bg-white p-5 shadow-2xl">
            <div className="flex items-center justify-between mb-3">
              <h2 className="font-display text-lg font-semibold text-stone-900">
                {movementTarget.type === 'entree' ? 'Entrée de stock' : 'Sortie de stock'} —{' '}
                {movementTarget.item.name}
              </h2>
              <button
                type="button"
                onClick={() => setMovementTarget(null)}
                className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-xl text-stone-500"
              >
                <X className="h-5 w-5" />
              </button>
            </div>
            <form onSubmit={handleSubmitMovement} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-stone-700 mb-1">
                  Quantité ({movementTarget.item.unit})
                </label>
                <input
                  type="number"
                  min={1}
                  required
                  value={movementQty}
                  onChange={(e) => setMovementQty(Math.max(1, Number(e.target.value) || 1))}
                  className="w-full min-h-[48px] rounded-xl border border-stone-300 px-3 text-base font-mono-num font-bold"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-stone-700 mb-1">
                  Motif / Note
                </label>
                <input
                  type="text"
                  value={movementNote}
                  onChange={(e) => setMovementNote(e.target.value)}
                  placeholder={
                    movementTarget.type === 'entree'
                      ? 'Ex: Achat fournisseur marché'
                      : 'Ex: Utilisation retouche'
                  }
                  className="w-full min-h-[44px] rounded-xl border border-stone-300 px-3 text-sm"
                />
              </div>
              {movementTarget.type === 'entree' && (
                <label className="flex items-center gap-2.5 text-xs text-stone-700 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={logExpense}
                    onChange={(e) => setLogExpense(e.target.checked)}
                    className="h-4 w-4 rounded border-stone-300 accent-[#1E3A5F]"
                  />
                  <span>
                    Enregistrer automatiquement en dépense ({' '}
                    {formatMoney(movementQty * movementTarget.item.unitPrice, db.settings.currency)})
                  </span>
                </label>
              )}
              <button
                type="submit"
                className="w-full min-h-[48px] rounded-xl bg-[#1E3A5F] text-white text-sm font-semibold"
              >
                Valider le mouvement
              </button>
            </form>
          </div>
        </div>
      )}

      <NumericKeypadSheet
        isOpen={priceKeypadOpen}
        title="Prix d’achat unitaire"
        unit={db.settings.currency}
        initialValue={unitPrice}
        allowDecimal={db.settings.currency === '€'}
        step={db.settings.currency === 'FCFA' ? 100 : 1}
        onConfirm={setUnitPrice}
        onClose={() => setPriceKeypadOpen(false)}
      />
    </div>
  );
};
