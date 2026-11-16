import { AtelierDatabase } from '../types';
import { createInitialSeedData } from '../data/seedData';
import { createInitialLicenseState } from '../utils/license';

const DB_NAME = 'atelierpro_offline_db';
const DB_VERSION = 1;
const STORE_NAME = 'workshop_state';
const STATE_KEY = 'primary_atelier';

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        db.createObjectStore(STORE_NAME);
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

export async function loadAtelierDatabase(): Promise<AtelierDatabase> {
  try {
    const db = await openDb();
    return await new Promise((resolve, reject) => {
      const tx = db.transaction(STORE_NAME, 'readonly');
      const store = tx.objectStore(STORE_NAME);
      const req = store.get(STATE_KEY);
      req.onsuccess = async () => {
        if (req.result) {
          const loaded = req.result as AtelierDatabase;
          if (!loaded.license) {
            const hydrated = { ...loaded, license: createInitialLicenseState() };
            await saveAtelierDatabase(hydrated);
            resolve(hydrated);
          } else {
            resolve(loaded);
          }
        } else {
          const seed = createInitialSeedData();
          await saveAtelierDatabase(seed);
          resolve(seed);
        }
      };
      req.onerror = () => reject(req.error);
    });
  } catch {
    // Fallback to localStorage if IndexedDB is restricted
    const raw = localStorage.getItem('atelierpro_fallback_db');
    if (raw) {
      const parsed = JSON.parse(raw) as AtelierDatabase;
      return parsed.license ? parsed : { ...parsed, license: createInitialLicenseState() };
    }
    const seed = createInitialSeedData();
    localStorage.setItem('atelierpro_fallback_db', JSON.stringify(seed));
    return seed;
  }
}

export async function saveAtelierDatabase(data: AtelierDatabase): Promise<void> {
  try {
    const db = await openDb();
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(STORE_NAME, 'readwrite');
      const store = tx.objectStore(STORE_NAME);
      const req = store.put(data, STATE_KEY);
      req.onsuccess = () => resolve();
      req.onerror = () => reject(req.error);
    });
  } catch {
    localStorage.setItem('atelierpro_fallback_db', JSON.stringify(data));
  }
}

export async function resetAtelierDatabase(): Promise<AtelierDatabase> {
  const seed = createInitialSeedData();
  await saveAtelierDatabase(seed);
  return seed;
}
