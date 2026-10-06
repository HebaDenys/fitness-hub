# Fitness Hub — Stato operativo e passaggio di consegne

**Aggiornamento:** 6 ottobre 2026. Piano vincolante: [MASTER_PLAN.md](MASTER_PLAN.md).  
**HEAD iniziale della sessione:** `1fce6ed88fa33619c54229214eecfbceb5555450`.  
**Ultimo codice verificato:** `1436986a7f045821d4b29ca27b2dccd758ad0826` — 0.3.4, versionCode 6, database 6.  
**CI verificata:** [37530351539](https://github.com/HebaDenys/fitness-hub/actions/runs/37530351539), job `112497827977`, conclusione `success`.  
**Chiusura:** aggiornamento solo documentale; nessun cambiamento al runtime/APK verificato.

## 1. Prossima attività precisa

**FH-XIA-05/06/10 — onboarding Xiaomi, selezione regione/modello/dispositivo/subject e stato delle sorgenti.**

Il percorso autenticazione -> richieste firmate -> parser -> committer Room esiste ora ed è verificato con risposte sintetiche. Non rifarlo, non tornare al CSV come percorso principale e non presentarlo come login reale già disponibile nell'interfaccia.

1. Rileggere HEAD, AGENTS, master, CI, contratto auth/sessione e codice UI/DI effettivi; eventuali regressioni hanno precedenza.
2. Introdurre un solo runtime/client riutilizzato tramite DI, senza più istanze concorrenti dello store/file della sessione.
3. Definire stato e metadata di connessione privi di segreti: la UI non deve ricevere direttamente serviceToken/ssecurity o il DTO sessione.
4. Costruire onboarding regione (scelta esplicita, non GPS), modello verificato/non verificato e scoperta dei device/subject realmente restituiti dalla sorgente. Il client login attuale ritorna Unit: aggiungere l'accesso ai soli metadati necessari senza esporre il session store.
5. Selezione esplicita della persona/dispositivo prima di confirmBinding; riutilizzare la protezione Room esistente, senza assegnazioni tramite nome/peso. Testare con più utenti/device e cambi account.
6. Collegare stato/errori della sincronizzazione e archivio alla UI con fixture; nessun finto stato «connesso» o completamento non dimostrato.
7. Gestione del completamento CAPTCHA/2FA/redirect STS resta da progettare sul normale protocollo vendor verificato. Oggi i challenge sono rilevati e interrompono il login, non sono completati.
8. **FH-SAFE-03:** chiave privata, custodia e percorso di migrazione richiedono decisione esplicita prima dei login reali. Non cambiare package/firma né chiedere password/token in chat. Il gate corrente è deliberatamente bloccato; non rimuoverlo come scorciatoia per la UI.
9. Dopo l'incremento eseguire build/test/lint, verificare SHA/release reali e aggiornare questo registro. I test senza credenziali non sono bloccati dall'assenza del telefono.

## 2. Consegna 0.3.4 — FH-XIA-02/03/04 con SAFE

### Autenticazione e trasporto

`XiaomiAuthentication` implementa il normale scambio xiaomiio in tre passaggi, con verifica sid/callback prima dell'invio dell'hash password. Non persiste password o il più ampio passToken dell'account. Distingue rifiuto credenziali, CAPTCHA, verifica aggiuntiva, protocollo cambiato ed errori HTTP senza riportare descrizioni private del vendor.

`XiaomiWireCrypto` implementa nonce, firma e RC4-drop1024 del protocollo upstream. Le primitive legacy servono soltanto alla compatibilità sotto HTTPS verificato; il file sessione usa AES-GCM, non RC4. Un vettore Go indipendente controlla firma e ciphertext, oltre ai round-trip.

`XiaomiHttpsTransport` usa HTTPS con validazione predefinita, endpoint e header autorizzati, nessun redirect automatico/cache/logger, byte/timeout/concorrenza limitati e disconnessione su cancellazione. La policy non accetta domini simili, userinfo, porte esplicite o percorsi arbitrari. Le misurazioni vendor restano read-only. I redirect STS, anche potenzialmente legittimi, oggi falliscono e richiedono revisione separata.

### Sessione e collegamento al database

`XiaomiSession` e codec conservano soltanto la sessione di servizio, la connessione/regione/UID e i tempi. Durata locale massima 24 ore o minore Max-Age positivo del cookie: è una policy dell'app, non una garanzia sulla scadenza del server. Nessun rinnovo con passToken è implementato.

`XiaomiProtectedSessionStore` cifra con AES-256-GCM e chiave AndroidKeyStore dedicata, AtomicFile in noBackupFilesDir, fuori da Room/backup. Non rigenera una chiave mancante durante lettura; corruzione/invalidazione richiedono nuovo login senza cancellare la salute locale. IO serializzato fuori dal thread UI. La cancellazione completa dalla memoria delle copie String JVM non è garantita.

`XiaomiAuthenticatedPageSource` verifica scope, modello, UID e richiesta; il coordinator `XiaomiCloudClient` lo collega a `RoomXiaomiArchive.read`. Le protezioni persona/device e transazioni/checkpoint esistenti restano. Logout interrompe operazioni correnti e in coda, impedisce a risposte tardive di ripristinare la sessione e non modifica misurazioni o binding. Non è ancora registrato un worker periodico Xiaomi.

**Gate concreto:** `XiaomiCloudRuntime.create` usa `AwaitingPrivateSigning`. Non può fare login reale nella build corrente; nessun flag permissivo o interruttore UI. Questo è un blocco esplicito in attesa della decisione di firma, non un controllo del certificato privato già implementato.

## 3. Registro attività

Uno stato vale solo per il criterio/livello di prova indicato. Gli ID non elencati mantengono lo stato del master o TODO.

| ID | Stato e prova / residuo |
|---|---|
| FH-XIA-01 | VERIFIED — protocollo/fixture 0.3.2 e committer reale 0.3.3. |
| FH-XIA-02 | IN_PROGRESS — normale autenticazione sintetica verificata in 0.3.4; challenge continuation e validazione account reale non eseguite. |
| FH-XIA-03 | IN_PROGRESS — adapter HTTPS/policy/firma/reader verificati con connessioni e risposte simulate; handshake e API vendor reali non provati. |
| FH-XIA-04 | IN_PROGRESS — sessione cifrata/file/expiry/logout verificati, collegati a Room; hardware Keystore Redmi e UI/worker non provati. |
| FH-XIA-05/06/10 | TODO — prossimo blocco di onboarding, selezione e stato UI. |
| FH-DATA-01/02 | IN_PROGRESS — binding/snapshot cloud nativo verificato in 0.3.3; ownership legacy/resolver universale/rebind restano. |
| FH-DATA-04 | VERIFIED — migrazioni v1–v5→v6 con Room/SQLite nativo; upgrade fisico ancora mancante. |
| FH-SAFE-01/04/05 | IN_PROGRESS — log allowlist, trasporto/sessione e segreti backup; audit di tutte le feature e UI fisica non concluso. |
| FH-SAFE-02 | AWAITING_DEVICE — policy OS testate in XML, non comportamento OEM. |
| FH-SAFE-03 | TODO — firma privata/custodia/migrazione da approvare; login reale bloccato, sviluppo/test sintetici consentiti. |
| FH-PORT-01/02/03/04/06 | IN_PROGRESS — backup DB v2 e IO verificati in 0.3.3; preferenze/media/cross-schema/prova fisica restano. |
| FH-QA-02 | IN_PROGRESS — suite Room e rete sintetica passate; nessuna equivalenza con QA hardware. |
| FH-UX-05 | TODO — ES completo; testi parziali in docs/localization. |

## 4. Verifiche effettive di questa sessione

### CI software

SHA `1436986a7f045821d4b29ca27b2dccd758ad0826`, [run 37530351539](https://github.com/HebaDenys/fitness-hub/actions/runs/37530351539), job `112497827977`: **success**, inclusa pubblicazione. Nessun tentativo fallito di questo commit.

- `./gradlew testDebugUnitTest lintDebug assembleDebug --stacktrace`: **BUILD SUCCESSFUL**.
- **346 test app / 37 suite: 0 failures, 0 errors, 0 skipped**. Sono 297 preesistenti più **49 nuovi test**, non 346 scritti qui.
- `python3 -m unittest discover -s ci -p 'test_*.py' -v`: **11 test passati**.
- Lint passato senza nuove disabilitazioni/baseline; deprecazioni/warning preesistenti rimangono.
- Verifica firma contro certificato CI test e controllo checksum: passati.
- [Report/schemi](https://github.com/HebaDenys/fitness-hub/actions/runs/37530351539/artifacts/11443449807) e [artifact APK](https://github.com/HebaDenys/fitness-hub/actions/runs/37530351539/artifacts/11443808527), soggetti alla retention.
- KSP rigenera lo stesso schema 6, blob `f9436689375524da4623aaba7a868e52c317a875`: nessuna migrazione/schema nuovo.

### Cosa coprono i 49 test nuovi

8 suite: URL/header/UTF-8/redaction; wire golden vector/nonce/form; login normale/challenge/cookie/cancellazione; source autenticata/errori; HTTPS adapter con connessione fake/bounds/cancel; cifratura/session store/tamper/key-loss; logout con risposte tardive/operazioni accodate; percorso login sintetico -> richieste cifrate -> Room/SQLite nativo -> replay/logout.

I test Room nuovi usano implementazioni generate e SQLite nativo sotto Robolectric API 28, non DAO mock. Il test file usa davvero AtomicFile/noBackupFilesDir nel runtime di test ma una chiave AES iniettata: NON dimostra hardware Keystore sul Redmi. I test HTTPS non fanno handshake remoto.

### Verifica locale e ricerca

Letti tramite GitHub HEAD, AGENTS, progress/master pertinenti, README/architettura, codice Room e Gradle, issue aperte (nessuna) e upstream. SmartScaleConnect confermato su `a9e5c04f1079b65d456c8a5fd296775a1ef29e8f`; MIT già attribuita nell'APK.

Eseguito localmente un generatore Go con crypto/rc4, sha1 e sha256 per il golden vector sintetico. Il tentativo Python iniziale mancava di un modulo e non è usato come prova. Il container non risolve github.com e non ha toolchain Android completo: nessuna build Android locale dichiarata; la verifica Android è quella CI sopra.

Nessun vero account/password/token, dato sanitario personale, telefonino, bilancia o challenge reale usato. Nessuna connessione reale ai servizi Xiaomi in questa sessione.

## 5. APK/release verificati

**0.3.4**, versionCode **6**, package **io.github.hebadenys.fitnesshub**, database **6**, dimensione **143111493 byte**.

[APK diretto](https://github.com/HebaDenys/fitness-hub/releases/download/test-latest/FitnessHub-v0.3.4-debug.apk) — [checksum](https://github.com/HebaDenys/fitness-hub/releases/download/test-latest/FitnessHub-v0.3.4-debug.apk.sha256).

SHA-256: `23d53de6d6fe22a993e2af540b607b853cd71e2452ddb9b85e58c6ff2fc9e64f`.

Asset `616649063`, caricato il 6 ottobre 2026 alle 21:00:30 UTC; release `test-latest` verificata sul commit software `1436986`. Firma test e package non cambiati. Il commit di chiusura modifica soltanto i documenti e non richiede ricompilazione dello stesso APK. Le release rolling possono cambiare: rileggerle prima di citarle in sessioni future.

## 6. Evidenze precedenti e limiti invariati

0.3.3: SHA `55ae7c0a9c3a5db55f81f6343633897f4b8b285b`, run 37525676439, 297 test/29 suite + 11 Python passati. Schema KSP 6 adottato in `1fce6ed`; identityHash `174b9871badd38370879c18980481f46`.

Backup v2 include le colonne di 19 tabelle dati/relazioni, non preferenze/media/credenziali/cursori. Massimo 32 MiB decifrati, schema identico, merge conservativo. V1 parziale ammesso solo su archivio salute vuoto. Non suggerire disinstallazioni o migrazioni di firma contando su trasferimento completo non provato.

HC resta a 11 tipi, finestra app 30/365, fasi sonno e write-back incompleti. Il binding Xiaomi iniziale è singolo/immutabile; resolver comune UI/analytics, deduplica semantica cross-source e completezza archivio restano da sviluppare.

Non c'è ancora una schermata Xiaomi operativa, selettore subject, sync periodico, completamento CAPTCHA/2FA o compatibilità provata S400 Pro/regione/firmware. L'esistenza del motore e i test verdi non chiudono T1.

Nessuna licenza finale, spesa, backend, firma nuova o esecuzione futura schedulata introdotti.

[Autenticazione/sessione](connectors/xiaomi-auth.md) — [Archivio](connectors/xiaomi-storage.md) — [Backup v2](backup-format-v2.md) — [Note 0.3.4](releases/0.3.4.md).
