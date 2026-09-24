import { z } from 'zod';

/** Same policy as the backend (PasswordPolicy): 8-128 chars, at least a letter and a digit. */
export const newPasswordSchema = z
  .string()
  .min(8, 'Almeno 8 caratteri')
  .max(128, 'Al massimo 128 caratteri')
  .refine((v) => /\p{L}/u.test(v), 'Deve contenere almeno una lettera')
  .refine((v) => /\p{Nd}/u.test(v), 'Deve contenere almeno una cifra');

export const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, 'Inserisci la password attuale'),
    newPassword: newPasswordSchema,
    confirmPassword: z.string().min(1, 'Ripeti la nuova password'),
  })
  .refine((v) => v.newPassword === v.confirmPassword, {
    path: ['confirmPassword'],
    message: 'Le password non coincidono',
  })
  .refine((v) => v.newPassword !== v.currentPassword, {
    path: ['newPassword'],
    message: 'La nuova password deve essere diversa da quella attuale',
  });

export type ChangePasswordForm = z.infer<typeof changePasswordSchema>;
