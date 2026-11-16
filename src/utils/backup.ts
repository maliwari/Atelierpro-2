import JSZip from 'jszip';
import { AtelierDatabase } from '../types';

export async function exportDatabaseAsZip(db: AtelierDatabase): Promise<void> {
  const zip = new JSZip();
  const payload = {
    exportedAt: new Date().toISOString(),
    app: 'AtelierPro',
    version: 1,
    database: db,
  };
  zip.file('atelierpro_backup.json', JSON.stringify(payload, null, 2));

  const blob = await zip.generateAsync({ type: 'blob', compression: 'DEFLATE' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  const dateStr = new Date().toISOString().split('T')[0];
  a.href = url;
  a.download = `AtelierPro_Sauvegarde_${dateStr}.zip`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

export async function importDatabaseFromFile(file: File): Promise<AtelierDatabase> {
  let jsonText = '';
  if (file.name.toLowerCase().endsWith('.zip')) {
    const zip = await JSZip.loadAsync(file);
    const entry = zip.file('atelierpro_backup.json') || Object.values(zip.files).find((f) => f.name.endsWith('.json'));
    if (!entry) {
      throw new Error('Archive ZIP invalide : fichier JSON introuvable.');
    }
    jsonText = await entry.async('string');
  } else {
    jsonText = await file.text();
  }

  const parsed = JSON.parse(jsonText);
  const candidate = (parsed.database || parsed) as AtelierDatabase;
  if (
    !candidate ||
    !Array.isArray(candidate.models) ||
    !Array.isArray(candidate.clients) ||
    !Array.isArray(candidate.orders) ||
    !Array.isArray(candidate.inventory)
  ) {
    throw new Error('Structure de sauvegarde non reconnue.');
  }
  return candidate;
}
