import { jsPDF } from 'jspdf';
import {
  AtelierSettings,
  Client,
  Employee,
  GarmentModel,
  HaberdasheryItem,
  Order,
  STANDARD_MEASUREMENTS,
} from '../types';
import { formatDateFr, formatMoney, openExternalLink } from './formatters';

function getMeasurementLabel(key: string, model?: GarmentModel): string {
  const std = STANDARD_MEASUREMENTS.find((m) => m.key === key);
  if (std) return std.label;
  const custom = model?.customMeasurements?.find((m) => m.key === key);
  return custom ? custom.label : key;
}

/**
 * Loads an image URL/dataURL and normalizes it to a PNG data URL via Canvas
 * so jsPDF can always embed it reliably (even if source was WebP or SVG).
 */
async function toPngDataUrl(src: string): Promise<string | null> {
  return new Promise((resolve) => {
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => {
      try {
        const size = 256;
        const canvas = document.createElement('canvas');
        canvas.width = size;
        canvas.height = size;
        const ctx = canvas.getContext('2d');
        if (!ctx) {
          resolve(null);
          return;
        }
        ctx.fillStyle = '#FFFFFF';
        ctx.fillRect(0, 0, size, size);
        const scale = Math.min(size / img.width, size / img.height);
        const w = img.width * scale;
        const h = img.height * scale;
        ctx.drawImage(img, (size - w) / 2, (size - h) / 2, w, h);
        resolve(canvas.toDataURL('image/png'));
      } catch {
        resolve(null);
      }
    };
    img.onerror = () => resolve(null);
    img.src = src;
  });
}

function drawFallbackWorkshopLogoPng(workshopName: string): string {
  const canvas = document.createElement('canvas');
  canvas.width = 256;
  canvas.height = 256;
  const ctx = canvas.getContext('2d');
  if (!ctx) return '';

  // Deep indigo background badge
  ctx.fillStyle = '#1E3A5F';
  ctx.fillRect(0, 0, 256, 256);

  // Subtle gold inner border
  ctx.strokeStyle = '#D97706';
  ctx.lineWidth = 8;
  ctx.strokeRect(14, 14, 228, 228);

  // Initials monogram
  const initials = workshopName
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0].toUpperCase())
    .join('') || 'AP';

  ctx.fillStyle = '#FAF9F6';
  ctx.font = 'bold 92px Georgia, serif';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText(initials, 128, 122);

  // Tailor needle/thread accent line
  ctx.strokeStyle = '#D97706';
  ctx.lineWidth = 6;
  ctx.beginPath();
  ctx.moveTo(56, 188);
  ctx.lineTo(200, 188);
  ctx.stroke();

  return canvas.toDataURL('image/png');
}

async function resolveWorkshopLogoPng(settings: AtelierSettings): Promise<string> {
  if (settings.workshopLogoUrl) {
    const customPng = await toPngDataUrl(settings.workshopLogoUrl);
    if (customPng) return customPng;
  }
  const pwaPng = await toPngDataUrl('/pwa-192x192.png');
  if (pwaPng) return pwaPng;
  return drawFallbackWorkshopLogoPng(settings.workshopName);
}

export interface GeneratedReceiptResult {
  doc: jsPDF;
  blob: Blob;
  file: File;
  fileName: string;
  summaryText: string;
}

