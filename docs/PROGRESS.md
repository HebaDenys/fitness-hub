# Fitness Hub — Stato operativo

Aggiornamento: 6 ottobre 2026. Piano vincolante: MASTER_PLAN.md.
HEAD iniziale: `47049fdd78a45f53d99e3ffb20e6a5c2788b7229`.

## Task attivo

**FH-XIA-05/06/10: onboarding, selezione e storico sorgente Xiaomi — incremento 0.3.5.**

Codice preparato; **CI del nuovo commit ancora da verificare**. Non dichiarare passati i test nuovi finché non è disponibile il loro risultato effettivo. Baseline verificata: 0.3.4, commit software `1436986a7f045821d4b29ca27b2dccd758ad0826`, run 37530351539 (346 test app, 11 Python, lint/build/firma/release passati).

## Modifiche di questo incremento

- Route Settings -> Xiaomi Home integrata nella navigazione, nessun secondo APK.
- XiaomiModule Hilt singleton per runtime/client/session store e repository.
- AccountInfo privo di token; discovery paginata dal percorso storico verificato, con nomi effimeri e identità esatte.
- Nessuna scrittura di misure durante discovery; selezione manuale anche per un solo candidato e conferma separata.
- Riconferma account/sessione corrente prima del binding Room; account diverso non può sostituire silenziosamente la sessione dello storico.
- UI per operazioni/errori, sync manuale a lotti, ripresa/cancellazione e logout non distruttivo.
- Storico locale con pagine da 20 record e dettagli valori/unità/metodi/qualità; numero snapshot distinto dal numero pesate.
- Password mascherata, non saveable, pulizia su invio/background e FLAG_SECURE durante la schermata.
- Test protocollo, Room/SQLite nativo, ViewModel e Compose/Robolectric. Tutti i dati sono sintetici.

**Gate non cambiato:** il runtime usa AwaitingPrivateSigning. Questa build mostra il blocco e non chiede credenziali reali. La schermata non va presentata come login Xiaomi già funzionante sul Redmi. Nessuna modifica di package, firma o database; versione 0.3.5/versionCode 7, schema 6.

## Verifica da concludere

Eseguire tramite CI `./gradlew testDebugUnitTest lintDebug assembleDebug`, 11 test CI/privacy, controllo schema/firma/checksum e pubblicazione; registrare SHA/run/esito reali. Non sopprimere test/lint se falliscono.

Clone locale tentato: github.com non risolvibile nel container; nessuna build Android locale dichiarata. Letture e scritture tramite connettore GitHub. Documentazione Android/Robolectric e contratto upstream consultati per lifecycle/test e discovery; nessun login/account reale usato.

## Prossimo blocco dopo verifica

**FH-DATA-05 / FH-BODY e REC pertinenti: resolver canonico condiviso**, includendo snapshot Xiaomi selezionati senza confondere versioni/copie con pesate diverse. Leggere gli ID/criteri esatti nel MASTER_PLAN prima di iniziare. In parallelo la decisione FH-SAFE-03 sulla firma privata/custodia/migrazione rimane necessaria prima del login reale; niente rimozione del gate o pubblicazione di chiavi private.

La schermata ora esiste: non rifare onboarding/controller da zero. Restano completamento challenge, verifica account/model/regione reali, scheduler Xiaomi, write-back HC e validazione fisica/accessibilità. Il binding iniziale rimane singolo e immutabile; nessun rebind distruttivo implicito.

## Evidenze precedenti e limiti

0.3.4: 346 test/37 suite + 11 CI, run 37530351539. 0.3.3: 297 test/29 suite + 11 CI, run 37525676439. Schema 6 generato da KSP: blob `f9436689375524da4623aaba7a868e52c317a875`, identityHash `174b9871badd38370879c18980481f46`.

Backup v2 copre 19 tabelle dati/relazioni, non preferenze/media/credenziali/cursori; stesso schema e massimo 32 MiB plaintext, conflitti annullano tutto. V1 resta parziale. Non suggerire disinstallazione contando su trasferimento completo non verificato.

HC non esteso da questa sessione: 11 tipi, finestra app 30/365, fasi sonno e write-back incompleti. Nuovo storico è una vista della sorgente, non ancora il resolver comune per Body/Dashboard/Insights. Compatibilità S400 Pro/Redmi e Keystore hardware non provati.

Nessuna licenza finale, costo, server, firma nuova o attività futura schedulata introdotta.

Riferimenti: connectors/xiaomi-onboarding.md, connectors/xiaomi-auth.md, connectors/xiaomi-storage.md, releases/0.3.5.md.
