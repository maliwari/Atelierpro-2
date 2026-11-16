import React, { useEffect, useState } from 'react';
import {
  BookOpen,
  CalendarDays,
  CheckCircle2,
  ClipboardList,
  Lock,
  Package,
  Plus,
  Scissors,
  ShieldCheck,
  Users,
  Wallet,
} from 'lucide-react';
import {
  AtelierDatabase,
  AtelierSettings,
  Client,
  Employee,
  Expense,
  GarmentModel,
  HaberdasheryItem,
  Order,
  OrderStatus,
  StockMovement,
} from './types';
import {
  loadAtelierDatabase,
  resetAtelierDatabase,
  saveAtelierDatabase,
} from './db/atelierDb';
import { HomeAgendaView } from './components/HomeAgendaView';
import { OrdersView } from './components/OrdersView';
import { OrderFormSheet } from './components/OrderFormSheet';
import { CatalogView } from './components/CatalogView';
import { ClientsView } from './components/ClientsView';
import { InventoryView } from './components/InventoryView';
import { FinanceView } from './components/FinanceView';
import { OfflineIndicator } from './components/PWAInstallButton';
import { ClockTamperView, SubscriptionView } from './components/SubscriptionView';
import {
  activateLicenseCode,
  checkAndAdvanceClock,
  createInitialLicenseState,
  evaluateLicense,
} from './utils/license';

type MainTab =
  | 'accueil'
  | 'commandes'
  | 'catalogue'
  | 'clients'
  | 'mercerie'
  | 'equipe'
  | 'finances'
  | 'sauvegarde'
  | 'abonnement';

