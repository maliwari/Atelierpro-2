import React, { useMemo, useState } from 'react';
import {
  AlertTriangle,
  Calendar as CalendarIcon,
  ChevronLeft,
  ChevronRight,
  Clock,
  Package,
  Plus,
  Scissors,
  Users,
} from 'lucide-react';
import { AtelierDatabase, Order } from '../types';
import { formatDateFr, formatMoney, getOrderUrgency } from '../utils/formatters';
import { ResilientImage } from './PhotoCaptureInput';

interface HomeAgendaViewProps {
  db: AtelierDatabase;
  onNewOrder: () => void;
  onOpenOrder: (orderId: string) => void;
  onNavigateTab: (tab: string) => void;
}

export const HomeAgendaView: React.FC<HomeAgendaViewProps> = ({
  db,
  onNewOrder,
  onOpenOrder,
  onNavigateTab,
}) => {
  const todayISO = useMemo(() => new Date().toISOString().split('T')[0], []);
  const [selectedDate, setSelectedDate] = useState<string>(todayISO);
  const [calendarMonth, setCalendarMonth] = useState<Date>(() => {
    const d = new Date();
    return new Date(d.getFullYear(), d.getMonth(), 1);
  });
  const [agendaMode, setAgendaMode] = useState<'week' | 'month'>('week');

  // 10-day horizontal scroller centered around today (-2 to +7 days)
  const scrollerDays = useMemo(() => {
    const days: { iso: string; dayShort: string; dayNum: number; isToday: boolean }[] = [];
    for (let offset = -2; offset <= 9; offset++) {
      const d = new Date();
      d.setDate(d.getDate() + offset);
      const iso = d.toISOString().split('T')[0];
      days.push({
        iso,
        dayShort: d.toLocaleDateString('fr-FR', { weekday: 'short' }).replace('.', ''),
        dayNum: d.getDate(),
        isToday: iso === todayISO,
      });
    }
    return days;
  }, [todayISO]);

  // Month grid days
  const monthDays = useMemo(() => {
    const year = calendarMonth.getFullYear();
    const month = calendarMonth.getMonth();
    const firstDay = new Date(year, month, 1);
    const startOffset = (firstDay.getDay() + 6) % 7; // Monday = 0
    const daysInMonth = new Date(year, month + 1, 0).getDate();
    const cells: (string | null)[] = [];
    for (let i = 0; i < startOffset; i++) cells.push(null);
    for (let day = 1; day <= daysInMonth; day++) {
      const d = new Date(year, month, day);
      const yStr = d.getFullYear();
      const mStr = String(d.getMonth() + 1).padStart(2, '0');
      const dStr = String(d.getDate()).padStart(2, '0');
      cells.push(`${yStr}-${mStr}-${dStr}`);
    }
    return cells;
  }, [calendarMonth]);

  const activeOrders = useMemo(
    () => db.orders.filter((o) => o.status !== 'Livré'),
    [db.orders]
  );

  const urgentOrders = useMemo(() => {
    return activeOrders
      .map((order) => ({ order, urgency: getOrderUrgency(order) }))
      .filter((x) => x.urgency.level === 'overdue' || x.urgency.level === 'urgent_48h')
      .sort((a, b) => a.urgency.daysDiff - b.urgency.daysDiff);
  }, [activeOrders]);

  const lowStockItems = useMemo(
    () => db.inventory.filter((i) => i.quantityInStock <= i.criticalThreshold),
    [db.inventory]
  );

  // Events on selectedDate (fittings or deliveries)
  const dayEvents = useMemo(() => {
    const list: { order: Order; eventType: 'Essayage' | 'Livraison' }[] = [];
    db.orders.forEach((order) => {
      if (order.fittingDate === selectedDate) {
        list.push({ order, eventType: 'Essayage' });
      }
      if (order.deliveryDate === selectedDate) {
        list.push({ order, eventType: 'Livraison' });
      }
    });
    return list;
  }, [db.orders, selectedDate]);

  const eventCountByDate = useMemo(() => {
    const map: Record<string, number> = {};
    db.orders.forEach((o) => {
      if (o.status === 'Livré') return;
      if (o.fittingDate) map[o.fittingDate] = (map[o.fittingDate] || 0) + 1;
      if (o.deliveryDate) map[o.deliveryDate] = (map[o.deliveryDate] || 0) + 1;
    });
    return map;
  }, [db.orders]);

  const readyCount = activeOrders.filter((o) => o.status === 'Prêt').length;

  return (
    <div className="space-y-6 pb-8">
      {/* Hero / Workshop Action Header */}
      <section className="flex flex-col sm:flex-row sm:items-end justify-between gap-4 border-b border-stone-200 pb-5">
        <div>
          <p className="text-xs font-medium text-stone-500">
            {db.settings.workshopName} · {formatDateFr(todayISO)}
          </p>
          <h1 className="font-display text-2xl sm:text-3xl font-semibold text-stone-900 mt-1 text-balance">
            Tableau d’atelier & Agenda
          </h1>
        </div>
        <button
          type="button"
          onClick={onNewOrder}
          className="min-h-[48px] px-5 py-2.5 rounded-xl bg-[#1E3A5F] text-white text-sm font-semibold inline-flex items-center justify-center gap-2 hover:bg-[#162C49] active:scale-[0.98] transition-all whitespace-nowrap shrink-0 shadow-sm"
        >
          <Plus className="h-5 w-5" />
          <span>Nouvelle commande</span>
        </button>
      </section>

      {/* Operational KPI Grid */}
      <section className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <button
          type="button"
          onClick={() => onNavigateTab('commandes')}
          className="text-left rounded-2xl border border-stone-200 bg-white p-4 hover:border-stone-300 transition-colors"
        >
          <div className="flex items-center justify-between text-stone-500 mb-2">
            <span className="text-xs font-medium">Commandes en cours</span>
            <Scissors className="h-4 w-4 text-[#1E3A5F]" />
          </div>
          <p className="font-mono-num text-2xl font-bold text-stone-900">{activeOrders.length}</p>
          <p className="text-xs text-stone-500 mt-1">{readyCount} prête(s) à livrer</p>
        </button>

        <button
          type="button"
          onClick={() => onNavigateTab('commandes')}
          className="text-left rounded-2xl border border-stone-200 bg-white p-4 hover:border-stone-300 transition-colors"
        >
          <div className="flex items-center justify-between text-stone-500 mb-2">
            <span className="text-xs font-medium">Urgences &lt; 48h</span>
            <AlertTriangle
              className={`h-4 w-4 ${urgentOrders.length > 0 ? 'text-red-700' : 'text-stone-400'}`}
            />
          </div>
          <p
            className={`font-mono-num text-2xl font-bold ${
              urgentOrders.length > 0 ? 'text-red-700' : 'text-stone-900'
            }`}
          >
            {urgentOrders.length}
          </p>
          <p className="text-xs text-stone-500 mt-1">
            {urgentOrders.filter((u) => u.urgency.level === 'overdue').length} en retard
          </p>
        </button>

        <button
          type="button"
          onClick={() => onNavigateTab('mercerie')}
          className="text-left rounded-2xl border border-stone-200 bg-white p-4 hover:border-stone-300 transition-colors"
        >
          <div className="flex items-center justify-between text-stone-500 mb-2">
            <span className="text-xs font-medium">Alertes mercerie</span>
            <Package
              className={`h-4 w-4 ${lowStockItems.length > 0 ? 'text-amber-700' : 'text-stone-400'}`}
            />
          </div>
          <p
            className={`font-mono-num text-2xl font-bold ${
              lowStockItems.length > 0 ? 'text-amber-700' : 'text-stone-900'
            }`}
          >
            {lowStockItems.length}
          </p>
          <p className="text-xs text-stone-500 mt-1">Sous seuil critique</p>
        </button>

        <button
          type="button"
          onClick={() => onNavigateTab('clients')}
          className="text-left rounded-2xl border border-stone-200 bg-white p-4 hover:border-stone-300 transition-colors"
        >
          <div className="flex items-center justify-between text-stone-500 mb-2">
            <span className="text-xs font-medium">Clients enregistrés</span>
            <Users className="h-4 w-4 text-[#1E3A5F]" />
          </div>
          <p className="font-mono-num text-2xl font-bold text-stone-900">{db.clients.length}</p>
          <p className="text-xs text-stone-500 mt-1">{db.models.length} modèles au catalogue</p>
        </button>
      </section>

      {/* Main Split: Agenda Calendar + Urgent Alerts */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        {/* Agenda / Calendar */}
        <section className="lg:col-span-7 rounded-2xl border border-stone-200 bg-white p-4 sm:p-5 space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <CalendarIcon className="h-5 w-5 text-[#1E3A5F]" />
              <h2 className="font-display text-lg font-semibold text-stone-900">
                Agenda des essayages & livraisons
              </h2>
            </div>

            <div className="flex items-center gap-1 p-1 bg-stone-100 rounded-xl">
              <button
                type="button"
                onClick={() => setAgendaMode('week')}
                className={`min-h-[36px] px-3 text-xs font-semibold rounded-lg transition-colors ${
                  agendaMode === 'week'
                    ? 'bg-white text-stone-900 shadow-xs'
                    : 'text-stone-600 hover:text-stone-900'
                }`}
              >
                Semaine
              </button>
              <button
                type="button"
                onClick={() => setAgendaMode('month')}
                className={`min-h-[36px] px-3 text-xs font-semibold rounded-lg transition-colors ${
                  agendaMode === 'month'
                    ? 'bg-white text-stone-900 shadow-xs'
                    : 'text-stone-600 hover:text-stone-900'
                }`}
              >
                Mois
              </button>
            </div>
          </div>

          {agendaMode === 'week' ? (
            <div className="flex items-center gap-2 overflow-x-auto pb-2 -mx-1 px-1">
              {scrollerDays.map((d) => {
                const isSelected = d.iso === selectedDate;
                const count = eventCountByDate[d.iso] || 0;
                return (
                  <button
                    key={d.iso}
                    type="button"
                    onClick={() => setSelectedDate(d.iso)}
                    className={`min-w-[60px] min-h-[68px] flex flex-col items-center justify-center rounded-2xl border px-2 py-2 transition-colors shrink-0 ${
                      isSelected
                        ? 'bg-[#1E3A5F] border-[#1E3A5F] text-white'
                        : d.isToday
                        ? 'bg-stone-100 border-stone-300 text-stone-900'
                        : 'bg-white border-stone-200 text-stone-700 hover:bg-stone-50'
                    }`}
                  >
                    <span className="text-[11px] font-medium capitalize opacity-80">
                      {d.dayShort}
                    </span>
                    <span className="font-mono-num text-base font-bold mt-0.5">{d.dayNum}</span>
                    <span
                      className={`text-[10px] font-mono-num mt-0.5 ${
                        isSelected
                          ? 'text-amber-200'
                          : count > 0
                          ? 'text-[#1E3A5F] font-semibold'
                          : 'text-transparent'
                      }`}
                    >
                      {count > 0 ? `${count} rdv` : '—'}
                    </span>
                  </button>
                );
              })}
            </div>
          ) : (
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <button
                  type="button"
                  onClick={() =>
                    setCalendarMonth(
                      new Date(calendarMonth.getFullYear(), calendarMonth.getMonth() - 1, 1)
                    )
                  }
                  className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-xl border border-stone-200 hover:bg-stone-50"
                  aria-label="Mois précédent"
                >
                  <ChevronLeft className="h-4 w-4" />
                </button>
                <span className="text-sm font-semibold text-stone-900 capitalize">
                  {calendarMonth.toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' })}
                </span>
                <button
                  type="button"
                  onClick={() =>
                    setCalendarMonth(
                      new Date(calendarMonth.getFullYear(), calendarMonth.getMonth() + 1, 1)
                    )
                  }
                  className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-xl border border-stone-200 hover:bg-stone-50"
                  aria-label="Mois suivant"
                >
                  <ChevronRight className="h-4 w-4" />
                </button>
              </div>
              <div className="grid grid-cols-7 gap-1 text-center text-[11px] font-medium text-stone-500">
                {['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim'].map((wd) => (
                  <div key={wd} className="py-1">
                    {wd}
                  </div>
                ))}
              </div>
              <div className="grid grid-cols-7 gap-1">
                {monthDays.map((iso, idx) => {
                  if (!iso) return <div key={`empty-${idx}`} className="h-11" />;
                  const dayNum = Number(iso.split('-')[2]);
                  const isSelected = iso === selectedDate;
                  const count = eventCountByDate[iso] || 0;
                  return (
                    <button
                      key={iso}
                      type="button"
                      onClick={() => setSelectedDate(iso)}
                      className={`min-h-[44px] flex flex-col items-center justify-center rounded-xl text-xs font-mono-num transition-colors ${
                        isSelected
                          ? 'bg-[#1E3A5F] text-white font-bold'
                          : iso === todayISO
                          ? 'bg-stone-100 text-stone-900 font-semibold border border-stone-300'
                          : 'hover:bg-stone-50 text-stone-800'
                      }`}
                    >
                      <span>{dayNum}</span>
                      {count > 0 && (
                        <span
                          className={`text-[9px] leading-none mt-0.5 ${
                            isSelected ? 'text-amber-200' : 'text-[#1E3A5F] font-semibold'
                          }`}
                        >
                          {count}
                        </span>
                      )}
                    </button>
                  );
                })}
              </div>
            </div>
          )}

          {/* Events for selected date */}
          <div className="border-t border-stone-200 pt-4">
            <div className="flex items-center justify-between mb-3">
              <h3 className="text-sm font-semibold text-stone-900">
                Prévu le {formatDateFr(selectedDate)}
              </h3>
              <span className="text-xs font-mono-num text-stone-500">
                {dayEvents.length} échéance(s)
              </span>
            </div>

            {dayEvents.length === 0 ? (
              <div className="rounded-xl bg-stone-50 border border-stone-200/70 p-4 text-center text-sm text-stone-500">
                Aucun essayage ni livraison prévu pour cette date.
              </div>
            ) : (
              <div className="divide-y divide-stone-100">
                {dayEvents.map(({ order, eventType }, i) => {
                  const client = db.clients.find((c) => c.id === order.clientId);
                  const model = db.models.find((m) => m.id === order.modelId);
                  return (
                    <button
                      key={`${order.id}-${eventType}-${i}`}
                      type="button"
                      onClick={() => onOpenOrder(order.id)}
                      className="w-full min-h-[60px] py-3 flex items-center justify-between gap-3 text-left hover:bg-stone-50 px-2 rounded-xl transition-colors"
                    >
                      <div className="flex items-center gap-3 min-w-0">
                        <ResilientImage
                          src={order.fabricPhotoUrl || model?.photoUrl}
                          alt={model?.name || 'Modèle'}
                          className="h-11 w-11 rounded-xl object-cover shrink-0 border border-stone-200"
                        />
                        <div className="min-w-0">
                          <p className="text-sm font-semibold text-stone-900 truncate">
                            {client?.fullName || 'Client'}
                          </p>
                          <p className="text-xs text-stone-500 truncate">
                            {eventType} · {model?.name} · {order.status}
                          </p>
                        </div>
                      </div>
                      <span className="font-mono-num text-xs font-semibold text-[#1E3A5F] shrink-0">
                        {order.reference}
                      </span>
                    </button>
                  );
                })}
              </div>
            )}
          </div>
        </section>

        {/* Right Column: Priority Alerts & Low Stock */}
        <div className="lg:col-span-5 space-y-6">
          <section className="rounded-2xl border border-stone-200 bg-white p-4 sm:p-5">
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-2">
                <Clock className="h-5 w-5 text-red-700" />
                <h2 className="font-display text-lg font-semibold text-stone-900">
                  Livraisons prioritaires
                </h2>
              </div>
              <span className="text-xs text-stone-500">Retards &amp; 48h</span>
            </div>

            {urgentOrders.length === 0 ? (
              <p className="text-sm text-stone-500 py-3">
                Toutes les commandes sont dans les délais.
              </p>
            ) : (
              <div className="divide-y divide-stone-100">
                {urgentOrders.map(({ order, urgency }) => {
                  const client = db.clients.find((c) => c.id === order.clientId);
                  const model = db.models.find((m) => m.id === order.modelId);
                  const balance = Math.max(0, order.totalPrice - order.advancePaid);
                  return (
                    <button
                      key={order.id}
                      type="button"
                      onClick={() => onOpenOrder(order.id)}
                      className="w-full py-3 flex items-center justify-between gap-3 text-left hover:bg-stone-50 px-2 rounded-xl transition-colors min-h-[60px]"
                    >
                      <div className="min-w-0">
                        <div className="flex items-center gap-2">
                          <AlertTriangle
                            className={`h-4 w-4 shrink-0 ${
                              urgency.level === 'overdue' ? 'text-red-700' : 'text-amber-700'
                            }`}
                          />
                          <span
                            className={`text-xs font-semibold ${
                              urgency.level === 'overdue' ? 'text-red-700' : 'text-amber-800'
                            }`}
                          >
                            {urgency.label}
                          </span>
                          <span className="text-stone-300">·</span>
                          <span className="text-xs text-stone-500 font-mono-num">
                            {order.status}
                          </span>
                        </div>
                        <p className="text-sm font-semibold text-stone-900 truncate mt-0.5">
                          {client?.fullName} — {model?.name}
                        </p>
                      </div>
                      <div className="text-right shrink-0">
                        <p className="font-mono-num text-xs font-semibold text-stone-900">
                          {formatDateFr(order.deliveryDate)}
                        </p>
                        {balance > 0 && (
                          <p className="font-mono-num text-[11px] text-stone-500">
                            Reste {formatMoney(balance, db.settings.currency)}
                          </p>
                        )}
                      </div>
                    </button>
                  );
                })}
              </div>
            )}
          </section>

          <section className="rounded-2xl border border-stone-200 bg-white p-4 sm:p-5">
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-2">
                <Package className="h-5 w-5 text-amber-700" />
                <h2 className="font-display text-lg font-semibold text-stone-900">
                  Stocks mercerie bas
                </h2>
              </div>
              <button
                type="button"
                onClick={() => onNavigateTab('mercerie')}
                className="text-xs font-semibold text-[#1E3A5F] hover:underline min-h-[36px] px-2"
              >
                Gérer stock
              </button>
            </div>

            {lowStockItems.length === 0 ? (
              <p className="text-sm text-stone-500 py-2">
                Tous les articles de mercerie sont au-dessus du seuil critique.
              </p>
            ) : (
              <div className="divide-y divide-stone-100">
                {lowStockItems.map((item) => (
                  <div
                    key={item.id}
                    className="py-2.5 flex items-center justify-between gap-2 text-sm"
                  >
                    <div className="min-w-0">
                      <p className="font-medium text-stone-900 truncate">{item.name}</p>
                      <p className="text-xs text-stone-500">
                        Seuil critique : {item.criticalThreshold} {item.unit}(s)
                      </p>
                    </div>
                    <span className="font-mono-num text-sm font-bold text-amber-800 shrink-0">
                      {item.quantityInStock} {item.unit}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </section>
        </div>
      </div>
    </div>
  );
};