export async function buildDetailedReceiptPDF(
  order: Order,
  client: Client | undefined,
  model: GarmentModel | undefined,
  employee: Employee | undefined,
  settings: AtelierSettings
): Promise<GeneratedReceiptResult> {
  const doc = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' });
  const pageWidth = doc.internal.pageSize.getWidth();
  const pageHeight = doc.internal.pageSize.getHeight();
  const margin = 14;
  const contentWidth = pageWidth - margin * 2;

  // Header band
  doc.setFillColor(30, 58, 95);
  doc.rect(0, 0, pageWidth, 38, 'F');

  // Workshop Logo on the left of header
  const logoPng = await resolveWorkshopLogoPng(settings);
  if (logoPng) {
    doc.setFillColor(255, 255, 255);
    doc.roundedRect(margin, 7, 24, 24, 3, 3, 'F');
    doc.addImage(logoPng, 'PNG', margin + 1, 8, 22, 22);
  }

  const textStartX = logoPng ? margin + 30 : margin;
  doc.setTextColor(255, 255, 255);
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(17);
  doc.text(settings.workshopName, textStartX, 16);
  doc.setFont('helvetica', 'normal');
  doc.setFontSize(9.5);
  doc.text(settings.workshopAddress, textStartX, 23);
  doc.text(`Tél. : ${settings.workshopPhone}`, textStartX, 29);

  // Title & Reference
  let y = 50;
  doc.setTextColor(28, 25, 23);
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(15);
  doc.text('REÇU DÉTAILLÉ DE COMMANDE', margin, y);

  doc.setFontSize(10.5);
  doc.setTextColor(30, 58, 95);
  doc.text(`Réf. ${order.reference}`, pageWidth - margin, y, { align: 'right' });

  y += 5;
  doc.setDrawColor(214, 211, 209);
  doc.line(margin, y, pageWidth - margin, y);

  // 2-Column Info Cards: Client | Modèle & Planning
  y += 7;
  const colGap = 6;
  const colW = (contentWidth - colGap) / 2;

  // Client Box
  doc.setFillColor(250, 249, 246);
  doc.setDrawColor(231, 229, 228);
  doc.roundedRect(margin, y, colW, 38, 2.5, 2.5, 'FD');
  doc.setTextColor(87, 83, 78);
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(8.5);
  doc.text('CLIENT', margin + 5, y + 7);
  doc.setTextColor(28, 25, 23);
  doc.setFontSize(11);
  doc.text(client?.fullName || 'Client inconnu', margin + 5, y + 14);
  doc.setFont('helvetica', 'normal');
  doc.setFontSize(9.5);
  doc.text(`Tél. : ${client?.phone || '—'}`, margin + 5, y + 21);
  doc.text(client?.address || 'Adresse non renseignée', margin + 5, y + 27, {
    maxWidth: colW - 10,
  });

  // Model & Dates Box
  const rightColX = margin + colW + colGap;
  doc.setFillColor(250, 249, 246);
  doc.roundedRect(rightColX, y, colW, 38, 2.5, 2.5, 'FD');
  doc.setTextColor(87, 83, 78);
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(8.5);
  doc.text('MODÈLE & SUIVI', rightColX + 5, y + 7);
  doc.setTextColor(28, 25, 23);
  doc.setFontSize(11);
  doc.text(model?.name || 'Confection sur mesure', rightColX + 5, y + 14, {
    maxWidth: colW - 10,
  });
  doc.setFont('helvetica', 'normal');
  doc.setFontSize(9.5);
  doc.text(
    `Catégorie : ${model?.category || '—'}  •  Statut : ${order.status}`,
    rightColX + 5,
    y + 21
  );
  doc.text(
    `Commandé : ${formatDateFr(order.orderDate)}  •  Livraison : ${formatDateFr(order.deliveryDate)}`,
    rightColX + 5,
    y + 27
  );
  if (order.fittingDate || employee) {
    const extraLine = [
      order.fittingDate ? `Essayage : ${formatDateFr(order.fittingDate)}` : null,
      employee ? `Tailleur : ${employee.fullName}` : null,
    ]
      .filter(Boolean)
      .join('  •  ');
    doc.text(extraLine, rightColX + 5, y + 33, { maxWidth: colW - 10 });
  }

  y += 45;

  // Fabric notes if present
  if (order.fabricNotes) {
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(9.5);
    doc.setTextColor(28, 25, 23);
    doc.text('Tissu / Fourniture client :', margin, y);
    doc.setFont('helvetica', 'normal');
    doc.text(order.fabricNotes, margin + 45, y, { maxWidth: contentWidth - 45 });
    y += 8;
  }

  // Measurements Section
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(11);
  doc.setTextColor(28, 25, 23);
  doc.text('Mensurations enregistrées (cm)', margin, y + 2);
  y += 6;

  const measurementEntries = Object.entries(order.measurements).filter(([, val]) => val > 0);
  if (measurementEntries.length === 0) {
    doc.setFont('helvetica', 'normal');
    doc.setFontSize(9.5);
    doc.setTextColor(120, 113, 108);
    doc.text('Aucune mensuration saisie pour cette commande.', margin, y + 5);
    y += 12;
  } else {
    const rows = Math.ceil(measurementEntries.length / 2);
    const rowHeight = 8;
    const tableHeight = rows * rowHeight + 4;
    doc.setFillColor(255, 255, 255);
    doc.setDrawColor(231, 229, 228);
    doc.roundedRect(margin, y, contentWidth, tableHeight, 2.5, 2.5, 'FD');

    measurementEntries.forEach(([key, val], index) => {
      const col = index % 2;
      const row = Math.floor(index / 2);
      const cellX = margin + 5 + col * (contentWidth / 2);
      const cellY = y + 6.5 + row * rowHeight;

      if (col === 0 && row > 0) {
        doc.setDrawColor(245, 245, 244);
        doc.line(margin + 3, cellY - 5, pageWidth - margin - 3, cellY - 5);
      }

      doc.setFont('helvetica', 'normal');
      doc.setFontSize(9.5);
      doc.setTextColor(87, 83, 78);
      doc.text(getMeasurementLabel(key, model), cellX, cellY);

      doc.setFont('helvetica', 'bold');
      doc.setTextColor(28, 25, 23);
      doc.text(`${val} cm`, cellX + contentWidth / 2 - 12, cellY, { align: 'right' });
    });

    y += tableHeight + 8;
  }

  // Financial Summary Box
  const balance = Math.max(0, order.totalPrice - order.advancePaid);
  doc.setFillColor(245, 245, 244);
  doc.setDrawColor(214, 211, 209);
  doc.roundedRect(margin, y, contentWidth, 40, 3, 3, 'FD');

  doc.setFont('helvetica', 'bold');
  doc.setFontSize(9);
  doc.setTextColor(87, 83, 78);
  doc.text('DÉTAIL FINANCIER', margin + 6, y + 7);

  doc.setFont('helvetica', 'normal');
  doc.setFontSize(10.5);
  doc.setTextColor(28, 25, 23);
  doc.text('Prix total convenu', margin + 6, y + 15);
  doc.setFont('helvetica', 'bold');
  doc.text(formatMoney(order.totalPrice, settings.currency), pageWidth - margin - 6, y + 15, {
    align: 'right',
  });

  doc.setFont('helvetica', 'normal');
  doc.text('Avance / Acompte versé', margin + 6, y + 23);
  doc.setTextColor(21, 128, 61);
  doc.setFont('helvetica', 'bold');
  doc.text(formatMoney(order.advancePaid, settings.currency), pageWidth - margin - 6, y + 23, {
    align: 'right',
  });

  doc.setDrawColor(214, 211, 209);
  doc.line(margin + 6, y + 27, pageWidth - margin - 6, y + 27);

  doc.setTextColor(28, 25, 23);
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(12);
  doc.text('SOLDE À PAYER', margin + 6, y + 35);
  doc.setTextColor(balance > 0 ? 185 : 21, balance > 0 ? 28 : 128, balance > 0 ? 28 : 61);
  doc.text(
    balance > 0 ? formatMoney(balance, settings.currency) : 'SOLDÉ (0)',
    pageWidth - margin - 6,
    y + 35,
    { align: 'right' }
  );

  y += 48;

  if (order.notes) {
    doc.setTextColor(87, 83, 78);
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(9);
    doc.text('Remarques :', margin, y);
    doc.setFont('helvetica', 'normal');
    doc.text(order.notes, margin + 24, y, { maxWidth: contentWidth - 24 });
  }

  // Footer
  doc.setDrawColor(231, 229, 228);
  doc.line(margin, pageHeight - 24, pageWidth - margin, pageHeight - 24);
  doc.setTextColor(120, 113, 108);
  doc.setFont('helvetica', 'normal');
  doc.setFontSize(8.5);
  doc.text(
    `Document généré par ${settings.workshopName} • Veuillez présenter ce reçu lors du retrait de votre tenue.`,
    pageWidth / 2,
    pageHeight - 16,
    { align: 'center', maxWidth: contentWidth }
  );

  const fileName = `Recu_${order.reference}.pdf`;
  const blob = doc.output('blob');
  const file = new File([blob], fileName, { type: 'application/pdf' });

  const measList = measurementEntries
    .slice(0, 8)
    .map(([k, v]) => `• ${getMeasurementLabel(k, model)} : ${v} cm`)
    .join('\n');

  const summaryText = [
    `*${settings.workshopName}*`,
    `Reçu de commande *${order.reference}*`,
    `Client : ${client?.fullName || 'Client'}`,
    `Modèle : ${model?.name || 'Sur-mesure'} (${model?.category || ''})`,
    `Livraison prévue : ${formatDateFr(order.deliveryDate)}`,
    measList ? `\n*Mensurations :*\n${measList}` : '',
    `\n*Prix total :* ${formatMoney(order.totalPrice, settings.currency)}`,
    `*Avance versée :* ${formatMoney(order.advancePaid, settings.currency)}`,
    `*Solde à payer :* ${
      balance > 0 ? formatMoney(balance, settings.currency) : 'Soldé'
    }`,
  ]
    .filter(Boolean)
    .join('\n');

  return { doc, blob, file, fileName, summaryText };
}

