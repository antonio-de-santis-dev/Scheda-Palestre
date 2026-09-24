import { ChevronLeft, ChevronRight } from 'lucide-react';
import { Button } from './Button';

interface PaginationProps {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
}

export function Pagination({ page, totalPages, onChange }: PaginationProps) {
  if (totalPages <= 1) {
    return null;
  }
  return (
    <nav className="pagination" aria-label="Paginazione">
      <Button
        variant="secondary"
        size="sm"
        onClick={() => onChange(page - 1)}
        disabled={page <= 0}
        icon={<ChevronLeft size={18} aria-hidden="true" />}
      >
        Precedente
      </Button>
      <span aria-live="polite">
        Pagina {page + 1} di {totalPages}
      </span>
      <Button variant="secondary" size="sm" onClick={() => onChange(page + 1)} disabled={page >= totalPages - 1}>
        Successiva
        <ChevronRight size={18} aria-hidden="true" />
      </Button>
    </nav>
  );
}
