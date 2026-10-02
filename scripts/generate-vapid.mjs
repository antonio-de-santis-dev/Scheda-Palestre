import { createECDH } from 'node:crypto';
import { writeFileSync } from 'node:fs';
const key = createECDH('prime256v1');
key.generateKeys();
const path = process.argv[2] ?? '.env.vapid';
// wx never overwrites an existing deployment key. No private material goes to stdout.
writeFileSync(path, `GYM_VAPID_PUBLIC_KEY=${key.getPublicKey().toString('base64url')}\nGYM_VAPID_PRIVATE_KEY=${key.getPrivateKey().toString('base64url')}\n`, { mode: 0o600, flag: 'wx' });
process.stdout.write(`Chiavi salvate nel file riservato ${path}.\n`);
