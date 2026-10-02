#!/usr/bin/env python3
"""Test connection/TLS/authentication only. Never sends an email or prints credentials."""
import os
from pathlib import Path
import smtplib
import socket
import ssl
import sys


def settings():
    path=Path(__file__).resolve().parent.parent/'.env'
    values={}
    if path.exists():
        for line in path.read_text().splitlines():
            if '=' in line and not line.lstrip().startswith(('#','!')):
                key,value=line.split('=',1);values[key.strip()]=value.strip()
    for key in ('GYM_SMTP_HOST','GYM_SMTP_PORT','GYM_SMTP_USERNAME','GYM_SMTP_PASSWORD'):
        if key in os.environ:values[key]=os.environ[key]
    return values


def check(values):
    username=values.get('GYM_SMTP_USERNAME','')
    password=values.get('GYM_SMTP_PASSWORD','')
    if not username or not password:
        print('SMTP_CONFIG_MISSING: username o password non impostati.');return 1
    try:
        port=int(values.get('GYM_SMTP_PORT','587'))
        print('Verifica connessione SMTP…')
        with smtplib.SMTP(values.get('GYM_SMTP_HOST','smtp.gmail.com'),port,timeout=10) as client:
            client.ehlo()
            client.starttls(context=ssl.create_default_context())
            client.ehlo()
            print('Connessione e TLS riusciti. Verifica autenticazione…')
            client.login(username,password)
        print('SMTP_OK: connessione, TLS e autenticazione riusciti. Nessuna email inviata.');return 0
    except smtplib.SMTPAuthenticationError:
        print('SMTP_AUTHENTICATION_FAILED: verifica account Gmail e password per app.');return 1
    except ssl.SSLError:
        print('SMTP_TLS_FAILED: verifica certificati, data del PC e rete.');return 1
    except (TimeoutError,socket.timeout):
        print('SMTP_TIMEOUT: connessione SMTP scaduta; verifica firewall e rete.');return 1
    except socket.gaierror:
        print('SMTP_DNS_FAILED: server SMTP non risolvibile.');return 1
    except ConnectionError:
        print('SMTP_CONNECTION_FAILED: connessione SMTP rifiutata o interrotta.');return 1
    except (smtplib.SMTPException,OSError,ValueError):
        print('SMTP_CHECK_FAILED: verifica configurazione SMTP. Nessun dettaglio riservato mostrato.');return 1


if __name__=='__main__':sys.exit(check(settings()))
