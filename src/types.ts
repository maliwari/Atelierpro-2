export type GarmentCategory = 'Homme' | 'Femme' | 'Enfant' | 'Cérémonie';

export type OrderStatus =
  | 'En attente'
  | 'Coupe'
  | 'Couture'
  | 'Essayage'
  | 'Prêt'
  | 'Livré';

export const ORDER_WORKFLOW: OrderStatus[] = [
  'En attente',
  'Coupe',
  'Couture',
  'Essayage',
  'Prêt',
  'Livré',
];

export type HaberdasheryUnit = 'pièce' | 'bobine' | 'mètre' | 'paquet';

export type EmployeeRole = 'Tailleur' | 'Coupeur' | 'Brodeur' | 'Apprenti';

export type PayMode = 'fixe' | 'piece' | 'commission';

export type ExpenseCategory =
  | 'Loyer atelier'
  | 'Électricité/Eau'
  | 'Achat Mercerie'
  | 'Salaires'
  | 'Entretien machines'
  | 'Divers';

export const EXPENSE_CATEGORIES: ExpenseCategory[] = [
  'Loyer atelier',
  'Électricité/Eau',
  'Achat Mercerie',
  'Salaires',
  'Entretien machines',
  'Divers',
];

export const STANDARD_MEASUREMENTS: { key: string; label: string; unit: string }[] = [
  { key: 'tour_poitrine', label: 'Tour de poitrine', unit: 'cm' },
  { key: 'tour_taille', label: 'Tour de taille', unit: 'cm' },
  { key: 'tour_hanches', label: 'Tour de hanches', unit: 'cm' },
  { key: 'carrure', label: 'Carrure', unit: 'cm' },
  { key: 'longueur_manche', label: 'Longueur manche', unit: 'cm' },
  { key: 'tour_bras', label: 'Tour de bras', unit: 'cm' },
  { key: 'longueur_totale', label: 'Longueur robe / boubou', unit: 'cm' },
  { key: 'longueur_veste', label: 'Longueur veste / haut', unit: 'cm' },
  { key: 'longueur_pantalon', label: 'Longueur pantalon', unit: 'cm' },
  { key: 'tour_cuisse', label: 'Tour de cuisse', unit: 'cm' },
  { key: 'tour_cou', label: 'Tour de cou', unit: 'cm' },
  { key: 'bas_pantalon', label: 'Bas pantalon', unit: 'cm' },
];

export interface HaberdasheryRecipeItem {
  itemId: string;
  quantity: number;
}

export interface GarmentModel {
  id: string;
  name: string;
  category: GarmentCategory;
  photoUrl: string;
  defaultPrice: number;
  estimatedDays: number;
  measurementKeys: string[];
  customMeasurements?: { key: string; label: string; unit: string }[];
  recipe: HaberdasheryRecipeItem[];
  notes?: string;
  createdAt: string;
}

export interface MeasurementSnapshot {
  date: string;
  orderId?: string;
  values: Record<string, number>;
}

export interface Client {
  id: string;
  fullName: string;
  phone: string; // International format e.g. +22370123456
  address: string;
  notes?: string;
  latestMeasurements: Record<string, number>;
  measurementHistory: MeasurementSnapshot[];
  createdAt: string;
}

export interface Order {
  id: string;
  reference: string;
  clientId: string;
  modelId: string;
  fabricPhotoUrl?: string;
  fabricNotes?: string;
  measurements: Record<string, number>;
  status: OrderStatus;
  stockDeducted: boolean;
  assignedEmployeeId?: string;
  orderDate: string; // YYYY-MM-DD
  fittingDate?: string; // YYYY-MM-DD
  deliveryDate: string; // YYYY-MM-DD
  totalPrice: number;
  advancePaid: number;
  notes?: string;
  createdAt: string;
}

export interface HaberdasheryItem {
  id: string;
  name: string;
  unit: HaberdasheryUnit;
  unitPrice: number;
  quantityInStock: number;
  criticalThreshold: number;
  updatedAt: string;
}

export interface StockMovement {
  id: string;
  itemId: string;
  type: 'entree' | 'sortie_auto' | 'sortie_manuelle';
  quantity: number;
  date: string;
  note: string;
  orderId?: string;
}

export interface Employee {
  id: string;
  fullName: string;
  phone: string;
  role: EmployeeRole;
  payMode: PayMode;
  payRate: number; // Monthly salary if fixe, fixed per garment if piece, % if commission
  active: boolean;
  joinedAt: string;
}

export interface Expense {
  id: string;
  category: ExpenseCategory;
  amount: number;
  date: string; // YYYY-MM-DD
  description: string;
  receiptPhotoUrl?: string;
  createdAt: string;
}

export interface AtelierSettings {
  workshopName: string;
  workshopPhone: string;
  workshopAddress: string;
  workshopLogoUrl?: string;
  currency: 'FCFA' | '€' | 'MAD';
  pinCode: string;
  pinEnabled: boolean;
}

export type LicenseStatusLabel = 'Essai gratuit' | 'Actif' | 'Expiré';

export interface UsedActivationCode {
  code: string;
  durationCode: 'M1' | 'M3' | 'A1';
  daysAdded: number;
  usedAt: string;
  isSynced: 0 | 1;
}

export interface AtelierLicenseState {
  workshopCode: string;
  planType: 'trial' | 'pro';
  expirationDate: string; // YYYY-MM-DD
  lastKnownEpochMs: number;
  lastKnownIso: string;
  isSynced: 0 | 1;
  usedCodes: UsedActivationCode[];
}

export interface AtelierDatabase {
  models: GarmentModel[];
  clients: Client[];
  orders: Order[];
  inventory: HaberdasheryItem[];
  stockMovements: StockMovement[];
  employees: Employee[];
  expenses: Expense[];
  settings: AtelierSettings;
  license?: AtelierLicenseState;
}