export async function generateClientReceiptPDF(
  order: Order,
  client: Client | undefined,
  model: GarmentModel | undefined,
  employee: Employee | undefined,
  settings: AtelierSettings
): Promise<string> {
  const { doc, fileName } = await buildDetailedReceiptPDF(
    order,
    client,
    model,
    employee,
    settings
  );
  doc.save(fileName);
  return fileName;
}

export async function shareClientReceiptViaWhatsApp(
  order: Order,
  client: Client | undefined,
  model: GarmentModel | undefined,
  employee: Employee | undefined,
  settings: AtelierSettings
): Promise<'native_share' | 'download_and_whatsapp'> {
  const { doc, file, fileName, summaryText } = await buildDetailedReceiptPDF(
    order,
    client,
    model,
    employee,
    settings
  );

  // On Android / mobile browsers that support sharing PDF files directly
  if (
    typeof navigator !== 'undefined' &&
    typeof navigator.share === 'function' &&
    typeof navigator.canShare === 'function' &&
    navigator.canShare({ files: [file] })
  ) {
    try {
      await navigator.share({
        title: `Reçu ${order.reference} — ${settings.workshopName}`,
        text: summaryText,
        files: [file],
      });
      return 'native_share';
    } catch (err) {
      // If user explicitly cancelled share sheet, don't force download
      if (err instanceof DOMException && err.name === 'AbortError') {
        return 'native_share';
      }
    }
  }

  // Fallback for desktop browsers / environments without file-share support:
  // Download the PDF receipt and open WhatsApp chat pre-filled with the detailed receipt summary
  doc.save(fileName);
  const cleanPhone = (client?.phone || '').replace(/[^0-9]/g, '');
  const waUrl = cleanPhone
    ? `https://wa.me/${cleanPhone}?text=${encodeURIComponent(summaryText)}`
    : `https://wa.me/?text=${encodeURIComponent(summaryText)}`;
  openExternalLink(waUrl);
  return 'download_and_whatsapp';
}

