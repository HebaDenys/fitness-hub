# Fitness Hub — Roadmap operativa

**Piano completo:** [MASTER_PLAN.md](MASTER_PLAN.md).  
**Punto da cui ripartire:** [PROGRESS.md](PROGRESS.md).  
**Revisione:** 1, 6 ottobre 2026.

Questa roadmap è un indice, non una seconda copia del backlog. Le vecchie Phase 1–7 e la strategia CSV storico/BLE principale sono state superate dalla decisione del proprietario: connettore Xiaomi Cloud integrato nello stesso APK, archivio locale completo, controlli cross-source e interoperabilità Health Connect.

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

Il backup e la sicurezza iniziano in T0/T1: la collocazione di T7 indica la prova completa di trasferimento, non il permesso di rimandare la protezione dei dati. QA e distribuzione sono attività continue. Parti indipendenti si possono completare quando una prova hardware è bloccata.

| Traguardo | Risultato concreto | Workstream principali |
|---|---|---|
| T0 | Build identificabile, dati protetti, limiti dichiarati. | SAFE, QA, DIST, PORT iniziale. |
| T1 | Login Xiaomi nell'app, proprio profilo, storico disponibile e nuova pesata senza export manuali. | XIA, DATA identità/persistenza, UX Fonti. |
| T2 | Uno stesso dato in Dashboard/Corpo/Insights, copie e anomalie gestite. | DATA, REC, BODY. |
| T3 | Tutti i tipi pertinenti disponibili, permessi corretti e write-back senza loop. | HC, REC, XIA export, QA integrazione. |
| T4 | Prodotto sconosciuto -> barcode/etichetta -> revisione -> riuso offline e diario. | NUT, UX, HC nutrizione. |
| T5 | Sessioni pesi/corpo libero/cardio registrabili, modificabili e riutilizzabili. | TRAIN, HC esercizio. |
| T6 | Follow-up con obiettivi storici, dati coerenti e report esportabili. | GOAL, LIFE, ANA, PORT report. |
| T7 | Backup -> altro dispositivo -> restore senza perdita di dati/relazioni. | PORT e QA Room/migrazioni. |
| T8 | Foto/testo -> bozza confermabile, con core indipendente dall'AI. | AI, SAFE/privacy. |
| T9 | Firma privata, licenza scelta, conformità e validazione reali. | DIST, LEGAL, QA. |

## Prossimo incremento

`FH-XIA-01`: parser/contratti Xiaomi Kotlin e fixture eseguibili, con upstream/licenza verificati. Seguono identità e provenienza comuni (`FH-DATA-01/02`) e i gate SAFE prima di usare credenziali reali. Vedere il criterio preciso e le dipendenze nel master.

## Regole di avanzamento

Un task inizia quando ha input e dipendenze sufficienti. Finisce solo con il percorso e le prove richieste; compilazione non equivale a funzionamento sul dispositivo. Nessuna percentuale globale gonfiata dal numero di file/test.

Non richiedere nuovamente approvazione per le attività tecniche già previste. Servono invece conferme per segreti, spese, cancellazioni, licenza, cambio identità/firma e distribuzione pubblica. Non schedulare lavoro futuro senza richiesta distinta.

**La 0.3.1 non contiene il connettore Xiaomi Cloud o il write-back Health Connect.** Il piano descrive il lavoro successivo, non li dichiara implementati.
