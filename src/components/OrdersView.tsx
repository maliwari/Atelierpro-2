import React, { useMemo, useState } from 'react';
import {
  AlertTriangle,
  ArrowLeft,
  CheckCircle2,
  Download,
  Edit3,
  Eye,
  FileText,
  MessageCircle,
  Plus,
  Printer,
  Scissors,
  Search,
  Share2,
  Trash2,
  UserCheck,
  Wallet,
  X,
} from 'lucide-react';
import {
  AtelierDatabase,
  ORDER_WORKFLOW,
  Order,
  OrderStatus,
  STANDARD_MEASUREMENTS,
} from '../types';
import {
  buildWhatsAppUrl,
  formatDateFr,
  formatMoney,
  getOrderUrgency,
  openExternalLink,
  WhatsAppTemplateType,
} from '../utils/formatters';
import {
  generateClientReceiptPDF,
  generateCutSheetPDF,
  shareClientReceiptViaWhatsApp,
} from '../utils/pdfGenerator';
import { ResilientImage } from './PhotoCaptureInput';
import { NumericKeypadSheet } from './NumericKeypadSheet';

interface OrdersViewProps {
  db: AtelierDatabase;
  selectedOrderId: string | null;
  onSelectOrder: (id: string | null) => void;
  onNewOrder: () => void;
  onEditOrder: (order: Order) => void;
  onUpdateStatus: (orderId: string, nextStatus: OrderStatus) => void;
  onRecordPayment: (orderId: string, newAdvancePaid: number) => void;
  onDeleteOrder: (orderId: string) => void;
}

