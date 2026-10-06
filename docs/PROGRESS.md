# Fitness Hub — Stato operativo e passaggio di consegne

**Aggiornamento:** 6 ottobre 2026.  
**Piano vincolante:** [MASTER_PLAN.md](MASTER_PLAN.md), revisione 1.  
**HEAD letto all'inizio:** `81b4f853b2a349fd7cf94e6d0c1a7d11f6194662`.  
**Ultima baseline CI verificata prima di questa consegna:** `addbc7dd003b691dabcbe2d671dd646c2f73930b` — 0.3.1.  
**Incremento corrente:** 0.3.2 — protocollo Xiaomi e prerequisiti privacy; verifiche Android/CI da confermare dopo il commit.

## 1. Attività corrente e prossimo passo

**`FH-XIA-01` — IMPLEMENTED, in attesa della CI del commit consegnato.**

Implementati `core/xiaomi/XiaomiJson.kt`, `XiaomiScaleProtocol.kt`, `XiaomiHistoryReader.kt`, fixture sintetiche e test. [Protocollo, fonti e limiti](connectors/xiaomi-protocol.md). Nessun login reale, trasporto HTTP autenticato, tabella Room o pulsante fittizio aggiunto.

Alla ripresa controllare per prima cosa CI e release del commit corrente: eventuali regressioni precedono altri task. Non considerare l'assenza di credenziali un blocco ai test/modelli locali.

### Prossimo incremento pronto: `FH-DATA-01` / `FH-DATA-02`

Portare i risultati tipizzati verso una persistenza corretta, senza abilitare ancora login reali:

1. Ispezionare schema Room v5, migrazioni, DAOs, backup e codice `core/xiaomi` effettivi.
2. Definire profilo locale e binding persistente `(connessione, regione, UID, accountId/subject)`; il nome o il peso non identificano una persona. Una seconda persona non può essere importata nello stesso archivio senza una scelta esplicita.
3. Introdurre envelope/record e provenienza per metrica. Separare acquisizione cloud, stima vendor, peso riportato, qualità, unità e timestamp originali. Non presumere che `dataVersion` sia una revisione o che `sn` sia un ID globale: il protocollo letto non lo prova.
4. Collegare una implementazione del committer Xiaomi a transazione record/checkpoint idempotente, con fixture. Non mettere la rete in una transazione DB.
5. Aggiornare migrazioni/schema esportato e copertura del backup insieme ai nuovi dati; test SQLite/Room reali per binding, rollback e relazioni. Non sostituirli con Mockito.
6. Prima del trasporto/login continuare SAFE: session store, URL/redirect HTTPS autorizzati, errori, forme di challenge realmente supportate, firme e UI dei segreti.
7. Aggiornare questo registro con evidenze e prima azione successiva.

`FH-SAFE-03` richiede in seguito una decisione sulla firma privata e migrazione. Non cambiare package o chiave senza approvazione e non chiedere password/token in chat. Modello bilancia/companion e Android esatti sono ancora da verificare sul telefono, non da indovinare.

## 2. Decisioni già approvate

- Un solo APK nostro; Xiaomi Home e companion/Health Connect restano componenti del setup.
- Xiaomi Cloud diretto è il flusso principale da completare. CSV e BLE esistenti restano ripieghi.
- Niente root, script esterno obbligatorio, backend Fitness Hub o account Fitness Hub.
- Room è l'archivio proprio; lettura ampia e write-back HC devono essere completati con permessi/outbox.
- Tutti i dati realmente esposti e autorizzati, non accesso universale a ogni archivio/app.
- Originali, qualità, unità e identità conservati. Nessuna correzione o fusione silenziosa sulla sola somiglianza dei numeri.
- Lavoro diretto su `main` autorizzato, commit multi-file atomici, mai force-push.
- Nessuna licenza finale scelta o esecuzione automatica futura autorizzata da questo piano.

## 3. Registro delle attività

Gli ID non elencati sono `TODO`. Uno stato VERIFIED vale solo per i criteri e il livello di prova indicati, non per l'intero prodotto.

| ID | Stato | Risultato / criterio residuo |
|---|---|---|
| PLAN-R1 | VERIFIED — documentazione | Master plan e continuità salvati nel commit `81b4f85`. Nessuna funzione era dichiarata completata dalla sola pianificazione. |
| FH-XIA-01 | IMPLEMENTED — attesa CI | Request CN/globali, tre formati risposta, parsing rigoroso/limitato, identità ambigue e campi/unità/provenienza separati, paginazione e contratti di commit. Fixture sintetiche e test. Upstream `a9e5c04` e licenza MIT annotati/inclusi nell'APK. Nessuna chiamata reale. |
| FH-SAFE-01 | IN_PROGRESS | [Inventario iniziale](security/pre-cloud-audit.md) con manifest, logger, secret store AI/bindkey, trasporto AI e firma. Logger reso allowlist, DTO Xiaomi redatti. Restano audit esteso chiamate Log/UI/errori e confronto comportamento runtime/policy. |
| FH-SAFE-02 | IMPLEMENTED — attesa CI/device | Esclusioni esplicite di backup OS cloud e device transfer, domini credential/device-protected. Quattro test XML locali passati; prove merged manifest e Android/OEM reali ancora mancanti. |
| FH-SAFE-03 | TODO — decisione prima dei login reali | Firma privata e custodia/migrazione da concordare. Chiave pubblica test esistente non cambiata. Non blocca fixture e persistenza locale. |
| FH-DATA-01 | TODO — prossimo | Binding persistente persona/connessione/subject oltre al filtro del singolo CSV. |
| FH-DATA-02 | TODO — prossimo | Envelope e provenienza per metrica nel DB; i DTO Xiaomi sono già separati ma non sono persistenza definitiva. |

