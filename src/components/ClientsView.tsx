import React, { useMemo, useState } from 'react';
import {
  Edit3,
  MapPin,
  MessageCircle,
  Phone,
  Plus,
  Ruler,
  Search,
  Trash2,
  X,
} from 'lucide-react';
import { AtelierDatabase, Client, STANDARD_MEASUREMENTS } from '../types';
import {
  buildWhatsAppUrl,
  formatDateFr,
  formatMoney,
  openExternalLink,
} from '../utils/formatters';
import { NumericKeypadSheet } from './NumericKeypadSheet';

interface ClientsViewProps {
  db: AtelierDatabase;
  onSaveClient: (client: Client) => void;
  onDeleteClient: (clientId: string) => void;
  onOpenOrder: (orderId: string) => void;
}

export const ClientsView: React.FC<ClientsViewProps> = ({
  db,
  onSaveClient,
  onDeleteClient,
  onOpenOrder,
}) => {
  const [search, setSearch] = useState('');
  const [selectedClientId, setSelectedClientId] = useState<string | null>(
    db.clients[0]?.id || null
  );
  const [modalOpen, setModalOpen] = useState(false);
  const [editingClient, setEditingClient] = useState<Client | null>(null);
  const [confirmDeleteId, setConfirmDeleteId] = useState<string | null>(null);

  // Form fields
  const [fullName, setFullName] = useState('');
  const [phone, setPhone] = useState('+223');
  const [address, setAddress] = useState('');
  const [notes, setNotes] = useState('');
  const [latestMeasurements, setLatestMeasurements] = useState<Record<string, number>>({});

  const [keypadConfig, setKeypadConfig] = useState<{
    open: boolean;
    key: string;
    label: string;
    value: number;
  }>({ open: false, key: '', label: '', value: 0 });

  const filteredClients = useMemo(() => {
    const q = search.trim().toLowerCase();
    return db.clients.filter(
      (c) =>
        c.fullName.toLowerCase().includes(q) ||
        c.phone.toLowerCase().includes(q) ||
        c.address.toLowerCase().includes(q)
    );
  }, [db.clients, search]);

  const activeClient = useMemo(
    () => db.clients.find((c) => c.id === selectedClientId) || null,
    [db.clients, selectedClientId]
  );

  const clientOrders = useMemo(
    () => (activeClient ? db.orders.filter((o) => o.clientId === activeClient.id) : []),
    [db.orders, activeClient]
  );

  const openCreateModal = () => {
    setEditingClient(null);
    setFullName('');
    setPhone('+223');
    setAddress('');
    setNotes('');
    setLatestMeasurements({});
    setModalOpen(true);
  };

  const openEditModal = (client: Client) => {
    setEditingClient(client);
    setFullName(client.fullName);
    setPhone(client.phone);
    setAddress(client.address);
    setNotes(client.notes || '');
    setLatestMeasurements({ ...client.latestMeasurements });
    setModalOpen(true);
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!fullName.trim() || !phone.trim()) return;
    const todayISO = new Date().toISOString().split('T')[0];
    const hasMeas = Object.values(latestMeasurements).some((v) => v > 0);
    const history = editingClient ? [...editingClient.measurementHistory] : [];
    if (hasMeas) {
      history.unshift({ date: todayISO, values: { ...latestMeasurements } });
    }
    const client: Client = {
      id: editingClient?.id || `cli-${Date.now()}`,
      fullName: fullName.trim(),
      phone: phone.trim(),
      address: address.trim() || 'Adresse non renseignée',
      notes: notes.trim() || undefined,
      latestMeasurements,
      measurementHistory: history.slice(0, 10),
      createdAt: editingClient?.createdAt || todayISO,
    };
    onSaveClient(client);
    setSelectedClientId(client.id);
    setModalOpen(false);
  };

  const sendWhatsApp = (client: Client, template: 'confirmation' | 'essayage' | 'pret' | 'custom') => {
    const latestOrder = db.orders.find((o) => o.clientId === client.id);
    const model = latestOrder ? db.models.find((m) => m.id === latestOrder.modelId) : undefined;
    const url = buildWhatsAppUrl(client.phone, template, {
      clientName: client.fullName,
      workshopName: db.settings.workshopName,
      modelName: model?.name,
      reference: latestOrder?.reference,
      deliveryDate: latestOrder?.deliveryDate,
      fittingDate: latestOrder?.fittingDate,
      balanceDue: latestOrder ? Math.max(0, latestOrder.totalPrice - latestOrder.advancePaid) : 0,
      currency: db.settings.currency,
    });
    openExternalLink(url);
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="font-display text-2xl font-semibold text-stone-900">
            Clients &amp; Mensurations
          </h1>
          <p className="text-xs text-stone-500">
            Historique des mesures et communication rapide WhatsApp
          </p>
        </div>
        <button
          type="button"
          onClick={openCreateModal}
          className="min-h-[44px] px-4 rounded-xl bg-[#1E3A5F] text-white text-sm font-semibold inline-flex items-center gap-2 hover:bg-[#162C49]"
        >
          <Plus className="h-4 w-4" />
          <span>Nouveau client</span>
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-12 gap-5 items-start">
        {/* Clients List */}
        <div className="md:col-span-5 lg:col-span-4 space-y-3">
          <div className="relative">
            <Search className="h-4 w-4 text-stone-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
            <input
              type="search"
              placeholder="Rechercher nom, tél, quartier..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full min-h-[44px] pl-10 pr-4 rounded-xl border border-stone-200 bg-white text-sm"
            />
          </div>

          <div className="rounded-2xl border border-stone-200 bg-white divide-y divide-stone-100 overflow-hidden">
            {filteredClients.length === 0 ? (
              <div className="p-6 text-center text-sm text-stone-500">Aucun client trouvé.</div>
            ) : (
              filteredClients.map((client) => {
                const count = db.orders.filter((o) => o.clientId === client.id).length;
                const isSelected = activeClient?.id === client.id;
                return (
                  <button
                    key={client.id}
                    type="button"
                    onClick={() => setSelectedClientId(client.id)}
                    className={`w-full p-3.5 text-left flex items-center justify-between gap-3 min-h-[64px] transition-colors ${
                      isSelected ? 'bg-[#1E3A5F]/8' : 'hover:bg-stone-50'
                    }`}
                  >
                    <div className="min-w-0">
                      <p className="text-sm font-semibold text-stone-900 truncate">
                        {client.fullName}
                      </p>
                      <p className="text-xs text-stone-500 font-mono-num truncate">
                        {client.phone} · {client.address}
                      </p>
                    </div>
                    <span className="font-mono-num text-xs font-semibold text-[#1E3A5F] shrink-0">
                      {count} cmd
                    </span>
                  </button>
                );
              })
            )}
          </div>
        </div>

        {/* Client Detail */}
        <div className="md:col-span-7 lg:col-span-8">
          {!activeClient ? (
            <div className="rounded-2xl border border-stone-200 bg-white p-10 text-center text-sm text-stone-500">
              Sélectionnez un client pour voir ses mensurations et ses commandes.
            </div>
          ) : (
            <div className="rounded-2xl border border-stone-200 bg-white p-5 space-y-5">
              <div className="flex flex-wrap items-start justify-between gap-3 border-b border-stone-200 pb-4">
                <div>
                  <h2 className="font-display text-2xl font-semibold text-stone-900">
                    {activeClient.fullName}
                  </h2>
                  <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-stone-600 mt-1.5">
                    <span className="inline-flex items-center gap-1 font-mono-num">
                      <Phone className="h-3.5 w-3.5 text-[#1E3A5F]" />
                      {activeClient.phone}
                    </span>
                    <span className="inline-flex items-center gap-1">
                      <MapPin className="h-3.5 w-3.5 text-[#1E3A5F]" />
                      {activeClient.address}
                    </span>
                  </div>
                  {activeClient.notes && (
                    <p className="text-xs text-stone-600 mt-2 italic">“{activeClient.notes}”</p>
                  )}
                </div>

                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => openEditModal(activeClient)}
                    className="min-h-[44px] px-3.5 rounded-xl border border-stone-200 text-xs font-semibold text-stone-800 hover:bg-stone-50 inline-flex items-center gap-1.5"
                  >
                    <Edit3 className="h-4 w-4" />
                    <span>Modifier / Mesures</span>
                  </button>
                  {confirmDeleteId === activeClient.id ? (
                    <button
                      type="button"
                      onClick={() => {
                        onDeleteClient(activeClient.id);
                        setConfirmDeleteId(null);
                      }}
                      className="min-h-[44px] px-3 rounded-xl bg-red-700 text-white text-xs font-semibold"
                    >
                      Confirmer
                    </button>
                  ) : (
                    <button
                      type="button"
                      onClick={() => setConfirmDeleteId(activeClient.id)}
                      className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-xl border border-stone-200 text-red-700 hover:bg-red-50"
                      aria-label="Supprimer le client"
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  )}
                </div>
              </div>

              {/* Quick WhatsApp Bar */}
              <div className="space-y-2">
                <p className="text-xs font-semibold text-stone-700">
                  Communication rapide WhatsApp
                </p>
                <div className="flex flex-wrap gap-2">
                  <button
                    type="button"
                    onClick={() => sendWhatsApp(activeClient, 'confirmation')}
                    className="min-h-[44px] px-3.5 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-900 text-xs font-semibold inline-flex items-center gap-1.5 hover:bg-emerald-100"
                  >
                    <MessageCircle className="h-4 w-4 text-emerald-700" />
                    <span>Confirmation</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => sendWhatsApp(activeClient, 'essayage')}
                    className="min-h-[44px] px-3.5 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-900 text-xs font-semibold inline-flex items-center gap-1.5 hover:bg-emerald-100"
                  >
                    <MessageCircle className="h-4 w-4 text-emerald-700" />
                    <span>Rappel essayage</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => sendWhatsApp(activeClient, 'pret')}
                    className="min-h-[44px] px-3.5 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-900 text-xs font-semibold inline-flex items-center gap-1.5 hover:bg-emerald-100"
                  >
                    <MessageCircle className="h-4 w-4 text-emerald-700" />
                    <span>Tenue prête</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => sendWhatsApp(activeClient, 'custom')}
                    className="min-h-[44px] px-3.5 rounded-xl border border-stone-200 bg-white text-stone-800 text-xs font-semibold inline-flex items-center gap-1.5 hover:bg-stone-50"
                  >
                    <MessageCircle className="h-4 w-4 text-stone-600" />
                    <span>Message libre</span>
                  </button>
                </div>
              </div>

              {/* Latest Measurements */}
              <div className="space-y-2.5 border-t border-stone-200 pt-4">
                <div className="flex items-center gap-2">
                  <Ruler className="h-4 w-4 text-[#1E3A5F]" />
                  <h3 className="text-sm font-semibold text-stone-900">
                    Dernières mensurations enregistrées (pré-remplies aux commandes)
                  </h3>
                </div>
                {Object.keys(activeClient.latestMeasurements).length === 0 ? (
                  <p className="text-xs text-stone-500">
                    Aucune mesure enregistrée pour le moment.
                  </p>
                ) : (
                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                    {Object.entries(activeClient.latestMeasurements)
                      .filter(([, v]) => v > 0)
                      .map(([key, val]) => {
                        const std = STANDARD_MEASUREMENTS.find((s) => s.key === key);
                        return (
                          <div
                            key={key}
                            className="rounded-xl border border-stone-200 bg-stone-50 px-3 py-2"
                          >
                            <span className="block text-[11px] text-stone-500 truncate">
                              {std?.label || key}
                            </span>
                            <span className="font-mono-num text-sm font-bold text-stone-900">
                              {val} cm
                            </span>
                          </div>
                        );
                      })}
                  </div>
                )}
              </div>

              {/* Client Order History */}
              <div className="space-y-2.5 border-t border-stone-200 pt-4">
                <h3 className="text-sm font-semibold text-stone-900">
                  Historique des commandes ({clientOrders.length})
                </h3>
                {clientOrders.length === 0 ? (
                  <p className="text-xs text-stone-500">Aucune commande passée par ce client.</p>
                ) : (
                  <div className="divide-y divide-stone-100 rounded-xl border border-stone-200">
                    {clientOrders.map((ord) => {
                      const model = db.models.find((m) => m.id === ord.modelId);
                      return (
                        <button
                          key={ord.id}
                          type="button"
                          onClick={() => onOpenOrder(ord.id)}
                          className="w-full px-3.5 py-3 flex items-center justify-between gap-2 text-left hover:bg-stone-50 min-h-[52px]"
                        >
                          <div className="min-w-0">
                            <p className="text-sm font-semibold text-stone-900 truncate">
                              {model?.name || 'Modèle'}
                            </p>
                            <p className="text-xs text-stone-500 font-mono-num">
                              {ord.reference} · {ord.status} · {formatDateFr(ord.deliveryDate)}
                            </p>
                          </div>
                          <span className="font-mono-num text-xs font-bold text-stone-900 shrink-0">
                            {formatMoney(ord.totalPrice, db.settings.currency)}
                          </span>
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Create / Edit Client Modal */}
      {modalOpen && (
        <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/50 backdrop-blur-xs">
          <div className="w-full max-w-xl max-h-[92vh] flex flex-col rounded-t-3xl sm:rounded-2xl bg-white shadow-2xl overflow-hidden">
            <div className="flex items-center justify-between px-5 py-4 border-b border-stone-200">
              <h2 className="font-display text-xl font-semibold text-stone-900">
                {editingClient ? 'Modifier la fiche client' : 'Nouveau client'}
              </h2>
              <button
                type="button"
                onClick={() => setModalOpen(false)}
                className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-xl text-stone-500 hover:bg-stone-100"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <form onSubmit={handleSubmit} className="overflow-y-auto p-5 space-y-4 flex-1">
              <div>
                <label className="block text-xs font-semibold text-stone-700 mb-1">
                  Nom complet
                </label>
                <input
                  type="text"
                  required
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  className="w-full min-h-[44px] rounded-xl border border-stone-300 px-3 text-sm"
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-stone-700 mb-1">
                    Téléphone (format international)
                  </label>
                  <input
                    type="tel"
                    required
                    value={phone}
                    onChange={(e) => setPhone(e.target.value)}
                    placeholder="+22376000000"
                    className="w-full min-h-[44px] rounded-xl border border-stone-300 px-3 text-sm font-mono-num"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-stone-700 mb-1">
                    Adresse / Quartier
                  </label>
                  <input
                    type="text"
                    value={address}
                    onChange={(e) => setAddress(e.target.value)}
                    className="w-full min-h-[44px] rounded-xl border border-stone-300 px-3 text-sm"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-stone-700 mb-1">
                  Notes particulières
                </label>
                <textarea
                  rows={2}
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
                  className="w-full rounded-xl border border-stone-300 p-3 text-sm"
                />
              </div>

              <div className="border-t border-stone-200 pt-4 space-y-2.5">
                <label className="block text-sm font-semibold text-stone-900">
                  Mensurations de base (cm) — toucher pour saisir
                </label>
                <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
                  {STANDARD_MEASUREMENTS.slice(0, 9).map((m) => {
                    const val = latestMeasurements[m.key] || 0;
                    return (
                      <button
                        key={m.key}
                        type="button"
                        onClick={() =>
                          setKeypadConfig({
                            open: true,
                            key: m.key,
                            label: m.label,
                            value: val,
                          })
                        }
                        className="min-h-[56px] rounded-xl border border-stone-200 bg-stone-50 px-3 py-2 text-left hover:border-[#1E3A5F]"
                      >
                        <span className="block text-[11px] text-stone-500 truncate">
                          {m.label}
                        </span>
                        <span className="font-mono-num text-sm font-bold text-stone-900">
                          {val > 0 ? `${val} cm` : '—'}
                        </span>
                      </button>
                    );
                  })}
                </div>
              </div>

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
                  Enregistrer la fiche client
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      <NumericKeypadSheet
        isOpen={keypadConfig.open}
        title={keypadConfig.label}
        unit="cm"
        initialValue={keypadConfig.value}
        allowDecimal={true}
        step={0.5}
        onConfirm={(val) =>
          setLatestMeasurements((prev) => ({ ...prev, [keypadConfig.key]: val }))
        }
        onClose={() => setKeypadConfig((prev) => ({ ...prev, open: false }))}
      />
    </div>
  );
};
