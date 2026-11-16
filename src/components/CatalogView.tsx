import React, { useMemo, useState } from 'react';
import { Check, Edit3, Plus, Ruler, Scissors, Trash2, X } from 'lucide-react';
import {
  AtelierDatabase,
  GarmentCategory,
  GarmentModel,
  HaberdasheryRecipeItem,
  STANDARD_MEASUREMENTS,
} from '../types';
import { formatMoney } from '../utils/formatters';
import { PhotoCaptureInput, ResilientImage } from './PhotoCaptureInput';
import { NumericKeypadSheet } from './NumericKeypadSheet';

interface CatalogViewProps {
  db: AtelierDatabase;
  onSaveModel: (model: GarmentModel) => void;
  onDeleteModel: (modelId: string) => void;
}

const CATEGORIES: ('Tous' | GarmentCategory)[] = ['Tous', 'Homme', 'Femme', 'Enfant', 'Cérémonie'];

export const CatalogView: React.FC<CatalogViewProps> = ({ db, onSaveModel, onDeleteModel }) => {
  const [categoryFilter, setCategoryFilter] = useState<'Tous' | GarmentCategory>('Tous');
  const [editingModel, setEditingModel] = useState<GarmentModel | null>(null);
  const [modalOpen, setModalOpen] = useState(false);

  // Form states
  const [name, setName] = useState('');
  const [category, setCategory] = useState<GarmentCategory>('Femme');
  const [photoUrl, setPhotoUrl] = useState('');
  const [defaultPrice, setDefaultPrice] = useState(25000);
  const [estimatedDays, setEstimatedDays] = useState(5);
  const [measurementKeys, setMeasurementKeys] = useState<string[]>([]);
  const [customMeasurements, setCustomMeasurements] = useState<
    { key: string; label: string; unit: string }[]
  >([]);
  const [newCustomMeasLabel, setNewCustomMeasLabel] = useState('');
  const [recipe, setRecipe] = useState<HaberdasheryRecipeItem[]>([]);
  const [notes, setNotes] = useState('');
  const [confirmDeleteId, setConfirmDeleteId] = useState<string | null>(null);

  const [keypadOpen, setKeypadOpen] = useState(false);

  const filteredModels = useMemo(() => {
    if (categoryFilter === 'Tous') return db.models;
    return db.models.filter((m) => m.category === categoryFilter);
  }, [db.models, categoryFilter]);

  const openCreateModal = () => {
    setEditingModel(null);
    setName('');
    setCategory('Femme');
    setPhotoUrl('');
    setDefaultPrice(25000);
    setEstimatedDays(5);
    setMeasurementKeys(['tour_poitrine', 'tour_taille', 'tour_hanches', 'longueur_totale']);
    setCustomMeasurements([]);
    setRecipe([]);
    setNotes('');
    setModalOpen(true);
  };

  const openEditModal = (model: GarmentModel) => {
    setEditingModel(model);
    setName(model.name);
    setCategory(model.category);
    setPhotoUrl(model.photoUrl);
    setDefaultPrice(model.defaultPrice);
    setEstimatedDays(model.estimatedDays);
    setMeasurementKeys([...model.measurementKeys]);
    setCustomMeasurements(model.customMeasurements ? [...model.customMeasurements] : []);
    setRecipe(model.recipe.map((r) => ({ ...r })));
    setNotes(model.notes || '');
    setModalOpen(true);
  };

  const toggleMeasurementKey = (key: string) => {
    setMeasurementKeys((prev) =>
      prev.includes(key) ? prev.filter((k) => k !== key) : [...prev, key]
    );
  };

  const addCustomMeasurement = () => {
    const label = newCustomMeasLabel.trim();
    if (!label) return;
    const key = `custom_${label.toLowerCase().replace(/[^a-z0-9]/g, '_')}_${Date.now().toString().slice(-4)}`;
    setCustomMeasurements((prev) => [...prev, { key, label, unit: 'cm' }]);
    setMeasurementKeys((prev) => [...prev, key]);
    setNewCustomMeasLabel('');
  };

  const updateRecipeItem = (itemId: string, qty: number) => {
    setRecipe((prev) => {
      const exists = prev.find((r) => r.itemId === itemId);
      if (qty <= 0) {
        return prev.filter((r) => r.itemId !== itemId);
      }
      if (exists) {
        return prev.map((r) => (r.itemId === itemId ? { ...r, quantity: qty } : r));
      }
      return [...prev, { itemId, quantity: qty }];
    });
  };

  const getRecipeCost = (rec: HaberdasheryRecipeItem[]) => {
    return rec.reduce((sum, r) => {
      const item = db.inventory.find((i) => i.id === r.itemId);
      return sum + (item ? item.unitPrice * r.quantity : 0);
    }, 0);
  };

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;
    const model: GarmentModel = {
      id: editingModel?.id || `mod-${Date.now()}`,
      name: name.trim(),
      category,
      photoUrl,
      defaultPrice,
      estimatedDays: Math.max(1, estimatedDays),
      measurementKeys,
      customMeasurements: customMeasurements.length > 0 ? customMeasurements : undefined,
      recipe,
      notes: notes.trim() || undefined,
      createdAt: editingModel?.createdAt || new Date().toISOString().split('T')[0],
    };
    onSaveModel(model);
    setModalOpen(false);
  };

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="font-display text-2xl font-semibold text-stone-900">
            Modèles &amp; Catalogue
          </h1>
          <p className="text-xs text-stone-500">
            Gabarits de mesures dynamiques et recettes de mercerie associées
          </p>
        </div>
        <button
          type="button"
          onClick={openCreateModal}
          className="min-h-[44px] px-4 rounded-xl bg-[#1E3A5F] text-white text-sm font-semibold inline-flex items-center gap-2 hover:bg-[#162C49]"
        >
          <Plus className="h-4 w-4" />
          <span>Nouveau modèle</span>
        </button>
      </div>

      {/* Category Filter Segmented Bar */}
      <div className="flex items-center gap-1.5 overflow-x-auto pb-1">
        {CATEGORIES.map((cat) => (
          <button
            key={cat}
            type="button"
            onClick={() => setCategoryFilter(cat)}
            className={`min-h-[40px] px-3.5 rounded-xl text-xs font-semibold whitespace-nowrap shrink-0 transition-colors ${
              categoryFilter === cat
                ? 'bg-[#1E3A5F] text-white'
                : 'bg-stone-100 text-stone-700 hover:bg-stone-200'
            }`}
          >
            {cat}
          </button>
        ))}
      </div>

      {/* Models Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
        {filteredModels.map((model) => {
          const mercerieCost = getRecipeCost(model.recipe);
          return (
            <div
              key={model.id}
              className="rounded-2xl border border-stone-200 bg-white overflow-hidden flex flex-col"
            >
              <div className="relative aspect-[4/3] bg-stone-100">
                <ResilientImage
                  src={model.photoUrl}
                  alt={model.name}
                  className="w-full h-full object-cover"
                />
              </div>

              <div className="p-4 flex-1 flex flex-col justify-between space-y-3">
                <div>
                  <div className="flex items-center gap-2 text-xs text-stone-500">
                    <span>{model.category}</span>
                    <span>·</span>
                    <span className="font-mono-num">{model.estimatedDays} jours</span>
                  </div>
                  <h3 className="font-display text-lg font-semibold text-stone-900 mt-0.5 leading-snug">
                    {model.name}
                  </h3>
                  {model.notes && (
                    <p className="text-xs text-stone-600 mt-1 line-clamp-2">{model.notes}</p>
                  )}
                </div>

                <div className="space-y-2 border-t border-stone-100 pt-3">
                  <div className="flex items-center justify-between text-xs">
                    <span className="text-stone-500 inline-flex items-center gap-1">
                      <Ruler className="h-3.5 w-3.5" />
                      Gabarit
                    </span>
                    <span className="font-mono-num font-semibold text-stone-800">
                      {model.measurementKeys.length} mesures
                    </span>
                  </div>
                  <div className="flex items-center justify-between text-xs">
                    <span className="text-stone-500 inline-flex items-center gap-1">
                      <Scissors className="h-3.5 w-3.5" />
                      Coût mercerie
                    </span>
                    <span className="font-mono-num font-medium text-stone-700">
                      {formatMoney(mercerieCost, db.settings.currency)}
                    </span>
                  </div>
                  <div className="flex items-center justify-between pt-1">
                    <span className="font-mono-num text-base font-bold text-[#1E3A5F]">
                      {formatMoney(model.defaultPrice, db.settings.currency)}
                    </span>
                    <div className="flex items-center gap-1">
                      <button
                        type="button"
                        onClick={() => openEditModal(model)}
                        className="min-h-[40px] px-2.5 rounded-xl border border-stone-200 text-xs font-semibold text-stone-700 hover:bg-stone-50 inline-flex items-center gap-1"
                      >
                        <Edit3 className="h-3.5 w-3.5" />
                        <span>Modifier</span>
                      </button>
                      {confirmDeleteId === model.id ? (
                        <button
                          type="button"
                          onClick={() => {
                            onDeleteModel(model.id);
                            setConfirmDeleteId(null);
                          }}
                          className="min-h-[40px] px-2.5 rounded-xl bg-red-700 text-white text-xs font-semibold"
                        >
                          Suppr.
                        </button>
                      ) : (
                        <button
                          type="button"
                          onClick={() => setConfirmDeleteId(model.id)}
                          className="min-h-[40px] min-w-[40px] flex items-center justify-center rounded-xl border border-stone-200 text-red-700 hover:bg-red-50"
                          aria-label="Supprimer le modèle"
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                        </button>
                      )}
                    </div>
                  </div>
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {/* Create / Edit Model Modal */}
      {modalOpen && (
        <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/50 backdrop-blur-xs">
          <div className="w-full max-w-2xl max-h-[92vh] flex flex-col rounded-t-3xl sm:rounded-2xl bg-white shadow-2xl overflow-hidden">
            <div className="flex items-center justify-between px-5 py-4 border-b border-stone-200">
              <h2 className="font-display text-xl font-semibold text-stone-900">
                {editingModel ? 'Modifier le modèle' : 'Créer un modèle'}
              </h2>
              <button
                type="button"
                onClick={() => setModalOpen(false)}
                className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-xl text-stone-500 hover:bg-stone-100"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <form onSubmit={handleSave} className="overflow-y-auto p-5 space-y-5 flex-1">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-stone-700 mb-1">
                    Nom du modèle
                  </label>
                  <input
                    type="text"
                    required
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="Ex: Robe Kaba Brodé"
                    className="w-full min-h-[44px] rounded-xl border border-stone-300 px-3 text-sm"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-stone-700 mb-1">
                    Catégorie
                  </label>
                  <select
                    value={category}
                    onChange={(e) => setCategory(e.target.value as GarmentCategory)}
                    className="w-full min-h-[44px] rounded-xl border border-stone-300 px-3 text-sm bg-white"
                  >
                    {(['Homme', 'Femme', 'Enfant', 'Cérémonie'] as GarmentCategory[]).map((c) => (
                      <option key={c} value={c}>
                        {c}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <button
                  type="button"
                  onClick={() => setKeypadOpen(true)}
                  className="min-h-[64px] rounded-2xl border border-stone-200 bg-stone-50 px-3.5 py-2 text-left hover:border-[#1E3A5F]"
                >
                  <span className="block text-[11px] font-medium text-stone-500">
                    Prix de confection par défaut
                  </span>
                  <span className="block font-mono-num text-lg font-bold text-stone-900">
                    {formatMoney(defaultPrice, db.settings.currency)}
                  </span>
                </button>
                <div>
                  <label className="block text-xs font-semibold text-stone-700 mb-1">
                    Délai confection (jours)
                  </label>
                  <input
                    type="number"
                    min={1}
                    max={90}
                    value={estimatedDays}
                    onChange={(e) => setEstimatedDays(Number(e.target.value) || 1)}
                    className="w-full min-h-[44px] rounded-xl border border-stone-300 px-3 text-sm font-mono-num"
                  />
                </div>
              </div>

              <PhotoCaptureInput
                label="Photo de référence du modèle"
                value={photoUrl}
                onChange={setPhotoUrl}
              />

              {/* Dynamic Measurement Template */}
              <div className="space-y-2.5 border-t border-stone-200 pt-4">
                <label className="block text-sm font-semibold text-stone-900">
                  Gabarit de mesures requis à la commande
                </label>
                <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
                  {[...STANDARD_MEASUREMENTS, ...customMeasurements].map((m) => {
                    const active = measurementKeys.includes(m.key);
                    return (
                      <button
                        key={m.key}
                        type="button"
                        onClick={() => toggleMeasurementKey(m.key)}
                        className={`min-h-[44px] px-3 py-2 rounded-xl border text-left text-xs font-medium flex items-center justify-between gap-2 transition-colors ${
                          active
                            ? 'bg-[#1E3A5F] text-white border-[#1E3A5F]'
                            : 'bg-white text-stone-700 border-stone-200 hover:bg-stone-50'
                        }`}
                      >
                        <span className="truncate">{m.label}</span>
                        {active && <Check className="h-3.5 w-3.5 shrink-0" />}
                      </button>
                    );
                  })}
                </div>
                <div className="flex gap-2 pt-1">
                  <input
                    type="text"
                    placeholder="Ajouter une mesure spécifique (ex: Hauteur buste)..."
                    value={newCustomMeasLabel}
                    onChange={(e) => setNewCustomMeasLabel(e.target.value)}
                    className="flex-1 min-h-[44px] rounded-xl border border-stone-300 px-3 text-xs"
                  />
                  <button
                    type="button"
                    onClick={addCustomMeasurement}
                    className="min-h-[44px] px-3 rounded-xl bg-stone-100 border border-stone-200 text-xs font-semibold text-stone-800"
                  >
                    Ajouter
                  </button>
                </div>
              </div>

              {/* Haberdashery Recipe */}
              <div className="space-y-2.5 border-t border-stone-200 pt-4">
                <div className="flex items-center justify-between">
                  <label className="text-sm font-semibold text-stone-900">
                    Recette de mercerie (déduite à la coupe)
                  </label>
                  <span className="font-mono-num text-xs font-semibold text-[#1E3A5F]">
                    Coût : {formatMoney(getRecipeCost(recipe), db.settings.currency)}
                  </span>
                </div>
                <div className="divide-y divide-stone-100 rounded-2xl border border-stone-200 bg-stone-50/50">
                  {db.inventory.map((item) => {
                    const current = recipe.find((r) => r.itemId === item.id)?.quantity || 0;
                    return (
                      <div
                        key={item.id}
                        className="px-3.5 py-2.5 flex items-center justify-between gap-3"
                      >
                        <div className="min-w-0">
                          <p className="text-xs font-semibold text-stone-900 truncate">
                            {item.name}
                          </p>
                          <p className="text-[11px] text-stone-500 font-mono-num">
                            {formatMoney(item.unitPrice, db.settings.currency)} / {item.unit}
                          </p>
                        </div>
                        <div className="flex items-center gap-2 shrink-0">
                          <button
                            type="button"
                            onClick={() => updateRecipeItem(item.id, Math.max(0, current - 1))}
                            className="min-h-[38px] min-w-[38px] rounded-lg border border-stone-300 bg-white font-bold text-stone-700"
                          >
                            -
                          </button>
                          <span className="font-mono-num w-12 text-center text-xs font-bold text-stone-900">
                            {current} {item.unit}
                          </span>
                          <button
                            type="button"
                            onClick={() => updateRecipeItem(item.id, current + 1)}
                            className="min-h-[38px] min-w-[38px] rounded-lg border border-stone-300 bg-white font-bold text-stone-700"
                          >
                            +
                          </button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>

              <textarea
                rows={2}
                placeholder="Notes techniques de confection..."
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                className="w-full rounded-xl border border-stone-300 p-3 text-sm"
              />

              <div className="flex gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setModalOpen(false)}
                  className="min-h-[48px] px-4 rounded-xl border border-stone-300 text-sm font-semibold text-stone-700"
                >
                  Annuler
                </button>
                <button
                  type="submit"
                  className="flex-1 min-h-[48px] rounded-xl bg-[#1E3A5F] text-white text-sm font-semibold"
                >
                  Enregistrer le modèle
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      <NumericKeypadSheet
        isOpen={keypadOpen}
        title="Prix de confection par défaut"
        unit={db.settings.currency}
        initialValue={defaultPrice}
        allowDecimal={db.settings.currency === '€'}
        step={db.settings.currency === 'FCFA' ? 1000 : 5}
        onConfirm={setDefaultPrice}
        onClose={() => setKeypadOpen(false)}
      />
    </div>
  );
};
