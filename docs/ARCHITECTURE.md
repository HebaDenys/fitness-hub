# Fitness Hub — Architettura attuale e obiettivo

**Specifica di riferimento:** [MASTER_PLAN.md](MASTER_PLAN.md), sezioni 3–5.  
**Stato verificato:** [PROGRESS.md](PROGRESS.md).  
**Aggiornamento:** 6 ottobre 2026.

## 1. Vincoli

Una sola applicazione Android Kotlin/Compose, Hilt e Room, con Health Connect per l'interoperabilità. Il database dell'app conserva i dati usati da UI/analytics/export. Nessun account Fitness Hub, backend obbligatorio o infrastruttura server da far installare all'utente.

Local-first non vieta una connessione vendor facoltativa. La nuova integrazione Xiaomi diretta è approvata e deve vivere nello stesso APK. Xiaomi Home resta l'app normale della bilancia; la companion della band continua a gestire il wearable.

## 2. Baseline implementata

Alla baseline 0.3.1 (`addbc7dd003b691dabcbe2d671dd646c2f73930b`) esistono:

- un modulo `app`, package `io.github.hebadenys.fitnesshub`;
- `core/database`, gateway HC e coordinamento sync;
- database v5 con migrazioni e schema esportato;
- nutrizione, scala CSV/BLE, workout, analytics, backup e AI prototipali;
- feature Compose separate e componenti grafici.

Non esiste ancora un connettore Xiaomi Cloud integrato. Il gateway HC legge un sottoinsieme di tipi e non implementa write-back. Il merge delle misure bilancia in Corpo non è ancora una vista canonica riusata ovunque. Il backup non preserva ancora l'intero archivio.

Non citare come implementate tabelle o classi presenti solo nella vecchia documentazione: il modello definitivo deriva dal codice e dalle migrazioni eseguite nei test.

## 3. Flusso obiettivo, non ancora completo

```text
Companion band -> Health Connect gateway --+
                                           |
Xiaomi Home -> Xiaomi Cloud connector ------+-> ingestione/staging
                                           |        |
Barcode/OCR/manuale/workout ----------------+        v
                                         identità + normalizzazione
                                                    |
                                     validazione + riconciliazione
                                                    |
                                          Room e checkpoint
                                                    |
                          +-------------------------+-------------------+
                          |                         |                   |
                   repository canonici       outbox HC opt-in    backup/export
                          |
                 UI / obiettivi / analytics / report
```

## 4. Confini proposti

- **Protocollo:** HTTP, autenticazione, paginazione e parsing, isolati per vendor. Il parsing puro non dipende dalla UI.
- **Session storage:** credenziali/sessioni tramite componente dedicato e Keystore, separato dall'archivio sanitario e dai backup.
- **Ingestione:** associa persona/connessione, preserva unità/tempi/origini, applica validatori e salva transazionalmente.
- **Modello sanitario:** tabelle tipizzate con envelope comune e campi vendor versionati; niente refactor EAV globale preventivo.
- **Riconciliazione:** collegamenti fra copie e scelta per metrica, senza distruggere gli originali.
- **Repository canonici:** unica sorgente di numeri per Dashboard, Corpo, Insights e report.
- **Outbox:** export HC opt-in con identità/revisioni/retry; non reinserisce ogni dato appena importato da HC.
- **Presentazione:** stato leggibile, nessun I/O pesante nei callback Compose, revisione di correzioni e bozze.

Estrarre nuovi moduli Gradle solo se riduce un problema reale di build, dipendenze o manutenibilità.

## 5. Identità e provenienza

Le chiavi includono profilo locale, connessione sorgente, subject vendor, tipo e identificativo esterno. Nome utente e indirizzo hardware non sono sostituti universali di un ID persona.

Separare trasporto e metodo: `IMPORTED` non significa `MEASURED`. Una percentuale di grasso calcolata dal vendor resta una stima vendor; un peso hardware importato resta un peso hardware. Provenienza, algoritmo e stato qualità vanno per metrica, non soltanto sulla giornata.

Preservare più misure nello stesso giorno, timestamp/offset originali e assunzioni. Normalizzazione e smoothing non sovrascrivono i dati originali.

## 6. Affidabilità sync

Fetch fuori da transazioni lunghe, staging limitato, commit della pagina prima del checkpoint, replay idempotente. Token HC separati per scope quando necessario; metadati e ID conservati per gestire cancellazioni e cambi di data. Mutua esclusione delle sincronizzazioni della stessa connessione e retry con backoff limitato.

Il connettore Xiaomi rimane read-only verso il vendor. Non dedurre cancellazioni da una pagina incompleta o da un errore di rete. Non confondere `NO_DATA` con `TEMPORARY_ERROR`.

## 7. Sicurezza e portabilità

I payload sanitari selezionati possono essere mantenuti localmente per preservare i campi non mappati; cookie/header/password/token non appartengono a quei payload. Segreti mai in log, test reali, repository o report CI.

Impostare esplicitamente policy Android Auto Backup e device transfer. Distinguere sandbox, cifratura OS, eventuale cifratura del DB, Keystore e backup portabile con passphrase: non sono sinonimi e hanno proprietà di recupero diverse.

La chiave CI pubblica attuale è deliberatamente test-only: non autentica il publisher. Prima del normale utilizzo con login sensibili serve un canale con firma privata e una migrazione controllata. Non sostituire la firma o l'app ID senza discutere gli effetti sui dati già installati.

Backup completo, relazioni, migrazioni e test di restore sono parte del modello, non un'aggiunta finale. Nessun suggerimento di disinstallazione basato sul backup prototipale.

## 8. Tracciamento del lavoro

I task `FH-DATA-*`, `FH-REC-*`, `FH-HC-*`, `FH-XIA-*` e `FH-PORT-*` del master contengono i criteri verificabili. Questa pagina descrive confini e direzione; lo stato di avanzamento vive soltanto in `PROGRESS.md`.