Stati: `TODO`, `IN_PROGRESS`, `BLOCKED`, `IMPLEMENTED`, `AWAITING_DEVICE`, `VERIFIED`, `DEFERRED`.

## 4. Baseline e limiti ancora reali

- CI iniziale [37499457080](https://github.com/HebaDenys/fitness-hub/actions/runs/37499457080) verificata verde; appartiene alla 0.3.1, non al nuovo incremento.
- Gateway HC: 11 tipi; fasi sonno non preservate, finestra applicativa 30/365 giorni, nessun write-back. Nessuna nuova garanzia di completezza introdotta in 0.3.2.
- Xiaomi: parser/contratti puri nuovi; CSV e BLE sperimentali esistenti. Nessuna sessione, sincronizzazione automatica o prova S400 Pro.
- Repository canonico condiviso da tutte le viste ancora da completare; nessun schema v6 in questo incremento.
- Backup portabile incompleto. Esclusioni OS non equivalgono a recovery lossless. Non disinstallare build con dati importanti contando su questo backup.
- La fonte Xiaomi upstream usa una euristica di pagina corta da 20 record; non è una prova che l'intero archivio sia stato restituito.
- Campi numerici vendor estesi preservati senza inventare unità; testo non classificato/identità/chiavi sensibili esclusi con issue. Non rivendicare preservazione di qualsiasi campo futuro.
- Issue e PR aperte: nessuna al controllo iniziale. Rileggere stato reale in ogni sessione.

## 5. Verifiche di questa sessione

**Eseguite localmente:** compilazione delle nuove classi pure con Kotlin/JVM disponibile e 42 metodi di test/assertion eseguiti tramite harness offline; quattro test Python di policy XML passati. L'harness usa un sostituto della sola annotazione JUnit per avviare i test, non il motore JUnit e non un emulatore Android. Il codice/assertions sono quelli del progetto; la suite JUnit reale è affidata alla CI.

**Non eseguita localmente:** build Gradle Android completa. L'ambiente locale non risolve GitHub per clonare il progetto e non dispone del toolchain Android completo. Le letture e scritture effettive sono fatte dal connettore GitHub; non inventare un risultato di build locale.

**Da confermare nella CI nuova:** `testDebugUnitTest`, `lintDebug`, `assembleDebug`, test Python CI, firma APK, checksum e release 0.3.2. Non usare una run vecchia come prova del nuovo commit.

**Non eseguiti:** login/rete Xiaomi, prova Redmi/S400/Mi Band, test Room/migrazioni/backup fisici. Nessuna credenziale reale richiesta né dato personale usato nelle nuove fixture.

## 6. Gate esterni

- Il login reale deve essere effettuato dall'utente nel canale sicuro dell'app, con profilo corretto ed eventuali verifiche vendor normali. Non pubblicare segreti o interi export sanitari.
- La variante della bilancia, regione Xiaomi, modello band e versione Android non impediscono parser/DB con fixture, ma impediscono dichiarazioni di compatibilità reale.
- Firma privata, store, costi e licenza definitiva richiedono decisioni specifiche; la presente autorizzazione non le inventa.

## 7. Cronologia

### 6 ottobre 2026 — Piano R1

Letti repository/CI/documenti reali; commit documentale `81b4f85` con master, progress e direzione Xiaomi Cloud integrato. Build non rieseguita per soli Markdown.

### 6 ottobre 2026 — FH-XIA-01 e prerequisiti SAFE

Letti HEAD, AGENTS/master/progress, README/roadmap/architettura/CONTRIBUTING, issue/PR, CI e upstream Xiaomi pinning/licenza. Sviluppate classi Kotlin di protocollo e relativi test; aggiunte regole esplicite backup/trasferimento e allowlist di logging. Questa è una consegna di codice, non una nuova pianificazione. Nessun backend, dipendenza pesante, tabella persistente, account reale o cambio chiave introdotto. Il commit e il risultato CI saranno registrati dopo la verifica.

## Template di chiusura

```text
Data e HEAD iniziale:
Task ID / risultato verificabile:
Commit / file:
Comandi locali realmente eseguiti:
CI: URL, SHA e conclusione:
APK: versione, SHA e URL effettivi:
Prove dispositivo/Room mancanti:
Limiti e blocchi:
Prossima attività e prima azione:
```
