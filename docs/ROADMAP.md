# Fitness Hub — Roadmap operativa

**Piano completo:** [MASTER_PLAN.md](MASTER_PLAN.md).  
**Punto da cui ripartire:** [PROGRESS.md](PROGRESS.md).  
**Aggiornamento:** 6 ottobre 2026, consegna 0.3.3.

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

0.3.2: parser/contratti Xiaomi e prerequisiti privacy. 0.3.3: binding persistente e archivio cloud con committer Room, migrazioni schema 6 e backup database v2 collegato a Insights. Test nativi SQLite e CI verificati nel registro.

T1 non è ancora completo: mancano trasporto autenticato, sessione, onboarding e prova vendor. T7 non è completo: backup v2 copre righe DB, non preferenze/media, e il trasferimento fisico/cross-schema resta da verificare.

## Prossimo incremento

`FH-XIA-02/03/04` con gate SAFE: ricerca autenticazione effettiva, trasporto HTTPS limitato e session store protetto, testabili senza credenziali reali. Usare il parser e il committer già presenti; non rifarli. Firma privata/custodia/migrazione da approvare prima dei login reali, non prima di ogni test con fixture.

## Regole

Leggere codice/CI reali e completare percorsi verificabili. Non chiedere nuovamente approvazione per attività tecniche già previste. Conferme restano per segreti, spese, cancellazioni, licenza, identità/firma e store. Non schedulare lavoro futuro senza richiesta distinta.
