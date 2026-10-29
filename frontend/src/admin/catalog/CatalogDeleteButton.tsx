import { useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Trash2 } from 'lucide-react';
import { Button } from '../../shared/components/Button';
import { ConfirmDialog } from '../../shared/components/ConfirmDialog';
import { TextField } from '../../shared/components/Field';
import { ErrorAlert } from '../../shared/components/Alert';

interface CatalogDeleteButtonProps {
    name: string;
    label: string;
    description: string;
    onDelete: () => Promise<void>;
    onDeleted?: () => void;
}

export function CatalogDeleteButton({
                                        name,
                                        label,
                                        description,
                                        onDelete,
                                        onDeleted,
                                    }: CatalogDeleteButtonProps) {
    const queryClient = useQueryClient();
    const [step, setStep] = useState<0 | 1 | 2>(0);
    const [confirmation, setConfirmation] = useState('');
    const [fieldError, setFieldError] = useState<string>();
    const [error, setError] = useState<unknown>(null);
    const [pending, setPending] = useState(false);
    const running = useRef(false);

    const open = () => {
        setConfirmation('');
        setFieldError(undefined);
        setError(null);
        setStep(1);
    };

    const remove = async () => {
        if (running.current) {
            return;
        }

        if (confirmation !== name) {
            setFieldError('Scrivi il nome esattamente come indicato.');
            return;
        }

        running.current = true;
        setPending(true);
        setError(null);

        try {
            await onDelete();
        } catch (err) {
            setError(err);
            return;
        } finally {
            running.current = false;
            setPending(false);
        }

        setStep(0);
        onDeleted?.();

        // La cancellazione cambia anche schede e dati dell'area utente.
        void queryClient.invalidateQueries();
    };

    return (
        <>
            <Button
                variant="danger"
                size="sm"
                icon={<Trash2 size={16} aria-hidden="true" />}
                aria-label={`${label}: ${name}`}
                onClick={open}
                disabled={pending}
            >
                {label}
            </Button>

            <ConfirmDialog
                key={step}
                open={step !== 0}
                title={
                    step === 1
                        ? `Eliminare “${name}”?`
                        : 'Conferma eliminazione definitiva'
                }
                confirmLabel={step === 1 ? 'Continua' : 'Elimina definitivamente'}
                tone={step === 1 ? 'primary' : 'danger'}
                loading={pending}
                onCancel={() => {
                    if (!running.current) {
                        setStep(0);
                    }
                }}
                onConfirm={() => {
                    if (step === 1) {
                        setStep(2);
                    } else if (step === 2) {
                        void remove();
                    }
                }}
            >
                <p>{description}</p>
                <p>La cancellazione è definitiva e non può essere annullata.</p>

                {step === 2 ? (
                    <TextField
                        label={`Per confermare, scrivi: ${name}`}
                        value={confirmation}
                        autoComplete="off"
                        disabled={pending}
                        error={fieldError}
                        onChange={(event) => {
                            setConfirmation(event.target.value);
                            setFieldError(undefined);
                        }}
                    />
                ) : null}

                {error ? <ErrorAlert error={error} /> : null}
            </ConfirmDialog>
        </>
    );
}