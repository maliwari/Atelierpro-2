import { AtelierSettings, Order } from '../types';

export function formatMoney(amount: number, currency: AtelierSettings['currency']): string {
  const formatted = new Intl.NumberFormat('fr-FR', {
    maximumFractionDigits: currency === '€' ? 2 : 0,
  }).format(amount);
  return `${formatted} ${currency}`;
}

export function formatDateFr(isoDate?: string): string {
  if (!isoDate) return '—';
  const [y, m, d] = isoDate.split('-').map(Number);
  if (!y || !m || !d) return isoDate;
  const date = new Date(y, m - 1, d);
  return date.toLocaleDateString('fr-FR', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  });
}

export function formatShortDateFr(isoDate?: string): string {
  if (!isoDate) return '—';
  const [y, m, d] = isoDate.split('-').map(Number);
  if (!y || !m || !d) return isoDate;
  const date = new Date(y, m - 1, d);
  return date.toLocaleDateString('fr-FR', {
    day: '2-digit',
    month: 'short',
  });
}

export type UrgencyLevel = 'overdue' | 'urgent_48h' | 'normal' | 'done';

export function getOrderUrgency(order: Order): {
  level: UrgencyLevel;
  label: string;
  daysDiff: number;
} {
  if (order.status === 'Livré') {
    return { level: 'done', label: 'Livré', daysDiff: 999 };
  }
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const [y, m, d] = order.deliveryDate.split('-').map(Number);
  const target = new Date(y, m - 1, d);
  target.setHours(0, 0, 0, 0);
  const daysDiff = Math.round((target.getTime() - today.getTime()) / (1000 * 60 * 60 * 24));

  if (daysDiff < 0) {
    return {
      level: 'overdue',
      label: `En retard (${Math.abs(daysDiff)}j)`,
      daysDiff,
    };
  }
  if (daysDiff <= 2) {
    return {
      level: 'urgent_48h',
      label: daysDiff === 0 ? 'Livraison aujourd’hui' : `Sous ${daysDiff * 24}h`,
      daysDiff,
    };
  }
  return {
    level: 'normal',
    label: `Dans ${daysDiff}j`,
    daysDiff,
  };
}

export type WhatsAppTemplateType = 'confirmation' | 'essayage' | 'pret' | 'custom';

export function buildWhatsAppUrl(
  phone: string,
  template: WhatsAppTemplateType,
  params: {
    clientName: string;
    workshopName: string;
    modelName?: string;
    reference?: string;
    deliveryDate?: string;
    fittingDate?: string;
    balanceDue?: number;
    currency?: AtelierSettings['currency'];
    customText?: string;
  }
): string {
  const cleanPhone = phone.replace(/[^0-9]/g, '');
  let message = '';
  if (template === 'confirmation') {
    message = `Bonjour ${params.clientName}, votre commande ${params.reference || ''} (${params.modelName || 'tenue sur mesure'}) est enregistrée à ${params.workshopName}. Livraison prévue le ${formatDateFr(params.deliveryDate)}. Merci de votre confiance !`;
  } else if (template === 'essayage') {
    message = `Bonjour ${params.clientName}, ${params.workshopName} vous rappelle votre rendez-vous d’essayage pour le modèle ${params.modelName || ''} (${params.reference || ''}) prévu le ${formatDateFr(params.fittingDate || params.deliveryDate)}. À très bientôt !`;
  } else if (template === 'pret') {
    const restText =
      params.balanceDue && params.balanceDue > 0
        ? ` Reste à régler : ${formatMoney(params.balanceDue, params.currency || 'FCFA')}.`
        : '';
    message = `Bonjour ${params.clientName}, bonne nouvelle ! Votre tenue (${params.modelName || ''} - ${params.reference || ''}) est prête à ${params.workshopName}.${restText} Vous pouvez passer la récupérer.`;
  } else {
    message = params.customText || `Bonjour ${params.clientName}, message de ${params.workshopName}.`;
  }
  return `https://wa.me/${cleanPhone}?text=${encodeURIComponent(message)}`;
}

export function openExternalLink(url: string): void {
  const a = document.createElement('a');
  a.href = url;
  a.target = '_blank';
  a.rel = 'noopener noreferrer';
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
}
