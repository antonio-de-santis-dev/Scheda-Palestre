import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';
import { Combobox, filterOptions } from './Combobox';

const options = [
  { id: 'a', label: 'Panca piana' },
  { id: 'b', label: 'Panca inclinata' },
  { id: 'c', label: 'Croci ai cavi' },
  { id: 'd', label: 'Pullover' },
];

function Harness({ initial = null }: { initial?: string | null }) {
  const [value, setValue] = useState<string | null>(initial);
  return (
    <>
      <Combobox label="Esercizio" options={options} value={value} onChange={setValue} emptyText="Nessun esercizio trovato in questo gruppo" />
      <output data-testid="value">{value ?? 'none'}</output>
      <button type="button">Dopo</button>
    </>
  );
}

describe('Combobox', () => {
  it('filters ignoring case and accents', () => {
    expect(filterOptions(options, 'PANCA').map((o) => o.id)).toEqual(['a', 'b']);
    expect(filterOptions([{ id: 'x', label: 'Alzate laterali à' }], 'a').length).toBe(1);
    expect(filterOptions(options, '  ').length).toBe(4);
  });

  it('exposes the ARIA combobox pattern and announces the number of results', async () => {
    render(<Harness />);
    const user = userEvent.setup();
    const input = screen.getByRole('combobox', { name: 'Esercizio' });
    expect(input).toHaveAttribute('aria-expanded', 'false');
    await user.type(input, 'panca');
    expect(input).toHaveAttribute('aria-expanded', 'true');
    const listbox = screen.getByRole('listbox', { name: 'Esercizio' });
    expect(input).toHaveAttribute('aria-controls', listbox.id);
    expect(screen.getAllByRole('option')).toHaveLength(2);
    expect(screen.getByText('2 risultati')).toBeInTheDocument();
  });

  it('supports arrows, Home/End and Enter with aria-activedescendant', async () => {
    render(<Harness />);
    const user = userEvent.setup();
    const input = screen.getByRole('combobox', { name: 'Esercizio' });
    await user.click(input);
    await user.keyboard('{ArrowDown}');
    const optionsEls = screen.getAllByRole('option');
    expect(input).toHaveAttribute('aria-activedescendant', optionsEls[0]!.id);
    await user.keyboard('{ArrowDown}');
    expect(input).toHaveAttribute('aria-activedescendant', optionsEls[1]!.id);
    await user.keyboard('{End}');
    expect(input).toHaveAttribute('aria-activedescendant', optionsEls[3]!.id);
    await user.keyboard('{Home}');
    expect(input).toHaveAttribute('aria-activedescendant', optionsEls[0]!.id);
    await user.keyboard('{ArrowUp}');
    expect(input).toHaveAttribute('aria-activedescendant', optionsEls[3]!.id);
    await user.keyboard('{Enter}');
    expect(screen.getByTestId('value')).toHaveTextContent('d');
    expect(input).toHaveValue('Pullover');
    expect(input).toHaveAttribute('aria-expanded', 'false');
    expect(input).toHaveFocus();
  });

  it('closes with Escape keeping the focus, and Tab never selects by accident', async () => {
    render(<Harness initial="a" />);
    const user = userEvent.setup();
    const input = screen.getByRole('combobox', { name: 'Esercizio' });
    await user.click(input);
    await user.keyboard('{ArrowDown}{ArrowDown}{Escape}');
    expect(input).toHaveAttribute('aria-expanded', 'false');
    expect(input).toHaveFocus();
    expect(screen.getByTestId('value')).toHaveTextContent('a');

    await user.keyboard('{ArrowDown}{ArrowDown}');
    await user.tab();
    expect(screen.getByRole('button', { name: 'Dopo' })).toHaveFocus();
    expect(screen.getByTestId('value')).toHaveTextContent('a');
    expect(input).toHaveValue('Panca piana');
  });

  it('shows a textual empty state', async () => {
    render(<Harness />);
    const user = userEvent.setup();
    await user.type(screen.getByRole('combobox', { name: 'Esercizio' }), 'zzz');
    expect(screen.queryAllByRole('option')).toHaveLength(0);
    expect(screen.getAllByText('Nessun esercizio trovato in questo gruppo').length).toBeGreaterThan(0);
  });

  it('selects with the mouse', async () => {
    render(<Harness />);
    const user = userEvent.setup();
    await user.click(screen.getByRole('combobox', { name: 'Esercizio' }));
    await user.click(screen.getByRole('option', { name: 'Croci ai cavi' }));
    expect(screen.getByTestId('value')).toHaveTextContent('c');
  });
});
