# Fitness Hub — Stato operativo e passaggio di consegne

**Aggiornamento:** 6 ottobre 2026.  
**Piano:** [MASTER_PLAN.md](MASTER_PLAN.md), revisione 1.  
**HEAD letto all'inizio:** `be12732813e9d3c3732f9c36ef6e95ded7335257`.  
**Ultimo codice verificato:** `55ae7c0a9c3a5db55f81f6343633897f4b8b285b` — APK 0.3.3, versionCode 5, database 6.  
**CI:** [37525676439](https://github.com/HebaDenys/fitness-hub/actions/runs/37525676439), job `112481944634`, conclusione `success`.  
**Aggiornamento finale:** adozione dello schema KSP e documentazione; nessun nuovo cambiamento al runtime rispetto al commit verificato.

## 1. Prossima attività precisa

**`FH-XIA-02/03/04` con SAFE — autenticazione verificabile, trasporto e sessione Xiaomi.**

Il parser/reader e il nuovo committer Room esistono e sono testati. Non rifarli e non tornare al CSV come percorso principale. Il prossimo incremento deve colmare il collegamento fra protocollo e rete/sessione, usando test con risposte sintetiche prima di qualunque account reale.

1. Rileggere HEAD, AGENTS, master, codice, CI e documenti del connettore. Una regressione reale ha precedenza.
2. Riesaminare il protocollo di autenticazione upstream e fissare la revisione studiata. Nessun OAuth/QR/challenge inventato e nessun aggiramento CAPTCHA/2FA.
3. Implementare trasporto isolato HTTPS con host/redirect autorizzati, timeout, risposta limitata, cancellazione e errori sanitizzati; nessun logging di cookie, payload sanitari o credenziali.
4. Session store Keystore separato dal database/backup. Logout cancella la sessione e annulla lavori futuri senza cancellare le misurazioni locali. Testare invalidazione e scadenza con fakes/fixture.
5. Usare i contratti `XiaomiPageSource` e `RoomXiaomiArchive.read`, preservando binding e commit/checkpoint atomici. Non eseguire rete dentro transazioni Room.
6. Integrare successivamente selezione account/regione/device/subject e stato reale nella UI. Un pulsante finto «connesso» non completa l'integrazione.
7. `FH-SAFE-03` richiede decisione su firma privata/custodia/migrazione **prima dei login reali**. Nessuna password/token in chat, CI o fixture. Non cambiare package o chiave di firma senza approvazione.
8. Aggiornare questo registro con prove, limiti e primo passo seguente. I test senza segreti non sono bloccati dall'assenza di un account reale.

## 2. Consegna 0.3.3

### Persistenza Xiaomi

Schema 6 aggiunge `source_identity`, `xiaomi_bindings`, `xiaomi_snapshots`, `xiaomi_checkpoints`. Una connessione/regione/account/subject/dispositivo viene associata esplicitamente al profilo locale. Il primo binding è immutabile in questo incremento: altri subject, device o connection ID non possono sostituirlo silenziosamente.

`RoomXiaomiArchive` collega il reader paginato al database: replay identici non aggiungono snapshot; inserimento record e avanzamento cursore sono una sola transazione. Fallimento o cancellazione ripristinano entrambi. La ripresa usa il checkpoint salvato; vecchie generazioni di worker non possono scrivere nel passaggio nuovo.

Ogni snapshot conserva timestamp, identità, valori/rappresentazioni originali, unità, metodo vendor e qualità per metrica, oltre alle estensioni numeriche sanitizzate supportate. Contenuto cambiato resta separato; `dataVersion` e `sn` non sono trasformati in ID/revisioni certe senza prova. Duplicato di snapshot non significa deduplicazione universale fra tutte le fonti.

### Backup utilizzabile dalla UI

Insights usa `DatabaseBackupService`, formato cifrato v2. Include tutte le colonne delle **19 tabelle dei dati registrate**, con ID e relazioni: diario/cibi, esercizi/sessioni/serie/template, campioni HC, composizione/pesate e archivio Xiaomi. Lo snapshot DB è coerente; ripristino aggiunge righe mancanti, ignora quelle identiche e annulla tutto in caso di conflitto, relazione orfana o identità/hash Xiaomi incoerenti.

Sono esclusi credenziali, preferenze, immagini e cursori operativi. Limite 32 MiB decifrati, limiti di celle/nesting, stesso schema DB per restore v2. Non è ancora un clone completo dell'ambiente applicativo o una migrazione fra versioni diverse del formato/schema.

I vecchi file v1 restano parziali e si ripristinano soltanto in un archivio salute vuoto; catalogo esercizi e impostazioni profilo locale possono rimanere. L'adattatore legacy non può più sovrascrivere dati di un archivio in uso.

File picker con IO fuori dal thread UI e lettura limitata; archivio grande non renderizzato come testo, incolla limitato a 64 KiB. Passphrase mascherata, non salvata nello stato dell'Activity e svuotata dal campo dopo invio. Copie String JVM non sono garantite cancellabili dalla memoria. Il CSV peso rimane l'export del trend, non il backup grezzo di tutti i domini.

## 3. Registro attività

Uno stato è riferito al criterio e al livello di prova espliciti, non all'intero prodotto. Gli ID non elencati restano nello stato del master, `TODO` se mai iniziati.

| ID | Stato | Prove / residuo |
|---|---|---|
| PLAN-R1 | VERIFIED — documentazione | Piano approvato in `81b4f85`. Nessuna funzione dichiarata completa dalla sola pianificazione. |
| FH-XIA-01 | VERIFIED — protocollo/fixture | `b757f8e`, CI 37518253962; esteso qui con committer reale, non con login. |
| FH-SAFE-01 | IN_PROGRESS | Audit e logger allowlist preesistenti; audit rete/sessione/UI completa ancora richiesto. |
| FH-SAFE-02 | AWAITING_DEVICE | Regole backup OS e test XML presenti. Comportamento OEM non verificato. |
| FH-SAFE-03 | TODO — decisione prima dei login | Firma privata/custodia/migrazione da approvare; chiave pubblica test non cambiata. |
| FH-SAFE-05 | IN_PROGRESS | UI backup mascherata/no saved state e cleanup; revisione completa di tutte le UI dei segreti e prove fisiche ancora mancanti. |
| FH-DATA-01 | IN_PROGRESS — incremento cloud verificato | Binding persistente esatto e test di riapertura/isolamento. Restano ownership legacy, onboarding e rebind consapevole. |
| FH-DATA-02 | IN_PROGRESS — incremento cloud verificato | Envelope per-metrica persistito e round-trip. Non è ancora modello/resolver universale HC/CSV/nutrizione. |
| FH-DATA-04 | VERIFIED — percorso v1–v5→v6 sotto SQLite nativo | Cinque test eseguono le migrazioni e la validazione generata da Room, controllando righe/colonne sentinella. Restano prove upgrade fisico e casi storici non rappresentati dalle fixture. |
| FH-QA-02 | IN_PROGRESS — nuova suite Room passata | Rollback record/checkpoint, riapertura, concorrenza, migrazioni e backup con Room/SQLite nativi, non DAO mock. Non sostituisce tutta la QA hardware. |
| FH-PORT-01/02/03/04 | IN_PROGRESS — database v2 verificato | Tutte le tabelle registrate, snapshot coerente e restore merge conservativo testati. Preferenze/allegati, migrazioni cross-schema e trasferimento fisico restano. |
| FH-PORT-06 | IN_PROGRESS | IO fuori UI, limite file/test, nesting; UX processo ucciso/accessibilità e grandi archivi su telefono da verificare. |
| FH-UX-05 | TODO — lingua ES completa | Nuovi testi runtime EN/IT. Traduzioni ES backup conservate in `docs/localization`, non abilitate come locale parziale. |

## 4. Verifiche effettive

### CI finale

[Run 37525676439](https://github.com/HebaDenys/fitness-hub/actions/runs/37525676439), SHA software `55ae7c0a9c3a5db55f81f6343633897f4b8b285b`:

- `./gradlew testDebugUnitTest lintDebug assembleDebug --stacktrace`: **BUILD SUCCESSFUL**.
- **297 test app / 29 suite, 0 failures, 0 errors, 0 skipped**. Sono 261 preesistenti + 36 nuovi test, non 297 test scritti qui.
- **11 test Python CI/privacy passati**.
- Test Jupiter e Vintage/Robolectric eseguiti insieme. Le prove Room usano le implementazioni generate e SQLite nativo: API 28; un round-trip backup aggiuntivo API 35.
- Migrazione da ciascun vecchio schema, replay paralleli, rollback con errore SQLite iniettato, cancellazione, associazione persona/dispositivo, ripresa, tutte le colonne/relazioni del backup, reimport identico, conflitti, dati orfani e archive tampering esercitati.
- Lint passato senza disabilitazioni/baseline nuove; restano warning/deprecazioni, non si dichiara zero warning.
- Firma APK confrontata con certificato test esistente, checksum e pubblicazione riusciti.
- [Report e schemi](https://github.com/HebaDenys/fitness-hub/actions/runs/37525676439/artifacts/11442093019), soggetti alla retention.

### Schema esportato

KSP ha prodotto `app/schemas/io.github.hebadenys.fitnesshub.core.database.HealthDatabase/6.json`, blob **`f9436689375524da4623aaba7a868e52c317a875`**, identityHash `174b9871badd38370879c18980481f46`. Il file è stato letto/revisionato e adottato byte-per-byte dal blob generato dalla CI, non ricostruito manualmente. I quattro nuovi schemi/FK/indici corrispondono alla migrazione eseguita dai test.

Il commit finale di schema/documentazione non cambia codice, configurazione o APK: non è stata rilanciata la build solo per ricommittare il suo output. Le prove citate sono quelle dello SHA software sopra, non test inventati del commit documentale.

### Tentativi precedenti e correzioni

- `f64912a`, run37523163811: i cinque test di migrazione fallivano nel lettore delle fixture perché gli schemi vecchi omettono `indices` per tabelle senza indici. Corretta lettura opzionale; test mantenuti.
- `30669cb`, run37524511220: tutti i 297 test passati; lint rilevava copertura ES incompleta dopo l'aggiunta di soli nove testi backup. Rimossa la registrazione del locale parziale, conservando i testi per FH-UX-05; nessuna soppressione di lint.
- `55ae7c0`: tutti i gate superati.

### Limiti di verifica

Nessuna build Android completa eseguita localmente in questo ambiente; verifica effettiva tramite CI GitHub. Nessun login/chiamata Xiaomi, test fisico Redmi/S400/Mi Band, UI strumentata su dispositivo o trasferimento fra telefoni effettuato. Robolectric/SQLite nativo non è un telefono reale.

## 5. APK verificato

**0.3.3**, versionCode **5**, package **`io.github.hebadenys.fitnesshub`**, dimensione **142996805 byte**.

[APK diretto](https://github.com/HebaDenys/fitness-hub/releases/download/test-latest/FitnessHub-v0.3.3-debug.apk) — [Checksum](https://github.com/HebaDenys/fitness-hub/releases/download/test-latest/FitnessHub-v0.3.3-debug.apk.sha256)

SHA-256: `3866f54c3be085cd0e8b8e8c9fa61f04d170db733cfc88d9d779f06a19bf1372`.

La release verificata punta allo SHA software `55ae7c0`; il successivo commit di schema/documentazione non modifica l'APK. La release rolling può cambiare: ricontrollare metadati quando si riprende.

## 6. Cosa NON è ancora completo

Il collegamento Xiaomi live, session store, selettore del subject e UI dei dati cloud non sono implementati. Il binding è singolo/immutabile e non assegna automaticamente proprietà ai vecchi record CSV/BLE/HC. Deduplica semantica cross-source e repository canonico di tutte le viste restano lavoro successivo.

HC non è stato esteso da questa consegna: 11 tipi, finestra applicativa 30/365, fasi sonno e write-back incompleti. S400 Pro e modello/firmware effettivo non verificati.

Backup v2 copre le righe del database, non preferenze/media/credenziali; restore stesso schema, conflitti fail-closed. Backup v1 resta parziale. Non suggerire disinstallazioni alla cieca o migrazioni di firma contando su recupero completo non provato.

Chiave pubblica TEST-ONLY conservata, nessuna licenza definitiva scelta e nessuna esecuzione futura schedulata. Il proprietario ha autorizzato il lavoro diretto su `main`, non l'uso di credenziali reali in questo ambiente.

[Archivio Xiaomi](connectors/xiaomi-storage.md) — [Backup v2](backup-format-v2.md) — [Note release](releases/0.3.3.md).