export function generateCutSheetPDF(
  order: Order,
  client: Client | undefined,
  model: GarmentModel | undefined,
  employee: Employee | undefined,
  inventory: HaberdasheryItem[],
  settings: AtelierSettings
): void {
  const doc = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' });
  const pageWidth = doc.internal.pageSize.getWidth();

  doc.setFillColor(28, 25, 23);
  doc.rect(0, 0, pageWidth, 24, 'F');
  doc.setTextColor(255, 255, 255);
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(15);
  doc.text(`FICHE DE COUPE — ${order.reference}`, 14, 15);
  doc.setFontSize(10);
  doc.setFont('helvetica', 'normal');
  doc.text(settings.workshopName, pageWidth - 14, 15, { align: 'right' });

  let y = 36;
  doc.setTextColor(28, 25, 23);
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(11);
  doc.text(`Modèle : ${model?.name || 'Sur-mesure'}`, 14, y);
  doc.setFont('helvetica', 'normal');
  doc.text(`Client : ${client?.fullName || '—'}`, 14, y + 7);
  doc.text(`Tailleur assigné : ${employee?.fullName || 'Non assigné'}`, 14, y + 14);
  doc.text(`Date livraison : ${formatDateFr(order.deliveryDate)}`, 120, y);
  if (order.fittingDate) {
    doc.text(`Date essayage : ${formatDateFr(order.fittingDate)}`, 120, y + 7);
  }
  doc.text(`Tissu : ${order.fabricNotes || 'Voir photo'}`, 14, y + 21);

  y += 32;
  doc.setDrawColor(214, 211, 209);
  doc.line(14, y, pageWidth - 14, y);
  y += 10;

  // Measurements Table
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(12);
  doc.text('Mensurations requises (cm)', 14, y);
  y += 8;

  const entries = Object.entries(order.measurements);
  doc.setFontSize(10);
  entries.forEach(([key, val], index) => {
    const colX = index % 2 === 0 ? 14 : 110;
    if (index > 0 && index % 2 === 0) y += 8;
    doc.setFont('helvetica', 'normal');
    doc.text(getMeasurementLabel(key, model), colX, y);
    doc.setFont('helvetica', 'bold');
    doc.text(`${val} cm`, colX + 65, y);
  });

  y += 18;
  doc.line(14, y, pageWidth - 14, y);
  y += 10;

  // Haberdashery checklist
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(12);
  doc.text('Mercerie nécessaire (Recette modèle)', 14, y);
  y += 8;
  doc.setFontSize(10);
  if (model && model.recipe.length > 0) {
    model.recipe.forEach((r) => {
      const item = inventory.find((i) => i.id === r.itemId);
      doc.setFont('helvetica', 'normal');
      doc.text(`[  ] ${item?.name || r.itemId}`, 16, y);
      doc.setFont('helvetica', 'bold');
      doc.text(`${r.quantity} ${item?.unit || ''}`, 110, y);
      y += 7;
    });
  } else {
    doc.setFont('helvetica', 'normal');
    doc.text('Aucune fourniture spécifique enregistrée.', 16, y);
    y += 7;
  }

  if (order.notes) {
    y += 8;
    doc.setFont('helvetica', 'bold');
    doc.text('Notes atelier :', 14, y);
    y += 6;
    doc.setFont('helvetica', 'normal');
    doc.text(order.notes, 14, y, { maxWidth: pageWidth - 28 });
  }

  doc.save(`Fiche_Coupe_${order.reference}.pdf`);
}
