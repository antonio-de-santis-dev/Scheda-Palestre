import type { VolumeSummary } from '../workout/api';

export function WorkoutVolume({ volume, detailed = false }: { volume: VolumeSummary | undefined; detailed?: boolean }) {
  if (!volume) return <span>Volume: Non disponibile</span>;
  const missing = volume.completedSets - volume.recordedSets;
  const value = volume.recordedKgReps == null ? 'Non disponibile'
    : `${volume.recordedKgReps.toLocaleString('it-IT', { maximumFractionDigits: 2 })} kg × ripetizioni`;
  return (
    <div className="small" style={{ marginTop: detailed ? 'var(--space-2)' : undefined }}>
      <span>Volume registrato: {value}</span>
      {volume.completedSets === 0 ? <span> · Nessuna serie completata</span>
        : <span> · {volume.recordedSets}/{volume.completedSets} serie con dati completi{missing > 0 ? ' · Dati parziali' : ''}</span>}
      {detailed && missing > 0 ? (
        <p className="muted" style={{ margin: 'var(--space-1) 0 0' }}>
          Serie completate escluse: {missing}; peso mancante in {volume.missingWeightSets}, ripetizioni mancanti in {volume.missingRepsSets}.
        </p>
      ) : null}
    </div>
  );
}
