# Fitness Hub — Roadmap operativa

**Piano completo:** [MASTER_PLAN.md](MASTER_PLAN.md).  
**Punto da cui ripartire:** [PROGRESS.md](PROGRESS.md).  
**Aggiornamento:** 6 ottobre 2026, consegna 0.3.4.

Questa roadmap è un indice, non una seconda copia del backlog. Le vecchie Phase 1–7 e il percorso CSV storico/BLE principale sono superati: Xiaomi Cloud integrato nello stesso APK, archivio locale completo, controlli cross-source e interoperabilità HC.

## Percorso principale

```text
T0 Base verificabile e protezione dati
 -> T1 Xiaomi integrato senza CSV obbligatorio
 -> T2 Archivio canonico e riconciliazione
 -> T3 Health Connect esteso e bidirezionale
 -> T4 Diario alimentare completo
 -> T5 Allenamento completo
 -> T6 Obiettivi, analytics e report
 -> T7 Recupero/trasferimento lossless
 -> T8 AI facoltativa
 -> T9 Rilascio responsabile
```

Backup e sicurezza iniziano in T0/T1: T7 indica la prova completa di trasferimento, non il permesso di rimandare la protezione dei dati. QA e distribuzione sono continue. Parti indipendenti si possono completare quando una prova hardware è bloccata.

| Traguardo | Risultato concreto | Workstream |
|---|---|---|
| T0 | Build identificabile, dati protetti, limiti dichiarati. | SAFE, QA, DIST, PORT iniziale. |
| T1 | Login Xiaomi, proprio profilo, storico e nuova pesata senza export manuali. | XIA, DATA identità/persistenza, UX Fonti. |
| T2 | Stesso dato in Dashboard/Corpo/Insights, copie e anomalie gestite. | DATA, REC, BODY. |
| T3 | Tipi pertinenti disponibili, permessi corretti e write-back senza loop. | HC, REC, XIA export, QA integrazione. |
| T4 | Prodotto sconosciuto -> barcode/etichetta -> revisione -> riuso offline e diario. | NUT, UX, HC nutrizione. |
| T5 | Sessioni pesi/corpo libero/cardio registrabili, modificabili e riutilizzabili. | TRAIN, HC esercizio. |
| T6 | Follow-up con obiettivi storici, dati coerenti e report esportabili. | GOAL, LIFE, ANA, PORT report. |
| T7 | Backup -> altro dispositivo -> restore senza perdita di dati/relazioni. | PORT e QA Room/migrazioni. |
| T8 | Foto/testo -> bozza confermabile, core indipendente dall'AI. | AI, SAFE/privacy. |
| T9 | Firma privata, licenza scelta, conformità e validazione reali. | DIST, LEGAL, QA. |

## Incrementi verificati, non traguardi completi

0.3.2: parser/contratti Xiaomi e prerequisiti privacy. 0.3.3: binding persistente, archivio e committer Room, migrazioni schema 6 e backup database v2. 0.3.4: motore autenticazione in tre passi, trasporto HTTPS limitato, sessione cifrata e coordinamento del reader Room. Le prove del nuovo blocco usano risposte sintetiche e SQLite nativo; CI, firma e APK verificati nel registro.

**T1 non è completo:** mancano onboarding/selezione, completamento challenge, integrazione UI/periodica e prova reale vendor. Il runtime è bloccato fino alla decisione sulla firma privata; non è un login utilizzabile sul telefono. T7 non è completo: backup v2 copre righe DB, non preferenze/media, e trasferimento fisico/cross-schema resta da verificare.

## Prossimo incremento

`FH-XIA-05/06/10`: onboarding regione/modello/device/subject e stato delle sorgenti, riusando client/sessione/archivio già presenti. Test UI/contratti con fixture prima delle credenziali reali. `FH-SAFE-03` rimane per chiave privata/custodia/migrazione prima del vero login; non blocca lo sviluppo indipendente.

## Regole

Leggere codice/CI reali e completare percorsi verificabili. Non chiedere nuovamente approvazione per attività tecniche già previste. Conferme restano per segreti, spese, cancellazioni, licenza, identità/firma e store. Non schedulare lavoro futuro senza richiesta distinta.
