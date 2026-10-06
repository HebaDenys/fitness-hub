# Fitness Hub — Stato operativo e passaggio di consegne

**Aggiornamento:** 6 ottobre 2026.  
**Piano vincolante:** [MASTER_PLAN.md](MASTER_PLAN.md), revisione 1.  
**HEAD letto all'inizio della sessione:** `81b4f853b2a349fd7cf94e6d0c1a7d11f6194662`.  
**Ultimo codice verificato:** `b757f8e7c3df84ac444476874e990098154e710a` — 0.3.2 di test.  
**CI verificata:** [run 37518253962](https://github.com/HebaDenys/fitness-hub/actions/runs/37518253962), conclusione `success`.  
**Ultima modifica del registro:** documentale, non modifica il codice/APK verificato.

## 1. Prossima attività precisa

**`FH-DATA-01` / `FH-DATA-02` — Binding persistente persona/sorgente e provenienza nel database.**

Il prerequisito `FH-XIA-01` è concluso al livello di parser/contratti e fixture. Non rifarlo e non tornare al CSV come strategia principale. Il protocollo nuovo non è ancora collegato a un login o a un archivio Room: il prossimo incremento deve realizzare la persistenza corretta prima del collegamento live.

### Risultato atteso della prossima sessione

1. Rileggere HEAD/CI; risolvere eventuali regressioni prima di estendere il codice.
2. Ispezionare schema Room v5, migrazioni, DAOs, backup e `core/xiaomi` effettivi.
3. Definire profilo locale e binding persistente `(connessione, regione, UID, accountId/subject)`; il nome o il peso non identificano una persona. Una seconda persona non può essere importata nello stesso archivio senza una scelta esplicita.
4. Introdurre envelope/record e provenienza per metrica. Separare acquisizione cloud, stima vendor, peso riportato, qualità, unità e timestamp originali. Non presumere che `dataVersion` sia una revisione o che `sn` sia un ID globale: il protocollo letto non lo prova.
5. Collegare una implementazione del committer Xiaomi a transazione record/checkpoint idempotente, con fixture. Non mettere la rete in una transazione DB.
6. Aggiornare migrazioni/schema esportato e copertura backup insieme ai nuovi dati; test SQLite/Room reali per binding, rollback e relazioni. Non sostituirli con Mockito.
7. Prima del trasporto/login continuare SAFE: session store, URL/redirect HTTPS autorizzati, errori, challenge realmente supportate, firma e UI dei segreti.
8. Aggiornare questo registro con evidenze e prima azione successiva.

`FH-SAFE-03` richiederà una decisione sulla firma privata e migrazione prima dei login reali. Non cambiare package o chiave senza approvazione e non chiedere password/token in chat. Modello bilancia/companion e Android esatti sono ancora da verificare sul telefono; non impediscono lo sviluppo con fixture.

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
| FH-XIA-01 | VERIFIED — contratti e fixture | Commit `b757f8e`: request CN/globali, tre formati risposta, JSON rigoroso/limitato, identità ambigue e campi/unità/provenienza separati, paginazione e contratti di commit. 36 nuovi test Xiaomi passati nel motore JUnit della CI. Upstream `a9e5c04` e licenza MIT annotati/inclusi nell'APK. Nessun login, chiamata reale o persistenza nuova. |
| FH-SAFE-01 | IN_PROGRESS | [Inventario iniziale](security/pre-cloud-audit.md) con manifest, logger, secret store AI/bindkey, trasporto AI e firma. Logger reso allowlist con 6 nuovi test passati; DTO Xiaomi redatti. Restano audit esteso chiamate Log/UI/errori e confronto comportamento runtime/policy. |
| FH-SAFE-02 | AWAITING_DEVICE | Esclusioni esplicite di backup OS cloud e device transfer, domini credential/device-protected. Quattro test XML passati e risorse compilate nella build Android. Prove comportamento backup/restore OEM e merged-manifest approfondito ancora necessarie. |
| FH-SAFE-03 | TODO — decisione prima dei login reali | Firma privata e custodia/migrazione da concordare. Chiave pubblica test esistente non cambiata. Non blocca fixture e persistenza locale. |
| FH-DATA-01 | TODO — prossimo | Binding persistente persona/connessione/subject oltre al filtro del singolo CSV. |
| FH-DATA-02 | TODO — prossimo | Envelope e provenienza per metrica nel DB; i DTO Xiaomi sono già separati ma non sono persistenza definitiva. |

Stati: `TODO`, `IN_PROGRESS`, `BLOCKED`, `IMPLEMENTED`, `AWAITING_DEVICE`, `VERIFIED`, `DEFERRED`.

## 4. Codice consegnato

- `core/xiaomi/XiaomiJson.kt`: parsing JSON rigoroso e limitato, con errori statici; non dipende da Android, Room o rete.
- `core/xiaomi/XiaomiScaleProtocol.kt`: request descriptor, tre formati, metadati/subject, tempi distinti, per-metric method/unit/quality e payload selezionato.
- `core/xiaomi/XiaomiHistoryReader.kt`: fetch -> parse off-main -> committer -> cursore successivo; budget/pause, errori e cancellazione. Il committer resta un contratto da implementare in Room.
- Test `XiaomiJsonTest`, `XiaomiScaleProtocolTest`, `XiaomiHistoryReaderTest` e fixture JSON totalmente sintetiche.
- `core/sync/OperationalLogLine.kt`, `AppLogger.kt` e test: allowlist effettive, nessuna stringificazione di oggetti arbitrari.
- Manifest e `res/xml/backup_rules.xml`, `data_extraction_rules.xml`; `ci/test_privacy_policy.py`.
- Attribuzione MIT in `assets/licenses/SmartScaleConnect-MIT.txt`, [protocollo](connectors/xiaomi-protocol.md), [audit SAFE](security/pre-cloud-audit.md), [note 0.3.2](releases/0.3.2.md).

Nessun backend, nuova dipendenza di produzione, schema DB o cambio di firma introdotto.

## 5. Verifiche e distribuzione

### Verifica locale limitata

Compilate le nuove classi pure con Kotlin/JVM disponibile ed eseguiti 42 metodi di test/assertion tramite harness offline. Quattro test Python di policy XML passati. L'harness locale usava un sostituto della sola annotazione JUnit per avviare i test, non il motore JUnit e non un emulatore Android.

Non è stata eseguita localmente la build Gradle Android completa: l'ambiente non risolve GitHub per il clone e non dispone del toolchain Android completo. Letture/scritture effettive tramite connettore GitHub.

### Verifica CI effettiva

[Run 37518253962](https://github.com/HebaDenys/fitness-hub/actions/runs/37518253962), job `112456641140`, SHA `b757f8e7c3df84ac444476874e990098154e710a`: completati con successo.

- `python3 -m unittest discover -s ci -p 'test_*.py' -v`: **11 test**, passati.
- `./gradlew testDebugUnitTest lintDebug assembleDebug --stacktrace`: **BUILD SUCCESSFUL**.
- JUnit: **261 test, 21 suite, 0 failures, 0 errors, 0 skipped**. Sono 219 preesistenti più 42 nuovi controlli, non tutti scritti in questa sessione.
- Firma APK confrontata con il certificato CI test esistente: corrispondente.
- Checksum e pubblicazione `test-latest`: completati con successo.
- [Report di verifica](https://github.com/HebaDenys/fitness-hub/actions/runs/37518253962/artifacts/11438550805), soggetti alla retention della CI.

APK **0.3.2**, versionCode **4**, package `io.github.hebadenys.fitnesshub`, dimensione **142843285 byte**:

[Download diretto](https://github.com/HebaDenys/fitness-hub/releases/download/test-latest/FitnessHub-v0.3.2-debug.apk) — [checksum](https://github.com/HebaDenys/fitness-hub/releases/download/test-latest/FitnessHub-v0.3.2-debug.apk.sha256)

SHA-256: `5ae988a0a6b5ccf0a113e6ac451fe4306f4144a40e25069bdb4d1d3b42df92fd`.

Release verificata sul commit software `b757f8e`; un successivo aggiornamento solo documentale del registro non modifica tale APK. Le release rolling possono cambiare: verificare sempre i metadati prima di usarle come prova futura.

### Prove non eseguite

Login/rete Xiaomi, prova Redmi/S400/Mi Band, nuovi test Room/migrazioni/backup fisici. Nessuna credenziale reale richiesta né dato personale usato nelle nuove fixture. Le risorse backup compilate e i test XML non provano il comportamento di ogni OEM.

## 6. Limiti ancora reali

- Gateway HC: 11 tipi; fasi sonno non preservate, finestra applicativa 30/365 giorni, nessun write-back. Nessuna nuova garanzia di completezza in 0.3.2.
- Xiaomi: protocollo/contratti puri nuovi; CSV e BLE sperimentali esistenti. Nessuna sessione, sincronizzazione automatica o prova S400 Pro.
- Repository canonico condiviso da tutte le viste ancora da completare; nessun schema v6 in questo incremento.
- Backup portabile incompleto. Le esclusioni OS non equivalgono a recovery lossless. Non disinstallare build con dati importanti contando su questo backup.
- La fonte Xiaomi upstream usa una euristica di pagina corta da 20 record; non è prova che l'intero archivio sia stato restituito.
- Campi numerici vendor estesi preservati senza inventare unità; testo non classificato/identità/chiavi sensibili esclusi con issue. Non rivendicare preservazione di qualsiasi campo futuro.
- Issue/PR aperte: nessuna al controllo iniziale. Rileggere GitHub nelle sessioni successive.

## 7. Gate esterni

Il login reale deve avvenire nel canale sicuro dell'app, con profilo corretto ed eventuali verifiche vendor normali. Non pubblicare segreti o interi export sanitari. Firma privata, store, costi e licenza definitiva richiedono decisioni specifiche; non inventarle. Il modello bilancia, regione Xiaomi, band e Android non impediscono lo sviluppo locale ma impediscono dichiarazioni di compatibilità reale.

## 8. Cronologia

### 6 ottobre 2026 — Piano R1

Commit documentale `81b4f85` con master, progress e direzione Xiaomi Cloud integrato. Build non rieseguita per soli Markdown.

### 6 ottobre 2026 — FH-XIA-01 e prerequisiti SAFE

Commit software `b757f8e`. Letti HEAD, documenti vincolanti, issue/PR, CI e upstream Xiaomi pinning/licenza. Sviluppato il prerequisito Kotlin ed eseguiti i test sopra. Pipeline verde al primo commit atomico; release 0.3.2 verificata. Aggiornamento di chiusura solo Markdown con evidenze e task successivo; nessuna pianificazione sostitutiva del codice.

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