export default function App() {
  const [db, setDb] = useState<AtelierDatabase | null>(null);
  const [activeTab, setActiveTab] = useState<MainTab>('accueil');
  const [selectedOrderId, setSelectedOrderId] = useState<string | null>(null);
  const [orderFormOpen, setOrderFormOpen] = useState(false);
  const [editingOrder, setEditingOrder] = useState<Order | null>(null);
  const [financeUnlocked, setFinanceUnlocked] = useState(false);
  const [clockTampered, setClockTampered] = useState(false);
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  const verifyClock = (loaded: AtelierDatabase): AtelierDatabase => {
    const baseLicense = loaded.license ?? createInitialLicenseState();
    const { updated, isClockTampered } = checkAndAdvanceClock(baseLicense);
    setClockTampered(isClockTampered);
    if (updated !== loaded.license) {
      const next = { ...loaded, license: updated };
      void saveAtelierDatabase(next);
      return next;
    }
    return loaded;
  };

  useEffect(() => {
    loadAtelierDatabase().then((loaded) => {
      const checked = verifyClock(loaded);
      setDb(checked);
      if (checked.orders.length > 0) {
        setSelectedOrderId(checked.orders[0].id);
      }
    });
  }, []);

  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => {
      setToastMessage((prev) => (prev === msg ? null : prev));
    }, 4000);
  };

  const commitDb = (updater: (prev: AtelierDatabase) => AtelierDatabase) => {
    setDb((prev) => {
      if (!prev) return prev;
      const next = updater(prev);
      void saveAtelierDatabase(next);
      return next;
    });
  };

  if (!db) {
    return (
      <div className="min-h-screen bg-[#FAF9F6] flex flex-col items-center justify-center p-6 text-center">
        <Scissors className="h-8 w-8 text-[#1E3A5F] mb-3" />
        <p className="font-display text-lg font-semibold text-stone-900">AtelierPro</p>
        <p className="text-xs text-stone-500 mt-1">Chargement de la base locale hors-ligne…</p>
      </div>
    );
  }

  const currentLicense = db.license ?? createInitialLicenseState();
  const evaluatedLicense = evaluateLicense(currentLicense, clockTampered);

  const handleActivateLicenseCode = async (rawCode: string) => {
    const res = await activateLicenseCode(currentLicense, rawCode);
    if (res.ok) {
      commitDb((prev) => ({ ...prev, license: res.nextLicense }));
      setClockTampered(false);
      showToast(`Licence activée jusqu’au ${res.nextLicense.expirationDate}.`);
    }
    return res;
  };

  if (evaluatedLicense.isClockTampered) {
    return (
      <ClockTamperView
        license={evaluatedLicense}
        onRecheckClock={() => setDb((prev) => (prev ? verifyClock(prev) : prev))}
      />
    );
  }

  if (evaluatedLicense.status === 'Expiré') {
    return (
      <SubscriptionView
        licenseState={currentLicense}
        evaluated={evaluatedLicense}
        isBlocking
        onActivateCode={handleActivateLicenseCode}
      />
    );
  }

  // Order handlers (including automatic haberdashery deduction on Coupe!)
  const handleSaveOrder = (order: Order, updatedClientMeasurements?: Record<string, number>) => {
    const todayISO = new Date().toISOString().split('T')[0];
    commitDb((prev) => {
      const exists = prev.orders.some((o) => o.id === order.id);
      const nextOrders = exists
        ? prev.orders.map((o) => (o.id === order.id ? order : o))
        : [order, ...prev.orders];

      let nextClients = prev.clients;
      if (updatedClientMeasurements && Object.keys(updatedClientMeasurements).length > 0) {
        nextClients = prev.clients.map((c) => {
          if (c.id !== order.clientId) return c;
          const merged = { ...c.latestMeasurements, ...updatedClientMeasurements };
          return {
            ...c,
            latestMeasurements: merged,
            measurementHistory: [
              { date: todayISO, orderId: order.id, values: { ...updatedClientMeasurements } },
              ...c.measurementHistory,
            ].slice(0, 12),
          };
        });
      }
      return { ...prev, orders: nextOrders, clients: nextClients };
    });
    setSelectedOrderId(order.id);
    setActiveTab('commandes');
    showToast(`Commande ${order.reference} enregistrée.`);
  };

  const handleUpdateOrderStatus = (orderId: string, nextStatus: OrderStatus) => {
    const todayISO = new Date().toISOString().split('T')[0];
    let deductedSummary = '';
    let lowStockAlert = '';

    commitDb((prev) => {
      const target = prev.orders.find((o) => o.id === orderId);
      if (!target) return prev;
      const model = prev.models.find((m) => m.id === target.modelId);

      let nextInventory = prev.inventory;
      let nextMovements = prev.stockMovements;
      let stockDeducted = target.stockDeducted;

      // Automatic stock deduction when reaching Coupe for the first time
      const shouldDeduct =
        !target.stockDeducted &&
        (nextStatus === 'Coupe' ||
          nextStatus === 'Couture' ||
          nextStatus === 'Essayage' ||
          nextStatus === 'Prêt' ||
          nextStatus === 'Livré');

      if (shouldDeduct && model && model.recipe.length > 0) {
        const createdMovements: StockMovement[] = [];
        const lowItems: string[] = [];

        nextInventory = prev.inventory.map((invItem) => {
          const rec = model.recipe.find((r) => r.itemId === invItem.id);
          if (!rec) return invItem;
          const nextQty = Math.max(0, invItem.quantityInStock - rec.quantity);
          if (nextQty <= invItem.criticalThreshold) {
            lowItems.push(invItem.name);
          }
          createdMovements.push({
            id: `mov-${Date.now()}-${invItem.id}`,
            itemId: invItem.id,
            type: 'sortie_auto',
            quantity: rec.quantity,
            date: todayISO,
            note: `Déduction coupe ${target.reference}`,
            orderId: target.id,
          });
          return { ...invItem, quantityInStock: nextQty, updatedAt: todayISO };
        });

        nextMovements = [...createdMovements, ...prev.stockMovements];
        stockDeducted = true;
        deductedSummary = `${createdMovements.length} fourniture(s) déduite(s) du stock.`;
        if (lowItems.length > 0) {
          lowStockAlert = ` Stock bas : ${lowItems.join(', ')}.`;
        }
      }

      return {
        ...prev,
        inventory: nextInventory,
        stockMovements: nextMovements,
        orders: prev.orders.map((o) =>
          o.id === orderId ? { ...o, status: nextStatus, stockDeducted } : o
        ),
      };
    });

    if (deductedSummary) {
      showToast(`Statut : ${nextStatus}. ${deductedSummary}${lowStockAlert}`);
    } else {
      showToast(`Statut mis à jour : ${nextStatus}`);
    }
  };

  const handleRecordPayment = (orderId: string, newAdvancePaid: number) => {
    commitDb((prev) => ({
      ...prev,
      orders: prev.orders.map((o) =>
        o.id === orderId ? { ...o, advancePaid: newAdvancePaid } : o
      ),
    }));
    showToast('Paiement mis à jour.');
  };

  const handleDeleteOrder = (orderId: string) => {
    commitDb((prev) => ({
      ...prev,
      orders: prev.orders.filter((o) => o.id !== orderId),
    }));
    setSelectedOrderId(null);
    showToast('Commande supprimée.');
  };

  // Models
  const handleSaveModel = (model: GarmentModel) => {
    commitDb((prev) => {
      const exists = prev.models.some((m) => m.id === model.id);
      return {
        ...prev,
        models: exists
          ? prev.models.map((m) => (m.id === model.id ? model : m))
          : [model, ...prev.models],
      };
    });
    showToast(`Modèle « ${model.name} » enregistré.`);
  };

  const handleDeleteModel = (modelId: string) => {
    commitDb((prev) => ({
      ...prev,
      models: prev.models.filter((m) => m.id !== modelId),
    }));
    showToast('Modèle supprimé.');
  };

  // Clients
  const handleSaveClient = (client: Client) => {
    commitDb((prev) => {
      const exists = prev.clients.some((c) => c.id === client.id);
      return {
        ...prev,
        clients: exists
          ? prev.clients.map((c) => (c.id === client.id ? client : c))
          : [client, ...prev.clients],
      };
    });
    showToast(`Fiche client « ${client.fullName} » enregistrée.`);
  };

  const handleDeleteClient = (clientId: string) => {
    commitDb((prev) => ({
      ...prev,
      clients: prev.clients.filter((c) => c.id !== clientId),
    }));
    showToast('Client supprimé.');
  };

  // Inventory
  const handleSaveInventoryItem = (item: HaberdasheryItem) => {
    commitDb((prev) => {
      const exists = prev.inventory.some((i) => i.id === item.id);
      return {
        ...prev,
        inventory: exists
          ? prev.inventory.map((i) => (i.id === item.id ? item : i))
          : [item, ...prev.inventory],
      };
    });
    showToast(`Fourniture « ${item.name} » enregistrée.`);
  };

  const handleAdjustStock = (
    itemId: string,
    delta: number,
    note: string,
    recordAsExpense: boolean
  ) => {
    const todayISO = new Date().toISOString().split('T')[0];
    commitDb((prev) => {
      const item = prev.inventory.find((i) => i.id === itemId);
      if (!item) return prev;
      const nextQty = Math.max(0, item.quantityInStock + delta);
      const movement: StockMovement = {
        id: `mov-${Date.now()}`,
        itemId,
        type: delta >= 0 ? 'entree' : 'sortie_manuelle',
        quantity: Math.abs(delta),
        date: todayISO,
        note,
      };
      let nextExpenses = prev.expenses;
      if (recordAsExpense && delta > 0) {
        const exp: Expense = {
          id: `exp-${Date.now()}`,
          category: 'Achat Mercerie',
          amount: delta * item.unitPrice,
          date: todayISO,
          description: `Achat ${delta} ${item.unit}(s) — ${item.name}`,
          createdAt: todayISO,
        };
        nextExpenses = [exp, ...prev.expenses];
      }
      return {
        ...prev,
        inventory: prev.inventory.map((i) =>
          i.id === itemId ? { ...i, quantityInStock: nextQty, updatedAt: todayISO } : i
        ),
        stockMovements: [movement, ...prev.stockMovements],
        expenses: nextExpenses,
      };
    });
    showToast('Stock mis à jour.');
  };

  // Staff
  const handleSaveEmployee = (emp: Employee) => {
    commitDb((prev) => {
      const exists = prev.employees.some((e) => e.id === emp.id);
      return {
        ...prev,
        employees: exists
          ? prev.employees.map((e) => (e.id === emp.id ? emp : e))
          : [emp, ...prev.employees],
      };
    });
    showToast(`Salarié « ${emp.fullName} » enregistré.`);
  };

  const handleDeleteEmployee = (empId: string) => {
    commitDb((prev) => ({
      ...prev,
      employees: prev.employees.filter((e) => e.id !== empId),
    }));
    showToast('Salarié supprimé.');
  };

  // Expenses & Settings
  const handleSaveExpense = (exp: Expense) => {
    commitDb((prev) => ({
      ...prev,
      expenses: [exp, ...prev.expenses],
    }));
    showToast('Dépense enregistrée.');
  };

  const handleDeleteExpense = (expId: string) => {
    commitDb((prev) => ({
      ...prev,
      expenses: prev.expenses.filter((e) => e.id !== expId),
    }));
    showToast('Dépense supprimée.');
  };

  const handleUpdateSettings = (settings: AtelierSettings) => {
    commitDb((prev) => ({ ...prev, settings }));
  };

  const handleImportDatabase = (newDb: AtelierDatabase) => {
    setDb(newDb);
    void saveAtelierDatabase(newDb);
  };

  const handleResetDatabase = async () => {
    const fresh = await resetAtelierDatabase();
    setDb(fresh);
  };

  const isCatalogueFamily =
    activeTab === 'catalogue' || activeTab === 'clients' || activeTab === 'mercerie';
  const isFinanceFamily =
    activeTab === 'finances' ||
    activeTab === 'equipe' ||
    activeTab === 'sauvegarde' ||
    activeTab === 'abonnement';

  const desktopNavItems: { id: MainTab; label: string; icon: React.ReactNode; locked?: boolean }[] = [
    { id: 'accueil', label: 'Accueil & Agenda', icon: <CalendarDays className="h-4 w-4" /> },
    { id: 'commandes', label: 'Commandes', icon: <ClipboardList className="h-4 w-4" /> },
    { id: 'catalogue', label: 'Modèles', icon: <BookOpen className="h-4 w-4" /> },
    { id: 'clients', label: 'Clients & Mesures', icon: <Users className="h-4 w-4" /> },
    { id: 'mercerie', label: 'Mercerie & Stocks', icon: <Package className="h-4 w-4" /> },
    {
      id: 'equipe',
      label: 'Équipe (RH)',
      icon: <Users className="h-4 w-4" />,
      locked: db.settings.pinEnabled && !financeUnlocked,
    },
    {
      id: 'finances',
      label: 'Finances & Rentabilité',
      icon: <Wallet className="h-4 w-4" />,
      locked: db.settings.pinEnabled && !financeUnlocked,
    },
    {
      id: 'sauvegarde',
      label: 'Sauvegarde & PIN',
      icon: <ShieldCheck className="h-4 w-4" />,
      locked: db.settings.pinEnabled && !financeUnlocked,
    },
    {
      id: 'abonnement',
      label: 'Abonnement',
      icon: <ShieldCheck className="h-4 w-4" />,
    },
  ];

  return (
    <div className="min-h-screen bg-[#FAF9F6] text-stone-900 flex flex-col lg:flex-row">
      {/* DESKTOP / TABLET LANDSCAPE SIDEBAR RAIL */}
      <aside className="hidden lg:flex lg:flex-col lg:w-64 lg:fixed lg:inset-y-0 border-r border-stone-200 bg-white z-30">
        <div className="px-5 py-5 border-b border-stone-200">
          <div className="flex items-center gap-2.5">
            <div className="h-9 w-9 rounded-xl bg-[#1E3A5F] text-white flex items-center justify-center shrink-0">
              <Scissors className="h-5 w-5" />
            </div>
            <div className="min-w-0">
              <span className="font-display text-lg font-bold text-stone-900 block leading-tight">
                AtelierPro
              </span>
              <span className="text-[11px] text-stone-500 truncate block">
                {db.settings.workshopName}
              </span>
            </div>
          </div>
        </div>

        <nav className="flex-1 p-3 space-y-1 overflow-y-auto">
          {desktopNavItems.map((item) => {
            const active = activeTab === item.id;
            return (
              <button
                key={item.id}
                type="button"
                onClick={() => setActiveTab(item.id)}
                className={`w-full min-h-[44px] px-3.5 rounded-xl flex items-center justify-between gap-2 text-sm font-medium transition-colors ${
                  active
                    ? 'bg-[#1E3A5F] text-white font-semibold'
                    : 'text-stone-700 hover:bg-stone-100'
                }`}
              >
                <span className="flex items-center gap-2.5 truncate">
                  {item.icon}
                  <span className="truncate">{item.label}</span>
                </span>
                {item.locked && <Lock className="h-3.5 w-3.5 opacity-70 shrink-0" />}
              </button>
            );
          })}
        </nav>

        <div className="p-4 border-t border-stone-200 space-y-2.5">
          <button
            type="button"
            onClick={() => {
              setEditingOrder(null);
              setOrderFormOpen(true);
            }}
            className="w-full min-h-[46px] rounded-xl bg-stone-900 text-white text-xs font-semibold flex items-center justify-center gap-2 hover:bg-stone-800"
          >
            <Plus className="h-4 w-4" />
            <span>Prise de commande</span>
          </button>
        </div>
      </aside>

      {/* MOBILE / TABLET PORTRAIT TOP BAR (Compact 54px) */}
      <header className="lg:hidden sticky top-0 z-30 h-14 bg-white/95 backdrop-blur-md border-b border-stone-200 px-4 flex items-center justify-between gap-4">
        <button
          type="button"
          onClick={() => setActiveTab('accueil')}
          className="font-display text-lg font-bold text-stone-900 whitespace-nowrap shrink-0 flex items-center gap-2"
        >
          <Scissors className="h-4 w-4 text-[#1E3A5F]" />
          <span>AtelierPro</span>
        </button>

        <div className="flex items-center gap-2 shrink-0">
          <button
            type="button"
            onClick={() => {
              setEditingOrder(null);
              setOrderFormOpen(true);
            }}
            className="min-h-[40px] px-3 rounded-xl bg-[#1E3A5F] text-white text-xs font-semibold inline-flex items-center gap-1 whitespace-nowrap"
          >
            <Plus className="h-4 w-4" />
            <span>Commande</span>
          </button>
        </div>
      </header>

      {/* MAIN CONTENT AREA */}
      <main className="flex-1 lg:pl-64 pb-24 lg:pb-10">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-5 sm:pt-6">
          {/* Mobile Sub-navigation for Catalogue / Clients / Mercerie */}
          {isCatalogueFamily && (
            <div className="lg:hidden flex items-center gap-1.5 mb-5 p-1 bg-stone-200/70 rounded-xl">
              {[
                { id: 'catalogue', label: 'Modèles' },
                { id: 'clients', label: 'Clients' },
                { id: 'mercerie', label: 'Mercerie' },
              ].map((t) => (
                <button
                  key={t.id}
                  type="button"
                  onClick={() => setActiveTab(t.id as MainTab)}
                  className={`flex-1 min-h-[40px] rounded-lg text-xs font-semibold transition-colors ${
                    activeTab === t.id
                      ? 'bg-white text-stone-900 shadow-xs'
                      : 'text-stone-600'
                  }`}
                >
                  {t.label}
                </button>
              ))}
            </div>
          )}

          {activeTab === 'accueil' && (
            <HomeAgendaView
              db={db}
              onNewOrder={() => {
                setEditingOrder(null);
                setOrderFormOpen(true);
              }}
              onOpenOrder={(orderId) => {
                setSelectedOrderId(orderId);
                setActiveTab('commandes');
              }}
              onNavigateTab={(tab) => setActiveTab(tab as MainTab)}
            />
          )}

          {activeTab === 'commandes' && (
            <OrdersView
              db={db}
              selectedOrderId={selectedOrderId}
              onSelectOrder={setSelectedOrderId}
              onNewOrder={() => {
                setEditingOrder(null);
                setOrderFormOpen(true);
              }}
              onEditOrder={(order) => {
                setEditingOrder(order);
                setOrderFormOpen(true);
              }}
              onUpdateStatus={handleUpdateOrderStatus}
              onRecordPayment={handleRecordPayment}
              onDeleteOrder={handleDeleteOrder}
            />
          )}

          {activeTab === 'catalogue' && (
            <CatalogView
              db={db}
              onSaveModel={handleSaveModel}
              onDeleteModel={handleDeleteModel}
            />
          )}

          {activeTab === 'clients' && (
            <ClientsView
              db={db}
              onSaveClient={handleSaveClient}
              onDeleteClient={handleDeleteClient}
              onOpenOrder={(orderId) => {
                setSelectedOrderId(orderId);
                setActiveTab('commandes');
              }}
            />
          )}

          {activeTab === 'mercerie' && (
            <InventoryView
              db={db}
              onSaveItem={handleSaveInventoryItem}
              onAdjustStock={handleAdjustStock}
            />
          )}

          {isFinanceFamily && (
            <FinanceView
              db={db}
              licenseState={currentLicense}
              evaluatedLicense={evaluatedLicense}
              isUnlocked={financeUnlocked}
              initialSubTab={
                activeTab === 'equipe'
                  ? 'equipe'
                  : activeTab === 'sauvegarde'
                  ? 'sauvegarde'
                  : activeTab === 'abonnement'
                  ? 'abonnement'
                  : 'dashboard'
              }
              onUnlock={() => setFinanceUnlocked(true)}
              onLock={() => setFinanceUnlocked(false)}
              onSaveExpense={handleSaveExpense}
              onDeleteExpense={handleDeleteExpense}
              onSaveEmployee={handleSaveEmployee}
              onDeleteEmployee={handleDeleteEmployee}
              onUpdateSettings={handleUpdateSettings}
              onActivateLicenseCode={handleActivateLicenseCode}
              onImportDatabase={handleImportDatabase}
              onResetDatabase={handleResetDatabase}
              onNotify={showToast}
            />
          )}
        </div>
      </main>

      {/* MOBILE BOTTOM TAB BAR (4 primary destinations per Cahier des Charges) */}
      <nav
        aria-label="Navigation principale"
        className="lg:hidden fixed bottom-0 left-0 right-0 z-40 bg-white/95 backdrop-blur-md border-t border-stone-200 pb-safe"
      >
        <div className="grid grid-cols-4 h-16 items-center">
          {[
            { id: 'accueil', label: 'Accueil', icon: CalendarDays, active: activeTab === 'accueil' },
            {
              id: 'commandes',
              label: 'Commandes',
              icon: ClipboardList,
              active: activeTab === 'commandes',
            },
            {
              id: 'catalogue',
              label: 'Catalogue',
              icon: BookOpen,
              active: isCatalogueFamily,
            },
            {
              id: 'finances',
              label: 'Finances',
              icon: Wallet,
              active: isFinanceFamily,
            },
          ].map((tab) => {
            const Icon = tab.icon;
            return (
              <button
                key={tab.id}
                type="button"
                onClick={() => setActiveTab(tab.id as MainTab)}
                className={`min-h-[48px] flex flex-col items-center justify-center gap-0.5 transition-colors ${
                  tab.active ? 'text-[#1E3A5F]' : 'text-stone-500 hover:text-stone-800'
                }`}
              >
                <Icon className="h-5 w-5" strokeWidth={tab.active ? 2.4 : 1.8} />
                <span className={`text-[11px] tracking-tight ${tab.active ? 'font-bold' : 'font-medium'}`}>
                  {tab.label}
                </span>
              </button>
            );
          })}
        </div>
      </nav>

      {/* Order Form Sheet */}
      <OrderFormSheet
        isOpen={orderFormOpen}
        db={db}
        initialOrder={editingOrder}
        onSaveOrder={handleSaveOrder}
        onQuickAddClient={handleSaveClient}
        onClose={() => {
          setOrderFormOpen(false);
          setEditingOrder(null);
        }}
      />

      {/* Offline Status & Feedback Toast */}
      <OfflineIndicator />

      {toastMessage && (
        <div
          role="status"
          className="fixed bottom-20 lg:bottom-6 right-4 z-50 max-w-sm rounded-xl bg-stone-900 text-white px-4 py-3 text-xs font-medium shadow-lg flex items-center gap-2.5"
        >
          <CheckCircle2 className="h-4 w-4 text-emerald-400 shrink-0" />
          <span>{toastMessage}</span>
        </div>
      )}
    </div>
  );
}
