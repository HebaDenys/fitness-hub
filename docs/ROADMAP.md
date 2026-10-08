# Fitness Hub — Roadmap operativa

**Piano completo:** [MASTER_PLAN.md](MASTER_PLAN.md).  
**Punto da cui ripartire:** [PROGRESS.md](PROGRESS.md).  
**Aggiornamento:** 8 ottobre 2026, candidate 0.3.12.

Indice del backlog, non duplicazione. Le vecchie Phase 1–7 e la strategia CSV/BLE principale sono superate: Xiaomi Cloud nello stesso APK, archivio proprio e interoperabilità Health Connect.

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

Sicurezza e backup iniziano in T0/T1. T7 indica la prova completa di trasferimento, non il permesso di rimandare la protezione. QA e distribuzione sono continue; lavoro indipendente può avanzare mentre una prova hardware è bloccata.

| Traguardo | Risultato concreto | Workstream |
|---|---|---|
| T0 | Build identificabile, dati protetti, limiti dichiarati. | SAFE, QA, DIST, PORT iniziale. |
| T1 | Login Xiaomi, persona giusta, storico e nuova pesata senza export manuali. | XIA, DATA identità/persistenza, UX Fonti. |
| T2 | Stessi valori tra Dashboard/Corpo/Insights, copie e anomalie gestite. | DATA, REC, BODY. |
| T3 | Tipi pertinenti, permessi corretti e write-back senza loop. | HC, REC, XIA export, QA. |
| T4 | Prodotto sconosciuto -> barcode/etichetta -> revisione -> riuso e diario. | NUT, UX, HC nutrizione. |
| T5 | Allenamenti registrabili, modificabili e riutilizzabili. | TRAIN, HC esercizio. |
| T6 | Obiettivi storici, analytics coerenti e report esportabili. | GOAL, LIFE, ANA, PORT report. |
| T7 | Trasferimento/restore senza perdita di dati e relazioni. | PORT e QA Room. |
| T8 | Foto/testo -> bozza confermata, core indipendente dall'AI. | AI, SAFE. |
| T9 | Firma privata, licenza scelta, conformità e validazione reali. | DIST, LEGAL, QA. |

## Incrementi, non traguardi completi

0.3.2–0.3.6: protocollo/archivio Xiaomi gated, resolver corporeo e record Health Connect esatti. 0.3.7–0.3.10: Centro sorgenti, onboarding, grafici interattivi, timestamp HC, BLE receive-only, basi nutrizionali e CSV canonico. **0.3.11–0.3.12: inserimento corporeo manuale offline, schema 9 e candidate QA con policy no-INTERNET.** Esiti reali in PROGRESS.

**T1 resta incompleto:** runtime bloccato dal gate firma privata; challenge, account/regione/modello e compatibilità fisica non provati. La presenza della schermata non autorizza a dire che l'utente può già collegare Xiaomi. T7 resta incompleto: backup DB non include preferenze/media e trasferimento fisico/cross-schema ancora da completare.

## Prossimo incremento tecnico

**QA dispositivo + FH-SAFE-03:** installare la candidate sul Redmi, eseguire la checklist fisica/visuale e validare Health Connect/BLE/camera/restore. In parallelo decidere firma privata e migrazione prima di qualsiasi login Xiaomi reale. Dopo il device gate, riprendere checkpoint/tombstone HC e portabilità cross-schema.

In parallelo **FH-SAFE-03** richiede decisione del proprietario su firma privata, custodia e migrazione prima dei login reali. Non cambiare silenziosamente chiavi/package o togliere il gate. La decisione non impedisce test sintetici e lavoro sul dato canonico.

## Regole

Leggere codice/CI reali e consegnare incrementi verificabili. Niente nuove approvazioni per fasi tecniche già previste; restano per segreti, spese, cancellazioni, licenza, identità/firma e store. Nessuna esecuzione schedulata senza richiesta distinta.
