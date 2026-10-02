import { describe, expect, it } from 'vitest';
import { formatWeight, optionalWeightSchema, setResultSchema } from './weight';

describe('planned and actual loads', () => {
  it('distinguishes unrecorded values from zero and accepts Italian decimals', () => {
    expect(optionalWeightSchema.parse(' ')).toBeNull();
    expect(optionalWeightSchema.parse('0')).toBe(0);
    expect(optionalWeightSchema.parse('22,75')).toBe(22.75);
    expect(formatWeight(22.75)).toBe('22,75 kg');
    expect(setResultSchema.parse({ weightKgUsed: '', repsActual: '' })).toEqual({ weightKgUsed: null, repsActual: null });
    expect(setResultSchema.parse({ weightKgUsed: '0', repsActual: '0' })).toEqual({ weightKgUsed: 0, repsActual: 0 });
  });
  it('rejects invalid or overprecise values without rounding or truncating results', () => {
    for (const value of ['-1', '1001', '10.001', '1e2', 'abc', 'Infinity']) expect(optionalWeightSchema.safeParse(value).success).toBe(false);
    for (const value of ['-1', '1001', '8.5', 'abc']) expect(setResultSchema.safeParse({ weightKgUsed: '', repsActual: value }).success).toBe(false);
  });
});
