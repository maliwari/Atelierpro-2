import React, { useRef, useState } from 'react';
import { Camera, ImagePlus, Scissors, Trash2, X } from 'lucide-react';
import { compressImageFile, compressVideoFrame } from '../utils/imageCompression';

interface PhotoCaptureInputProps {
  label: string;
  value?: string;
  onChange: (dataUrl: string) => void;
  aspectClass?: string;
}

export const PhotoCaptureInput: React.FC<PhotoCaptureInputProps> = ({
  label,
  value,
  onChange,
  aspectClass = 'aspect-[4/3]',
}) => {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const cameraInputRef = useRef<HTMLInputElement>(null);
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);

  const [compressing, setCompressing] = useState(false);
  const [liveCameraOpen, setLiveCameraOpen] = useState(false);
  const [cameraError, setCameraError] = useState<string | null>(null);

  const handleFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setCompressing(true);
    setCameraError(null);
    try {
      const compressed = await compressImageFile(file, 1080, 0.7);
      onChange(compressed);
    } catch {
      setCameraError('Erreur lors de la compression de la photo.');
    } finally {
      setCompressing(false);
      e.target.value = '';
    }
  };

  const openLiveCamera = async () => {
    setCameraError(null);
    if (!navigator.mediaDevices?.getUserMedia) {
      cameraInputRef.current?.click();
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'environment' },
        audio: false,
      });
      streamRef.current = stream;
      setLiveCameraOpen(true);
      setTimeout(() => {
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
        }
      }, 60);
    } catch {
      // Fallback to native Android/iOS camera input capture="environment"
      cameraInputRef.current?.click();
    }
  };

  const stopCamera = () => {
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((t) => t.stop());
      streamRef.current = null;
    }
    setLiveCameraOpen(false);
  };

  const captureFromVideo = () => {
    if (!videoRef.current) return;
    const dataUrl = compressVideoFrame(videoRef.current, 1080, 0.7);
    if (dataUrl) {
      onChange(dataUrl);
    }
    stopCamera();
  };

  return (
    <div className="space-y-2">
      <label className="block text-sm font-medium text-stone-700">{label}</label>

      {value ? (
        <div className="relative overflow-hidden rounded-2xl border border-stone-200 bg-stone-100">
          <img
            src={value}
            alt={label}
            referrerPolicy="no-referrer"
            className={`w-full ${aspectClass} object-cover`}
          />
          <div className="absolute bottom-2 right-2 flex items-center gap-2">
            <button
              type="button"
              onClick={openLiveCamera}
              className="min-h-[44px] px-3 rounded-xl bg-white/95 text-xs font-semibold text-stone-900 shadow-sm border border-stone-200 flex items-center gap-1.5"
            >
              <Camera className="h-4 w-4" />
              <span>Reprendre</span>
            </button>
            <button
              type="button"
              onClick={() => onChange('')}
              className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-xl bg-white/95 text-red-700 shadow-sm border border-stone-200"
              aria-label="Supprimer la photo"
            >
              <Trash2 className="h-4 w-4" />
            </button>
          </div>
        </div>
      ) : (
        <div className="grid grid-cols-2 gap-2.5">
          <button
            type="button"
            onClick={openLiveCamera}
            disabled={compressing}
            className="min-h-[64px] flex flex-col items-center justify-center gap-1 rounded-2xl border border-dashed border-stone-300 bg-stone-50 px-3 py-3 text-xs font-semibold text-stone-800 hover:bg-stone-100 active:scale-[0.99]"
          >
            <Camera className="h-5 w-5 text-[#1E3A5F]" />
            <span>{compressing ? 'Compression...' : 'Appareil photo'}</span>
          </button>
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            disabled={compressing}
            className="min-h-[64px] flex flex-col items-center justify-center gap-1 rounded-2xl border border-dashed border-stone-300 bg-stone-50 px-3 py-3 text-xs font-semibold text-stone-800 hover:bg-stone-100 active:scale-[0.99]"
          >
            <ImagePlus className="h-5 w-5 text-[#1E3A5F]" />
            <span>Galerie / Fichier</span>
          </button>
        </div>
      )}

      <p className="text-[11px] text-stone-500">
        Compression automatique WebP / JPEG 70 % pour préserver le stockage hors-ligne.
      </p>

      {cameraError && <p className="text-xs text-red-700 font-medium">{cameraError}</p>}

      <input
        ref={cameraInputRef}
        type="file"
        accept="image/*"
        capture="environment"
        onChange={handleFile}
        className="hidden"
      />
      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        onChange={handleFile}
        className="hidden"
      />

      {liveCameraOpen && (
        <div className="fixed inset-0 z-50 flex flex-col items-center justify-center bg-black/90 p-4">
          <div className="w-full max-w-md space-y-4">
            <div className="flex items-center justify-between text-white">
              <div className="flex items-center gap-2">
                <Scissors className="h-4 w-4 text-amber-400" />
                <span className="text-sm font-semibold">{label}</span>
              </div>
              <button
                type="button"
                onClick={stopCamera}
                className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-xl bg-white/10 text-white"
              >
                <X className="h-5 w-5" />
              </button>
            </div>
            <div className="overflow-hidden rounded-2xl bg-stone-900 border border-stone-700">
              <video
                ref={videoRef}
                autoPlay
                playsInline
                muted
                className="w-full aspect-[4/3] object-cover"
              />
            </div>
            <button
              type="button"
              onClick={captureFromVideo}
              className="w-full min-h-[52px] rounded-xl bg-[#1E3A5F] text-white font-semibold text-sm flex items-center justify-center gap-2"
            >
              <Camera className="h-5 w-5" />
              <span>Capturer la photo</span>
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

export const ResilientImage: React.FC<{
  src?: string;
  alt: string;
  className?: string;
}> = ({ src, alt, className = '' }) => {
  const [failed, setFailed] = useState(false);

  if (!src || failed) {
    return (
      <div
        className={`flex flex-col items-center justify-center bg-gradient-to-br from-stone-100 to-stone-200 text-stone-400 p-3 text-center ${className}`}
      >
        <Scissors className="h-6 w-6 mb-1 text-stone-400" />
        <span className="text-[11px] font-medium text-stone-500 line-clamp-2">{alt}</span>
      </div>
    );
  }

  return (
    <img
      src={src}
      alt={alt}
      referrerPolicy="no-referrer"
      onError={() => setFailed(true)}
      className={className}
    />
  );
};
