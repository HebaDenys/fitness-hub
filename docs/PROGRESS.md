# Fitness Hub — stato operativo

**Aggiornamento:** 8 ottobre 2026.  
**Branch operativo:** `main`.  
**Candidate:** 0.3.16, versionCode 18, database 9.  
**Checklist:** [qa/0.3.12-functional-checklist.md](qa/0.3.12-functional-checklist.md).

## Stato raggiunto

Il resolver corporeo è condiviso da Dashboard, Corpo, Insights ed export. Gli eventi distinti sono ordinati prima per tempo; la priorità della sorgente non può far vincere una misura vecchia. Eventi con stesso identificatore esplicito vengono riconciliati senza cancellare gli originali.

Health Connect conserva peso e body-fat a precisione record (ID, timestamp, source package) oltre alla cache giornaliera. Il manifest contiene solo permessi `READ_*` pertinenti e nessun write-back.

Lo schema 9 aggiunge `manual_body_measurements`: peso e/o body fat manuali, timestamp esplicito, validazione, source `MANUAL`, timeline canonica e backup. Corpo rimane utilizzabile anche senza permessi Health Connect.

Nutrizione distingue basi `PER_100G`, `PER_SERVING`, `LEGACY` e `UNKNOWN`; i dati vecchi non vengono reinterpretati. Il parser Open Food Facts è testato su fixture, ma la candidate non richiede INTERNET e il lookup remoto è hard-disabled. Manuale, catalogo locale e OCR on-device restano i fallback.

Il Centro sorgenti non presenta Mi Fitness/Google Fit come account collegati: mostra soltanto package effettivamente osservati nei record Health Connect. Xiaomi cloud rimane `AwaitingPrivateSigning`. AI remota è disabilitata nella candidate no-INTERNET.

Backup v2 include 22 tabelle dominio; restore resta stesso-schema, transazionale e conservativo sui conflitti. Export peso usa gli eventi canonici grezzi con timestamp/source/method, non la media mobile analytics.

## Evidenze già verificate

- 0.3.10 runtime `77b70a8d6ce8b1d469e4738debd3e52c71590222`: run 37844340676, 409 test / 50 suite, schema 8, firma/checksum verdi.
- 0.3.11 manual-body `68012a3e60263e3cbd22d29925673551e74ceb3f`: run 37846599737, **415 test / 52 suite**, 0 failure/error/skip; 11 controlli CI/privacy; schema 9 blob `abc11c83e2f2aba5514b2d0399704168c471da89`; APK SHA-256 `e13a7d8bb9eb60eef89359bc87dc5f10907adb2318e19d51105c1449341ea394`.

## QA visuale

La chat corrente non dispone di Android emulator/ADB. Sono state tentate vere catture Compose sotto Robolectric, non mockup:
- run 37847324194: `captureToImage` timeout su 7 schermate;
- run 37847946367: stesso risultato anche con `GraphicsMode.NATIVE`.

La candidate mantiene test di composizione/semantica su 360dp, 840dp, dark theme, font 1.5×, grafici e stati empty/error. Questi non equivalgono a ispezione pixel. La prova visiva reale resta un gate dispositivo.

## FH-UX-03/06 — Impostazioni dopo il setup (0.3.13)

