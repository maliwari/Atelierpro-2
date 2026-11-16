import React, { useState } from 'react';
import { Edit3, Phone, Plus, Trash2, Users, X } from 'lucide-react';
import { AtelierDatabase, Employee, EmployeeRole, PayMode } from '../types';
import { formatMoney } from '../utils/formatters';
import { NumericKeypadSheet } from './NumericKeypadSheet';

interface StaffViewProps {
  db: AtelierDatabase;
  onSaveEmployee: (emp: Employee) => void;
  onDeleteEmployee: (empId: string) => void;
}

const ROLES: EmployeeRole[] = ['Tailleur', 'Coupeur', 'Brodeur', 'Apprenti'];

export const StaffView: React.FC<StaffViewProps> = ({ db, onSaveEmployee, onDeleteEmployee }) => {
  const [modalOpen, setModalOpen] = useState(false);
  const [editingEmp, setEditingEmp] = useState<Employee | null>(null);
  const [confirmDeleteId, setConfirmDeleteId] = useState<string | null>(null);

  const [fullName, setFullName] = useState('');
  const [phone, setPhone] = useState('+223');
  const [role, setRole] = useState<EmployeeRole>('Tailleur');
  const [payMode, setPayMode] = useState<PayMode>('piece');
  const [payRate, setPayRate] = useState(8000);
  const [active, setActive] = useState(true);
  const [keypadOpen, setKeypadOpen] = useState(false);

  const openCreate = () => {
    setEditingEmp(null);
    setFullName('');
    setPhone('+223');
    setRole('Tailleur');
    setPayMode('piece');
    setPayRate(8000);
    setActive(true);
    setModalOpen(true);
  };

  const openEdit = (emp: Employee) => {
    setEditingEmp(emp);
    setFullName(emp.fullName);
    setPhone(emp.phone);
    setRole(emp.role);
    setPayMode(emp.payMode);
    setPayRate(emp.payRate);
    setActive(emp.active);
    setModalOpen(true);
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!fullName.trim()) return;
    const emp: Employee = {
      id: editingEmp?.id || `emp-${Date.now()}`,
      fullName: fullName.trim(),
      phone: phone.trim(),
      role,
      payMode,
      payRate,
      active,
      joinedAt: editingEmp?.joinedAt || new Date().toISOString().split('T')[0],
    };
    onSaveEmployee(emp);
    setModalOpen(false);
  };

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="font-display text-2xl font-semibold text-stone-900">
            Équipe &amp; Rémunérations
          </h1>
          <p className="text-xs text-stone-500">
            Suivi de la charge de travail et calcul des gains (fixe, à la pièce ou commission)
          </p>
        </div>
        <button
          type="button"
          onClick={openCreate}
          className="min-h-[44px] px-4 rounded-xl bg-[#1E3A5F] text-white text-sm font-semibold inline-flex items-center gap-2 hover:bg-[#162C49]"
        >
          <Plus className="h-4 w-4" />
          <span>Ajouter salarié</span>
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {db.employees.map((emp) => {
          const assignedOrders = db.orders.filter((o) => o.assignedEmployeeId === emp.id);
          const inProgressCount = assignedOrders.filter(
            (o) => o.status !== 'Prêt' && o.status !== 'Livré'
          ).length;
          const completedOrders = assignedOrders.filter(
            (o) => o.status === 'Prêt' || o.status === 'Livré'
          );

          const computedPay =
            emp.payMode === 'fixe'
              ? emp.payRate
              : emp.payMode === 'piece'
              ? completedOrders.length * emp.payRate
              : completedOrders.reduce((s, o) => s + Math.round((o.totalPrice * emp.payRate) / 100), 0);

          return (
            <div
              key={emp.id}
              className="rounded-2xl border border-stone-200 bg-white p-4 sm:p-5 space-y-4"
            >
              <div className="flex items-start justify-between gap-3">
                <div className="flex items-center gap-3 min-w-0">
                  <div className="h-11 w-11 rounded-2xl bg-[#1E3A5F]/10 text-[#1E3A5F] flex items-center justify-center shrink-0">
                    <Users className="h-5 w-5" />
                  </div>
                  <div className="min-w-0">
                    <h3 className="font-display text-lg font-semibold text-stone-900 truncate">
                      {emp.fullName}
                    </h3>
                    <p className="text-xs text-stone-500 flex items-center gap-2">
                      <span>{emp.role}</span>
                      <span>·</span>
                      <span className="font-mono-num inline-flex items-center gap-1">
                        <Phone className="h-3 w-3" />
                        {emp.phone}
                      </span>
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-1">
                  <button
                    type="button"
                    onClick={() => openEdit(emp)}
                    className="min-h-[40px] min-w-[40px] flex items-center justify-center rounded-xl border border-stone-200 text-stone-600 hover:bg-stone-50"
                    aria-label="Modifier"
                  >
                    <Edit3 className="h-4 w-4" />
                  </button>
                  {confirmDeleteId === emp.id ? (
                    <button
                      type="button"
                      onClick={() => {
                        onDeleteEmployee(emp.id);
                        setConfirmDeleteId(null);
                      }}
                      className="min-h-[40px] px-2.5 rounded-xl bg-red-700 text-white text-xs font-semibold"
                    >
                      Suppr.
                    </button>
                  ) : (
                    <button
                      type="button"
                      onClick={() => setConfirmDeleteId(emp.id)}
                      className="min-h-[40px] min-w-[40px] flex items-center justify-center rounded-xl border border-stone-200 text-red-700 hover:bg-red-50"
                      aria-label="Supprimer"
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  )}
                </div>
              </div>

              <div className="grid grid-cols-3 gap-2 rounded-xl bg-stone-50 border border-stone-200/80 p-3">
                <div>
                  <span className="block text-[11px] text-stone-500">En atelier</span>
                  <span className="font-mono-num text-base font-bold text-stone-900">
                    {inProgressCount} cmd
                  </span>
                </div>
                <div>
                  <span className="block text-[11px] text-stone-500">Terminées</span>
                  <span className="font-mono-num text-base font-bold text-emerald-700">
                    {completedOrders.length} tenues
                  </span>
                </div>
                <div>
                  <span className="block text-[11px] text-stone-500">
                    {emp.payMode === 'fixe' ? 'Salaire mensuel' : 'Gains calculés'}
                  </span>
                  <span className="font-mono-num text-base font-bold text-[#1E3A5F]">
                    {formatMoney(computedPay, db.settings.currency)}
                  </span>
                </div>
              </div>

              <p className="text-xs text-stone-500">
                Mode de rémunération :{' '}
                <strong className="text-stone-800">
                  {emp.payMode === 'fixe'
                    ? `Salaire fixe (${formatMoney(emp.payRate, db.settings.currency)} / mois)`
                    : emp.payMode === 'piece'
                    ? `À la pièce (${formatMoney(emp.payRate, db.settings.currency)} / tenue)`
                    : `Commission (${emp.payRate}% par tenue terminée)`}
                </strong>
              </p>
            </div>
          );
        })}
      </div>

      {modalOpen && (
        <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/50 backdrop-blur-xs">
          <div className="w-full max-w-md rounded-t-3xl sm:rounded-2xl bg-white p-5 shadow-2xl">
            <div className="flex items-center justify-between mb-4">
              <h2 className="font-display text-xl font-semibold text-stone-900">
                {editingEmp ? 'Modifier la fiche salarié' : 'Nouveau salarié'}
              </h2>
              <button
                type="button"
                onClick={() => setModalOpen(false)}
                className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-xl text-stone-500"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <form onSubmit={handleSubmit} className="space-y-4">
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
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-stone-700 mb-1">
                    Téléphone
                  </label>
                  <input
                    type="tel"
                    required
                    value={phone}
                    onChange={(e) => setPhone(e.target.value)}
                    className="w-full min-h-[44px] rounded-xl border border-stone-300 px-3 text-sm font-mono-num"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-stone-700 mb-1">Poste</label>
                  <select
                    value={role}
                    onChange={(e) => setRole(e.target.value as EmployeeRole)}
                    className="w-full min-h-[44px] rounded-xl border border-stone-300 px-3 text-sm bg-white"
                  >
                    {ROLES.map((r) => (
                      <option key={r} value={r}>
                        {r}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-stone-700 mb-1">
                  Mode de rémunération
                </label>
                <div className="grid grid-cols-3 gap-1.5">
                  {[
                    { id: 'fixe', label: 'Fixe mensuel' },
                    { id: 'piece', label: 'À la pièce' },
                    { id: 'commission', label: 'Commission %' },
                  ].map((m) => (
                    <button
                      key={m.id}
                      type="button"
                      onClick={() => setPayMode(m.id as PayMode)}
                      className={`min-h-[42px] px-2 rounded-xl text-xs font-semibold border ${
                        payMode === m.id
                          ? 'bg-[#1E3A5F] text-white border-[#1E3A5F]'
                          : 'bg-stone-50 text-stone-700 border-stone-200'
                      }`}
                    >
                      {m.label}
                    </button>
                  ))}
                </div>
              </div>

              <button
                type="button"
                onClick={() => setKeypadOpen(true)}
                className="w-full min-h-[60px] rounded-xl border border-stone-200 bg-stone-50 px-3.5 py-2 text-left"
              >
                <span className="block text-[11px] text-stone-500">
                  {payMode === 'fixe'
                    ? 'Montant mensuel'
                    : payMode === 'piece'
                    ? 'Montant fixe par tenue'
                    : 'Pourcentage par tenue (%)'}
                </span>
                <span className="font-mono-num text-lg font-bold text-stone-900">
                  {payMode === 'commission'
                    ? `${payRate} %`
                    : formatMoney(payRate, db.settings.currency)}
                </span>
              </button>

              <button
                type="submit"
                className="w-full min-h-[48px] rounded-xl bg-[#1E3A5F] text-white text-sm font-semibold"
              >
                Enregistrer
              </button>
            </form>
          </div>
        </div>
      )}

      <NumericKeypadSheet
        isOpen={keypadOpen}
        title={
          payMode === 'commission' ? 'Pourcentage de commission' : 'Montant de rémunération'
        }
        unit={payMode === 'commission' ? '%' : db.settings.currency}
        initialValue={payRate}
        allowDecimal={false}
        step={payMode === 'commission' ? 5 : 1000}
        onConfirm={setPayRate}
        onClose={() => setKeypadOpen(false)}
      />
    </div>
  );
};
