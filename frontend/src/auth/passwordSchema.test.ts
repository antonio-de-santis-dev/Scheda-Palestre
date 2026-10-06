import { describe, expect, it } from 'vitest';
import { newPasswordSchema } from './passwordSchema';

describe('password encoder limits', () => {
  it('accepts exactly 72 UTF-8 bytes and rejects longer ASCII and Unicode passwords', () => {
    expect(newPasswordSchema.safeParse('a1' + 'a'.repeat(70)).success).toBe(true);
    expect(newPasswordSchema.safeParse('a1' + 'a'.repeat(71)).success).toBe(false);
    expect(newPasswordSchema.safeParse('é'.repeat(35) + 'a1').success).toBe(true);
    expect(newPasswordSchema.safeParse('é'.repeat(36) + 'a1').success).toBe(false);
  });
});