- IMPLEMENTED: ingranaggio Dashboard sempre presente, indipendente da caricamento, dati vuoti, permessi, errori e sincronizzazione; route Impostazioni esistente senza reset dell'onboarding.
- Tema Performance B limitato a Dashboard, hub Impostazioni e barra a sei tab: background #11161C, surface #1D252E, testo #F4F7FB, lime #C6F26B. Nessuna migrazione verso le cinque tab del concept.
- Cards sorgenti con titolo, descrizione e stato in verticale per evitare competizione orizzontale a font grandi. Nessun controllo fittizio o modifica dei connettori.
- Il profilo per le stime già esistente rimane in Impostazioni -> Bilancia locale; non è stato introdotto un profilo generale.
- Quattro regressioni Compose/Robolectric aggiunte: accesso in tutti gli stati, sync, apertura/ritorno ripetuti con flag setup conservato e ripristino dello stato salvato della navigazione. Usano schermate reali con ViewModel di test, non Hilt/telefono.
- Verifica automatica completata: software `39bc6c2c4d0e74c5a583a893abe78603b32332a5`, [CI 37852052322](https://github.com/HebaDenys/fitness-hub/actions/runs/37852052322) verde. `testDebugUnitTest lintDebug assembleDebug`: **427 test / 56 suite, 0 failure/error/skip**, inclusi i quattro test nuovi; **14 controlli Python CI/privacy** verdi. Nessuna build locale eseguita.
- APK `FitnessHub-v0.3.13-debug.apk`, versionCode 15, SHA-256 `bbe84f0761ed847ff3a9c73252e8bcb166094b721a4627d736612b763ec860a4`. Firma test/checksum verificati dalla CI. Release target, tag `test-latest` e asset confrontati con lo stesso SHA software; artifact APK `11581959201`, report `11583235412`.
- Review read-only del design: token B e navigazione coerenti; descrizioni/stati delle cards mantengono font 12/11sp ereditati e restano da portare a 14sp insieme alla verifica font grande. Nessun claim di conformità visuale completo.
- QA pixel e ciclo di vita reale Android restano AWAITING_DEVICE; saved-state Compose non equivale a process death sul Redmi.

## FH-UX-02/03/06 — Profilo e connessioni leggibili (0.3.14)

- IMPLEMENTED: card profilo locale in Impostazioni con route dedicata allo stesso `user_profile` Room già esistente. Nessun account cloud, nuovo profilo o migrazione. La schermata Bilancia rimanda allo stesso editor dedicato, che precarica i campi salvati; nessun percorso alternativo aggira la conferma scarto.
- Validazione finita dei parametri (altezza >100–300 cm, età 10–120 già supportata dalle formule, sesso richiesto), virgola decimale, stato salvataggio e blocco doppio invio. Fallimento storage distinto dal profilo salvato con aggiornamento stime incompleto. Originali non modificati.
- Form ripristinabile tramite saved state Compose; uscita da modifiche non salvate con Annulla/Scarta. Il salvataggio conserva il comportamento di aggiornamento delle stime locali preesistente, ora dichiarato nel form.
- Hub raggruppato: profilo, sorgenti facoltative, inserimento corporeo manuale, dati/privacy. Manuale apre la vera schermata Corpo anche da Health Connect senza permessi/disponibilità. AI resta disabilitata e Xiaomi conserva il gate.
- Tema B esteso soltanto ai dettagli HC/Bilancia/profilo; testo secondario e status portati almeno a 14sp nei componenti coinvolti. Azioni HC e permessi impilati per font grandi.
- Tredici regressioni aggiunte: input/persistenza UI, virgola e NaN, scarto/cancel, saved-state, navigazione profilo, font 2×, validazione e salvataggio ripetuto/fallito. I test mock non sono prove Room/dispositivo.
- Run 37854102958 aveva trovato un bottone HC ad altezza nulla (weight verticale): corretto a fillMaxWidth senza rimuovere il test, aggiungendo una verifica di visibilità.
- VERIFICA AUTOMATICA: runtime `c18906e1c262797b08dcac795a77000bed19a2fa`, [CI 37854727080](https://github.com/HebaDenys/fitness-hub/actions/runs/37854727080) verde: **440 test / 58 suite, 0 failure/error/skip**, 14 controlli Python, test/lint/build e firma/checksum superati. Main, tag e release verificati allo stesso SHA prima dell'incremento successivo.
- APK 0.3.14/code16 SHA-256 `d981c15079c85322e2162bd447814a7210d69c007149fee7cb123c03545603ba`. Nessun test disabilitato; nessuna build locale o nuova cattura pixel.

## FH-UX-03/04/06 — Dashboard Performance e dettaglio passi (0.3.15)

- IMPLEMENTED: gerarchia Dashboard nero/lime con passi in evidenza, sonno/peso, contesto temporale esplicito, scorciatoie vere verso Corpo e Nutrizione. Dati giornalieri vecchi indicati come ultimi disponibili, peso con data/fonte indipendente.
- Vuoto e permessi mancanti rimangono distinti; entrambe le viste permettono inserimento corporeo manuale e connessioni. Nessun obiettivo 8000 o orario fittizio del concept viene aggiunto.
- Nuova route dettaglio passi con barre, selezione touch/slider e lista equivalente di tutti i giorni. Range 7/30/90 persistito nel saved state; ritorno normale alla Dashboard. Totale limitato ai valori disponibili e copertura mostrata.
- Corretto il campionamento del grafico: N giorni di calendario ancorati a oggi, non N righe sparse; giorni mancanti null, zeri conservati, record fuori intervallo/futuri esclusi dalla vista senza cancellarli.
- Contenuto Dashboard ora applica una volta il padding della toolbar anche a loading/content, che il vecchio handler non applicava nei propri slot.
- Dieci regressioni aggiunte su finestre sparse/anno nuovo/null/zero, scorciatoie, stato vuoto/font2×, tap/slider/lista e ripristino range/lista. Grafici delle altre feature non riscritti.
- Review grafico: lo zero usa un anello sulla baseline, null resta un vuoto; asse verticale max/metà/zero allineato alle griglie e assente senza valori. Il NavHost consuma gli inset del root e usa background B solo sulle route ridisegnate; test geometrico evita il doppio padding.
- VERIFICA AUTOMATICA: runtime `a033924c3be91df440e31e80e4446ee6dff9304a`, [CI 37858445321](https://github.com/HebaDenys/fitness-hub/actions/runs/37858445321) verde: **450 test / 60 suite, 0 failure/error/skip**, 14 controlli Python, test/lint/build e firma/checksum superati. Anche il test geometrico di consumo inset è passato.
- APK 0.3.15/code17 SHA-256 `6ad71bc9f757b24b0cdd4c333064905705f104160f42e9753e83fee06c4d0666`; main, tag e release confrontati allo stesso SHA prima dell'incremento seguente. Screenshot reali e valutazione pixel/TalkBack sul dispositivo ancora AWAITING_DEVICE.

## FH-BODY-02 / FH-UX-03/04/06 — Corpo e inserimento manuale (0.3.16)

- IMPLEMENTED: Corpo Performance B con ultimi valori indipendenti per metrica, fonte/metodo e timestamp al millisecondo/offset visibile. Nessun colore attribuisce un giudizio sanitario al delta.
- Grafico peso basato sugli eventi canonici esatti, non sull'ultima misura aggregata del giorno: più eventi nello stesso giorno rimangono selezionabili tramite touch/slider. Range 7/30/90 di calendario e interruzione delle linee nei giorni mancanti.
- Storico lazy di tutti i record metrici disponibili nel repository corrente, con fonte/metodo/orario; non è una promessa di completezza delle fonti vendor. Il repository/resolver e gli originali non sono stati riscritti.
- Form manuale spostato in route dedicata raggiungibile da Corpo, Dashboard e aiuti Settings/HC: peso e/o grasso, virgola decimale, selettori nativi data/ora più input esplicito, saved state, campi bloccati durante scrittura, esito live-region e conferma scarto. Zero body-fat conservato; dato assente distinto.
- Parser locale ora strict: date impossibili non vengono normalizzate; gli orari locali inesistenti/ambigui al cambio DST sono rifiutati esplicitamente (offset manuale non ancora selezionabile). Timestamp esatti già acquisiti via sorgenti restano conservati, anche nelle ore ripetute.
- Cancellazione coroutine non è un errore storage; doppio salvataggio simultaneo bloccato. Nessuna migrazione e nessuna modifica a login/gate Xiaomi, rete, credenziali, firma o permessi.
- Sedici nuove regressioni su eventi/stesso giorno/DST, parse strict/zero, salvataggio doppio/errori/retry/cancel, storico, selezione, draft/recreation/back, font2× e ritorno alla route originaria. Mock e saved state Compose non equivalgono a processo Android o hardware.
- Review: fallback HC `hc-day:*` distinti come sola data, orario non disponibile; non vengono presentati come eventi a mezzanotte nel grafico esatto. Rimangono nei valori disponibili/storico. Fixture specifiche aggiunte.
- Run 37861054605: 464 test, 463 passati; una vecchia ricerca semantica ambiguamente trovava lo stesso peso in card e grafico. Asserzione ora riferita alla card principale e alla sua visibilità, non rimossa.
- CI/APK dell'esatto nuovo commit da verificare. Prove pixel, picker nativi, TalkBack e telefono ancora da eseguire.

## Prossimo passo

1. verificare CI/APK della 0.3.16; poi proseguire con Diario/Nutrizione e Report/Backup, salvo cambio di priorità esplicito per il canale Xiaomi privato;
2. installare quella APK sul Redmi senza disinstallare dati importanti;
3. eseguire la checklist fisica: onboarding, navigazione, Health Connect, manual body, pasti/camera, grafici, backup file/restore di prova, dark/font grande;
4. provare S400/BLE e companion→Health Connect con dati di test;
5. solo dopo decidere firma privata/migrazione per aprire Xiaomi cloud login.

Non chiamare prodotto finito prima di questi gate.


## FH-SIGN-01 — preparazione pipeline (9 ottobre 2026)

Corpo 0.3.16 verificato sul commit 9c25fe3fd91bb0bd965d54964ef362ab37c20550: CI 37861713790 verde, 466 test / 63 suite, 0 failure/error/skip, 14 controlli Python, lint/debug APK, firma e pubblicazione. SHA-256 APK: 681978f8bdccc979c3e8989e4556071e9195ae3e5b8661ee9c0e554ccb8cef0d. Nessuna QA pixel/dispositivo.

Preparata pipeline firma privata manuale e inattiva, con build senza segreti, job firma isolato e guard fail-closed. Verifica setup separata senza environment/segretI. Ambiente concordato Dev Env, unico reviewer HebaDenys, self-review ammessa, bypass admin consentito esplicitamente. Scope segreti riportato dal proprietario; verifica API ancora da eseguire. Nessuna chiave letta o generata, nessuna firma privata prodotta. CI di questo incremento ancora da verificare. Dettagli e limiti: [security/private-signing.md](security/private-signing.md).