export const OrdersView: React.FC<OrdersViewProps> = ({
  db,
  selectedOrderId,
  onSelectOrder,
  onNewOrder,
  onEditOrder,
  onUpdateStatus,
  onRecordPayment,
  onDeleteOrder,
}) => {
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('actives');
  const [paymentKeypadOpen, setPaymentKeypadOpen] = useState(false);
  const [confirmDeleteId, setConfirmDeleteId] = useState<string | null>(null);
  const [receiptModalOpen, setReceiptModalOpen] = useState(false);
  const [pdfBusy, setPdfBusy] = useState(false);
  const [pdfFeedback, setPdfFeedback] = useState<string | null>(null);

  const filteredOrders = useMemo(() => {
    const q = search.trim().toLowerCase();
    return db.orders
      .filter((order) => {
        if (statusFilter === 'actives') {
          if (order.status === 'Livré') return false;
        } else if (statusFilter === 'urgentes') {
          const urg = getOrderUrgency(order);
          if (urg.level !== 'overdue' && urg.level !== 'urgent_48h') return false;
        } else if (statusFilter !== 'toutes') {
          if (order.status !== statusFilter) return false;
        }

        if (!q) return true;
        const client = db.clients.find((c) => c.id === order.clientId);
        const model = db.models.find((m) => m.id === order.modelId);
        return (
          order.reference.toLowerCase().includes(q) ||
          (client?.fullName || '').toLowerCase().includes(q) ||
          (model?.name || '').toLowerCase().includes(q)
        );
      })
      .sort((a, b) => a.deliveryDate.localeCompare(b.deliveryDate));
  }, [db.orders, db.clients, db.models, search, statusFilter]);

  const activeOrder = useMemo(() => {
    if (!selectedOrderId) return null;
    return db.orders.find((o) => o.id === selectedOrderId) || null;
  }, [db.orders, selectedOrderId]);

  const activeClient = activeOrder
    ? db.clients.find((c) => c.id === activeOrder.clientId)
    : undefined;
  const activeModel = activeOrder
    ? db.models.find((m) => m.id === activeOrder.modelId)
    : undefined;
  const activeEmployee = activeOrder?.assignedEmployeeId
    ? db.employees.find((e) => e.id === activeOrder.assignedEmployeeId)
    : undefined;

  const sendWhatsApp = (template: WhatsAppTemplateType) => {
    if (!activeOrder || !activeClient) return;
    const balance = Math.max(0, activeOrder.totalPrice - activeOrder.advancePaid);
    const url = buildWhatsAppUrl(activeClient.phone, template, {
      clientName: activeClient.fullName,
      workshopName: db.settings.workshopName,
      modelName: activeModel?.name,
      reference: activeOrder.reference,
      deliveryDate: activeOrder.deliveryDate,
      fittingDate: activeOrder.fittingDate,
      balanceDue: balance,
      currency: db.settings.currency,
    });
    openExternalLink(url);
  };

  const handleDownloadReceiptPdf = async () => {
    if (!activeOrder) return;
    setPdfBusy(true);
    setPdfFeedback(null);
    try {
      const fileName = await generateClientReceiptPDF(
        activeOrder,
        activeClient,
        activeModel,
        activeEmployee,
        db.settings
      );
      setPdfFeedback(`Reçu téléchargé : ${fileName}`);
    } finally {
      setPdfBusy(false);
    }
  };

  const handleShareReceiptWhatsApp = async () => {
    if (!activeOrder) return;
    setPdfBusy(true);
    setPdfFeedback(null);
    try {
      const mode = await shareClientReceiptViaWhatsApp(
        activeOrder,
        activeClient,
        activeModel,
        activeEmployee,
        db.settings
      );
      setPdfFeedback(
        mode === 'native_share'
          ? 'Menu de partage ouvert avec le reçu PDF.'
          : 'Reçu PDF téléchargé et message WhatsApp préparé avec le détail complet.'
      );
    } finally {
      setPdfBusy(false);
    }
  };

  return (
    <div className="space-y-4">
      {/* Header Row */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="font-display text-2xl font-semibold text-stone-900">
            Commandes &amp; Production
          </h1>
          <p className="text-xs text-stone-500">
            Suivi du workflow en 1 clic et déduction automatique du stock à la coupe
          </p>
        </div>
        <button
          type="button"
          onClick={onNewOrder}
          className="min-h-[44px] px-4 py-2 rounded-xl bg-[#1E3A5F] text-white text-sm font-semibold inline-flex items-center gap-2 hover:bg-[#162C49] active:scale-[0.98] whitespace-nowrap"
        >
          <Plus className="h-4 w-4" />
          <span>Nouvelle commande</span>
        </button>
      </div>

      {/* Master-Detail Layout: Tablet Landscape / Desktop = 2 columns; Mobile = List or Detail */}
      <div className="grid grid-cols-1 md:grid-cols-12 gap-5 items-start">
        {/* MASTER LIST (hidden on mobile when an order is selected) */}
        <div
          className={`md:col-span-5 lg:col-span-4 space-y-3 ${
            activeOrder ? 'hidden md:block' : 'block'
          }`}
        >
          {/* Search */}
          <div className="relative">
            <Search className="h-4 w-4 text-stone-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
            <input
              type="search"
              placeholder="Client, modèle ou référence..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full min-h-[44px] pl-10 pr-4 rounded-xl border border-stone-200 bg-white text-sm text-stone-900 focus:outline-none focus:border-[#1E3A5F]"
            />
          </div>

          {/* Segmented Filter Tabs */}
          <div className="flex items-center gap-1 overflow-x-auto pb-1">
            {[
              { id: 'actives', label: 'En cours' },
              { id: 'urgentes', label: 'Urgentes' },
              { id: 'Prêt', label: 'Prêtes' },
              { id: 'Livré', label: 'Livrées' },
              { id: 'toutes', label: 'Toutes' },
            ].map((tab) => (
              <button
                key={tab.id}
                type="button"
                onClick={() => setStatusFilter(tab.id)}
                className={`min-h-[38px] px-3 rounded-lg text-xs font-semibold whitespace-nowrap shrink-0 transition-colors ${
                  statusFilter === tab.id
                    ? 'bg-[#1E3A5F] text-white'
                    : 'bg-stone-100 text-stone-600 hover:bg-stone-200'
                }`}
              >
                {tab.label}
              </button>
            ))}
          </div>

          {/* Orders List */}
          <div className="rounded-2xl border border-stone-200 bg-white divide-y divide-stone-100 overflow-hidden">
            {filteredOrders.length === 0 ? (
              <div className="p-6 text-center text-sm text-stone-500">
                Aucune commande ne correspond à ce filtre.
              </div>
            ) : (
              filteredOrders.map((order) => {
                const client = db.clients.find((c) => c.id === order.clientId);
                const model = db.models.find((m) => m.id === order.modelId);
                const urgency = getOrderUrgency(order);
                const isSelected = activeOrder?.id === order.id;
                const balance = Math.max(0, order.totalPrice - order.advancePaid);

                return (
                  <button
                    key={order.id}
                    type="button"
                    onClick={() => onSelectOrder(order.id)}
                    className={`w-full p-3.5 text-left flex items-center gap-3 transition-colors min-h-[76px] ${
                      isSelected ? 'bg-[#1E3A5F]/8' : 'hover:bg-stone-50'
                    }`}
                  >
                    <ResilientImage
                      src={order.fabricPhotoUrl || model?.photoUrl}
                      alt={model?.name || 'Tenue'}
                      className="h-14 w-12 rounded-xl object-cover shrink-0 border border-stone-200"
                    />
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center justify-between gap-2">
                        <span className="text-sm font-semibold text-stone-900 truncate">
                          {client?.fullName || 'Client'}
                        </span>
                        <span className="font-mono-num text-[11px] font-medium text-stone-500 shrink-0">
                          {order.reference}
                        </span>
                      </div>
                      <p className="text-xs text-stone-600 truncate mt-0.5">{model?.name}</p>
                      <div className="flex items-center justify-between gap-2 mt-1.5 text-xs">
                        <span
                          className={`inline-flex items-center gap-1 font-medium ${
                            urgency.level === 'overdue'
                              ? 'text-red-700'
                              : urgency.level === 'urgent_48h'
                              ? 'text-amber-700'
                              : order.status === 'Prêt' || order.status === 'Livré'
                              ? 'text-emerald-700'
                              : 'text-stone-600'
                          }`}
                        >
                          {(urgency.level === 'overdue' || urgency.level === 'urgent_48h') && (
                            <AlertTriangle className="h-3.5 w-3.5 shrink-0" />
                          )}
                          <span>
                            {order.status} · {urgency.label}
                          </span>
                        </span>
                        <span className="font-mono-num text-[11px] font-semibold text-stone-700 shrink-0">
                          {balance > 0 ? `Reste ${formatMoney(balance, db.settings.currency)}` : 'Soldé'}
                        </span>
                      </div>
                    </div>
                  </button>
                );
              })
            )}
          </div>
        </div>

        {/* DETAIL PANE */}
        <div
          className={`md:col-span-7 lg:col-span-8 ${
            activeOrder ? 'block' : 'hidden md:block'
          }`}
        >
          {!activeOrder ? (
            <div className="rounded-2xl border border-stone-200 bg-white p-10 text-center">
              <Scissors className="h-8 w-8 text-stone-400 mx-auto mb-3" />
              <h2 className="font-display text-lg font-semibold text-stone-900">
                Sélectionnez une commande
              </h2>
              <p className="text-sm text-stone-500 mt-1 max-w-md mx-auto">
                Consultez les photos de tissu, ajustez les mensurations, faites avancer l’étape de couture ou générez le reçu client PDF.
              </p>
            </div>
          ) : (
            <div className="rounded-2xl border border-stone-200 bg-white p-4 sm:p-6 space-y-6">
              {/* Mobile Back Button + Actions Header */}
              <div className="flex flex-wrap items-center justify-between gap-3 border-b border-stone-200 pb-4">
                <div className="flex items-center gap-2 min-w-0">
                  <button
                    type="button"
                    onClick={() => onSelectOrder(null)}
                    className="md:hidden min-h-[44px] min-w-[44px] flex items-center justify-center rounded-xl border border-stone-200 text-stone-700 hover:bg-stone-50"
                    aria-label="Retour à la liste"
                  >
                    <ArrowLeft className="h-5 w-5" />
                  </button>
                  <div className="min-w-0">
                    <div className="flex items-center gap-2 text-xs text-stone-500 font-mono-num">
                      <span>{activeOrder.reference}</span>
                      <span>·</span>
                      <span>Livraison {formatDateFr(activeOrder.deliveryDate)}</span>
                    </div>
                    <h2 className="font-display text-xl font-semibold text-stone-900 truncate">
                      {activeClient?.fullName || 'Client'} — {activeModel?.name}
                    </h2>
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => onEditOrder(activeOrder)}
                    className="min-h-[44px] px-3 rounded-xl border border-stone-200 text-xs font-semibold text-stone-700 hover:bg-stone-50 inline-flex items-center gap-1.5"
                  >
                    <Edit3 className="h-4 w-4" />
                    <span>Modifier</span>
                  </button>
                  {confirmDeleteId === activeOrder.id ? (
                    <button
                      type="button"
                      onClick={() => {
                        onDeleteOrder(activeOrder.id);
                        setConfirmDeleteId(null);
                      }}
                      className="min-h-[44px] px-3 rounded-xl bg-red-700 text-white text-xs font-semibold"
                    >
                      Confirmer suppression
                    </button>
                  ) : (
                    <button
                      type="button"
                      onClick={() => setConfirmDeleteId(activeOrder.id)}
                      className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-xl border border-stone-200 text-red-700 hover:bg-red-50"
                      aria-label="Supprimer la commande"
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  )}
                </div>
              </div>

              {/* 1-Click Workflow Stepper */}
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-semibold text-stone-700">
                    Suivi de production (clic direct pour changer d’étape)
                  </span>
                  {activeOrder.stockDeducted && (
                    <span className="text-xs text-emerald-700 font-medium inline-flex items-center gap-1">
                      <CheckCircle2 className="h-3.5 w-3.5" />
                      Mercerie déduite
                    </span>
                  )}
                </div>
                <div className="grid grid-cols-3 sm:grid-cols-6 gap-1.5">
                  {ORDER_WORKFLOW.map((step, index) => {
                    const currentIndex = ORDER_WORKFLOW.indexOf(activeOrder.status);
                    const isCurrent = step === activeOrder.status;
                    const isPast = index < currentIndex;
                    return (
                      <button
                        key={step}
                        type="button"
                        onClick={() => onUpdateStatus(activeOrder.id, step)}
                        className={`min-h-[44px] px-2 py-2 rounded-xl text-xs font-semibold transition-all text-center border ${
                          isCurrent
                            ? 'bg-[#1E3A5F] text-white border-[#1E3A5F] shadow-xs'
                            : isPast
                            ? 'bg-emerald-50 text-emerald-800 border-emerald-200 hover:bg-emerald-100'
                            : 'bg-stone-50 text-stone-600 border-stone-200 hover:bg-stone-100'
                        }`}
                      >
                        {step}
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Photos & Details Grid */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-2">
                  <p className="text-xs font-semibold text-stone-600">Tissu client</p>
                  <ResilientImage
                    src={activeOrder.fabricPhotoUrl}
                    alt={activeOrder.fabricNotes || 'Photo du tissu'}
                    className="w-full aspect-[4/3] rounded-2xl object-cover border border-stone-200"
                  />
                  {activeOrder.fabricNotes && (
                    <p className="text-xs text-stone-600">{activeOrder.fabricNotes}</p>
                  )}
                </div>

                <div className="space-y-3 rounded-2xl bg-stone-50 border border-stone-200 p-4">
                  <div className="flex items-center justify-between text-xs">
                    <span className="text-stone-500">Modèle référence</span>
                    <span className="font-semibold text-stone-900">{activeModel?.name}</span>
                  </div>
                  <div className="flex items-center justify-between text-xs">
                    <span className="text-stone-500">Tailleur assigné</span>
                    <span className="font-semibold text-stone-900 inline-flex items-center gap-1">
                      <UserCheck className="h-3.5 w-3.5 text-[#1E3A5F]" />
                      {activeEmployee ? `${activeEmployee.fullName}` : 'Non assigné'}
                    </span>
                  </div>
                  <div className="flex items-center justify-between text-xs">
                    <span className="text-stone-500">Date commande</span>
                    <span className="font-mono-num text-stone-900">
                      {formatDateFr(activeOrder.orderDate)}
                    </span>
                  </div>
                  {activeOrder.fittingDate && (
                    <div className="flex items-center justify-between text-xs">
                      <span className="text-stone-500">Essayage prévu</span>
                      <span className="font-mono-num font-semibold text-[#1E3A5F]">
                        {formatDateFr(activeOrder.fittingDate)}
                      </span>
                    </div>
                  )}
                  <div className="flex items-center justify-between text-xs">
                    <span className="text-stone-500">Livraison prévue</span>
                    <span className="font-mono-num font-semibold text-stone-900">
                      {formatDateFr(activeOrder.deliveryDate)}
                    </span>
                  </div>
                  {activeOrder.notes && (
                    <div className="pt-2 border-t border-stone-200 text-xs text-stone-600">
                      {activeOrder.notes}
                    </div>
                  )}
                </div>
              </div>

              {/* Financial Payment Summary */}
              <div className="rounded-2xl border border-stone-200 p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div className="grid grid-cols-3 gap-4 flex-1">
                  <div>
                    <span className="block text-[11px] text-stone-500">Prix total</span>
                    <span className="font-mono-num text-base font-bold text-stone-900">
                      {formatMoney(activeOrder.totalPrice, db.settings.currency)}
                    </span>
                  </div>
                  <div>
                    <span className="block text-[11px] text-stone-500">Encaissé</span>
                    <span className="font-mono-num text-base font-bold text-emerald-700">
                      {formatMoney(activeOrder.advancePaid, db.settings.currency)}
                    </span>
                  </div>
                  <div>
                    <span className="block text-[11px] text-stone-500">Reste à payer</span>
                    <span
                      className={`font-mono-num text-base font-bold ${
                        activeOrder.totalPrice - activeOrder.advancePaid > 0
                          ? 'text-red-700'
                          : 'text-emerald-700'
                      }`}
                    >
                      {formatMoney(
                        Math.max(0, activeOrder.totalPrice - activeOrder.advancePaid),
                        db.settings.currency
                      )}
                    </span>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => setPaymentKeypadOpen(true)}
                  className="min-h-[44px] px-4 rounded-xl bg-emerald-700 text-white text-xs font-semibold inline-flex items-center justify-center gap-2 hover:bg-emerald-800 shrink-0"
                >
                  <Wallet className="h-4 w-4" />
                  <span>Mettre à jour paiement</span>
                </button>
              </div>

              {/* Measurements Table */}
              <div>
                <h3 className="text-sm font-semibold text-stone-900 mb-2.5">
                  Mensurations de la commande
                </h3>
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                  {Object.entries(activeOrder.measurements).map(([key, val]) => {
                    const std = STANDARD_MEASUREMENTS.find((s) => s.key === key);
                    const custom = activeModel?.customMeasurements?.find((c) => c.key === key);
                    const label = std?.label || custom?.label || key;
                    return (
                      <div
                        key={key}
                        className="rounded-xl border border-stone-200 bg-stone-50 px-3 py-2"
                      >
                        <span className="block text-[11px] text-stone-500 truncate">{label}</span>
                        <span className="font-mono-num text-sm font-bold text-stone-900">
                          {val} cm
                        </span>
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Detailed Receipt PDF & WhatsApp Actions */}
              <div className="border-t border-stone-200 pt-4 space-y-4">
                <div className="rounded-2xl border border-[#1E3A5F]/20 bg-[#1E3A5F]/5 p-4 space-y-3">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div>
                      <h3 className="text-sm font-semibold text-stone-900">
                        Reçu détaillé client (PDF)
                      </h3>
                      <p className="text-xs text-stone-600">
                        Inclut le logo de l’atelier, le modèle, les mensurations et le solde à payer.
                      </p>
                    </div>
                    <button
                      type="button"
                      onClick={() => setReceiptModalOpen(true)}
                      className="min-h-[40px] px-3 rounded-xl border border-stone-300 bg-white text-stone-800 text-xs font-semibold inline-flex items-center gap-1.5 hover:bg-stone-50"
                    >
                      <Eye className="h-4 w-4 text-[#1E3A5F]" />
                      <span>Aperçu du reçu</span>
                    </button>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                    <button
                      type="button"
                      disabled={pdfBusy}
                      onClick={handleDownloadReceiptPdf}
                      className="min-h-[46px] px-4 rounded-xl bg-[#1E3A5F] text-white text-xs font-semibold inline-flex items-center justify-center gap-2 hover:bg-[#162C49] disabled:opacity-60"
                    >
                      <Download className="h-4 w-4" />
                      <span>{pdfBusy ? 'Génération…' : 'Télécharger Reçu PDF'}</span>
                    </button>

                    <button
                      type="button"
                      disabled={pdfBusy}
                      onClick={handleShareReceiptWhatsApp}
                      className="min-h-[46px] px-4 rounded-xl bg-emerald-700 text-white text-xs font-semibold inline-flex items-center justify-center gap-2 hover:bg-emerald-800 disabled:opacity-60"
                    >
                      <Share2 className="h-4 w-4" />
                      <span>Partager PDF via WhatsApp</span>
                    </button>
                  </div>

                  {pdfFeedback && (
                    <p className="text-xs font-medium text-[#1E3A5F]">{pdfFeedback}</p>
                  )}
                </div>

                {/* Quick WhatsApp templates + Cut Sheet */}
                <div className="flex flex-wrap items-center gap-2">
                  <button
                    type="button"
                    onClick={() => sendWhatsApp('confirmation')}
                    className="min-h-[44px] px-3.5 rounded-xl border border-emerald-200 bg-emerald-50 text-emerald-900 text-xs font-semibold inline-flex items-center gap-1.5 hover:bg-emerald-100"
                  >
                    <MessageCircle className="h-4 w-4 text-emerald-700" />
                    <span>WhatsApp Confirmation</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => sendWhatsApp('essayage')}
                    className="min-h-[44px] px-3.5 rounded-xl border border-emerald-200 bg-emerald-50 text-emerald-900 text-xs font-semibold inline-flex items-center gap-1.5 hover:bg-emerald-100"
                  >
                    <MessageCircle className="h-4 w-4 text-emerald-700" />
                    <span>Rappel Essayage</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => sendWhatsApp('pret')}
                    className="min-h-[44px] px-3.5 rounded-xl border border-emerald-200 bg-emerald-50 text-emerald-900 text-xs font-semibold inline-flex items-center gap-1.5 hover:bg-emerald-100"
                  >
                    <MessageCircle className="h-4 w-4 text-emerald-700" />
                    <span>Tenue Prête</span>
                  </button>

                  <div className="flex-1" />

                  <button
                    type="button"
                    onClick={() =>
                      generateCutSheetPDF(
                        activeOrder,
                        activeClient,
                        activeModel,
                        activeEmployee,
                        db.inventory,
                        db.settings
                      )
                    }
                    className="min-h-[44px] px-3.5 rounded-xl bg-stone-900 text-white text-xs font-semibold inline-flex items-center gap-1.5 hover:bg-stone-800"
                  >
                    <Printer className="h-4 w-4" />
                    <span>Fiche de coupe PDF</span>
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Receipt Preview Modal */}
      {receiptModalOpen && activeOrder && (
        <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/50 backdrop-blur-xs">
          <div className="w-full max-w-lg max-h-[92vh] flex flex-col rounded-t-3xl sm:rounded-2xl bg-white shadow-2xl overflow-hidden">
            <div className="flex items-center justify-between px-5 py-4 border-b border-stone-200">
              <div className="flex items-center gap-2">
                <FileText className="h-5 w-5 text-[#1E3A5F]" />
                <h2 className="font-display text-lg font-semibold text-stone-900">
                  Aperçu du reçu — {activeOrder.reference}
                </h2>
              </div>
              <button
                type="button"
                onClick={() => setReceiptModalOpen(false)}
                className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-xl text-stone-500 hover:bg-stone-100"
                aria-label="Fermer"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <div className="overflow-y-auto p-5 space-y-5 flex-1">
              {/* Workshop Header Preview */}
              <div className="rounded-2xl bg-[#1E3A5F] text-white p-4 flex items-center gap-3.5">
                <img
                  src={db.settings.workshopLogoUrl || '/pwa-192x192.png'}
                  alt={db.settings.workshopName}
                  className="h-14 w-14 rounded-xl bg-white object-contain p-1 shrink-0"
                />
                <div className="min-w-0">
                  <p className="font-display text-lg font-bold truncate">
                    {db.settings.workshopName}
                  </p>
                  <p className="text-xs text-white/80 truncate">{db.settings.workshopAddress}</p>
                  <p className="text-xs text-white/80 font-mono-num">{db.settings.workshopPhone}</p>
                </div>
              </div>

              {/* Client & Model */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
                <div className="rounded-xl border border-stone-200 bg-stone-50 p-3 space-y-1">
                  <span className="font-semibold text-stone-500 block">CLIENT</span>
                  <p className="text-sm font-semibold text-stone-900">
                    {activeClient?.fullName}
                  </p>
                  <p className="font-mono-num text-stone-600">{activeClient?.phone}</p>
                  <p className="text-stone-600">{activeClient?.address}</p>
                </div>
                <div className="rounded-xl border border-stone-200 bg-stone-50 p-3 space-y-1">
                  <span className="font-semibold text-stone-500 block">MODÈLE &amp; LIVRAISON</span>
                  <p className="text-sm font-semibold text-stone-900">{activeModel?.name}</p>
                  <p className="text-stone-600">
                    {activeModel?.category} · Statut : {activeOrder.status}
                  </p>
                  <p className="font-mono-num text-stone-700">
                    Livraison : {formatDateFr(activeOrder.deliveryDate)}
                  </p>
                </div>
              </div>

              {/* Measurements */}
              <div>
                <h3 className="text-xs font-semibold text-stone-700 mb-2">
                  Mensurations incluses ({Object.keys(activeOrder.measurements).length})
                </h3>
                <div className="grid grid-cols-2 gap-1.5 text-xs">
                  {Object.entries(activeOrder.measurements)
                    .filter(([, val]) => val > 0)
                    .map(([key, val]) => {
                      const std = STANDARD_MEASUREMENTS.find((s) => s.key === key);
                      const custom = activeModel?.customMeasurements?.find((c) => c.key === key);
                      return (
                        <div
                          key={key}
                          className="flex items-center justify-between rounded-lg border border-stone-200 px-2.5 py-1.5"
                        >
                          <span className="text-stone-600 truncate">
                            {std?.label || custom?.label || key}
                          </span>
                          <span className="font-mono-num font-bold text-stone-900 ml-2">
                            {val} cm
                          </span>
                        </div>
                      );
                    })}
                </div>
              </div>

              {/* Financial Balance */}
              <div className="rounded-xl border border-stone-200 bg-stone-50 p-3.5 space-y-1.5 text-sm">
                <div className="flex justify-between">
                  <span className="text-stone-600">Prix total</span>
                  <span className="font-mono-num font-semibold text-stone-900">
                    {formatMoney(activeOrder.totalPrice, db.settings.currency)}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-stone-600">Avance versée</span>
                  <span className="font-mono-num font-semibold text-emerald-700">
                    {formatMoney(activeOrder.advancePaid, db.settings.currency)}
                  </span>
                </div>
                <div className="border-t border-stone-200 pt-2 flex justify-between font-bold">
                  <span className="text-stone-900">Solde à payer</span>
                  <span
                    className={`font-mono-num ${
                      activeOrder.totalPrice - activeOrder.advancePaid > 0
                        ? 'text-red-700'
                        : 'text-emerald-700'
                    }`}
                  >
                    {activeOrder.totalPrice - activeOrder.advancePaid > 0
                      ? formatMoney(
                          activeOrder.totalPrice - activeOrder.advancePaid,
                          db.settings.currency
                        )
                      : 'Soldé (0)'}
                  </span>
                </div>
              </div>
            </div>

            <div className="p-4 border-t border-stone-200 grid grid-cols-2 gap-2.5">
              <button
                type="button"
                disabled={pdfBusy}
                onClick={handleDownloadReceiptPdf}
                className="min-h-[48px] rounded-xl bg-[#1E3A5F] text-white text-xs font-semibold inline-flex items-center justify-center gap-1.5 hover:bg-[#162C49]"
              >
                <Download className="h-4 w-4" />
                <span>Télécharger PDF</span>
              </button>
              <button
                type="button"
                disabled={pdfBusy}
                onClick={handleShareReceiptWhatsApp}
                className="min-h-[48px] rounded-xl bg-emerald-700 text-white text-xs font-semibold inline-flex items-center justify-center gap-1.5 hover:bg-emerald-800"
              >
                <Share2 className="h-4 w-4" />
                <span>Partager WhatsApp</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {activeOrder && (
        <NumericKeypadSheet
          isOpen={paymentKeypadOpen}
          title={`Montant total encaissé (${activeOrder.reference})`}
          unit={db.settings.currency}
          initialValue={activeOrder.advancePaid}
          allowDecimal={db.settings.currency === '€'}
          step={db.settings.currency === 'FCFA' ? 1000 : 5}
          onConfirm={(val) =>
            onRecordPayment(activeOrder.id, Math.min(activeOrder.totalPrice, val))
          }
          onClose={() => setPaymentKeypadOpen(false)}
        />
      )}
    </div>
  );
};
