#!/usr/bin/env python3
"""Configure recovery in a local ignored .env without printing or embedding SMTP secrets."""
import argparse
import getpass
import os
from pathlib import Path
import tempfile
from urllib.parse import urlsplit


def configure(path, url, sender, password):
    origin = urlsplit(url)
    if (origin.scheme not in ('http', 'https') or not origin.hostname
            or (origin.scheme == 'http' and origin.hostname not in ('localhost', '127.0.0.1', '::1'))
            or origin.username is not None or origin.password is not None
            or origin.path not in ('', '/') or origin.query or origin.fragment):
        raise ValueError('Usa la sola origine HTTPS, oppure http://localhost:5173 per lo sviluppo locale.')
    if '@' not in sender or any(ch.isspace() for ch in sender):
        raise ValueError('Indirizzo email non valido.')
    password = ''.join(ch for ch in password if not ch.isspace())
    if len(password) != 16 or not password.isascii() or not password.isalnum():
        raise ValueError('La password per app Gmail deve contenere 16 caratteri, senza separatori.')
    values = {
        'GYM_RECOVERY_ENABLED': 'true', 'GYM_PUBLIC_URL': url.rstrip('/'),
        'GYM_MAIL_FROM': sender, 'GYM_SMTP_HOST': 'smtp.gmail.com', 'GYM_SMTP_PORT': '587',
        'GYM_SMTP_USERNAME': sender, 'GYM_SMTP_PASSWORD': password,
    }
    path = Path(path).resolve()
    content = path.read_text() if path.exists() else ''
    kept = [line for line in content.splitlines() if line.split('=', 1)[0].strip() not in values]
    fd, temporary = tempfile.mkstemp(prefix='recovery-', suffix='.tmp', dir=path.parent)
    try:
        with os.fdopen(fd, 'w') as stream:
            stream.write('\n'.join(kept).rstrip() + '\n\n# Recupero password SMTP\n')
            stream.write(''.join(f'{key}={value}\n' for key, value in values.items()))
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--url', required=True, help='Origine frontend, senza /login')
    parser.add_argument('--sender', required=True, help='Account Gmail mittente')
    args = parser.parse_args()
    path = Path(__file__).resolve().parent.parent / '.env'
    secret = getpass.getpass('Password per app Gmail (input nascosto): ')
    try:
        configure(path, args.url.strip(), args.sender.strip(), secret)
    except ValueError as error:
        parser.error(str(error))
    print('Configurazione email salvata. Dati database preservati. Riavvia il backend.')


if __name__ == '__main__':
    main()
