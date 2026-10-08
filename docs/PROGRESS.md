# Fitness Hub — stato operativo

**Aggiornamento:** 8 ottobre 2026.  
**Branch operativo:** `main`.  
**Candidate:** 0.3.14, versionCode 16, database 9.  
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
- Run 37854102958: 440 test / 58 suite, 439 passati e 1 fallito; regressione reale trovata nel bottone HC Controlla stato (weight verticale, altezza nulla). Corretto a fillMaxWidth e aggiunta asserzione di visibilità; CI/APK dell'esatto nuovo commit da verificare; il precedente runtime 39bc6c2 rimane la baseline verde. Nessun test disabilitato; nessuna build locale o nuova cattura pixel.

## Prossimo passo

1. verificare CI/APK della 0.3.14; poi ispezionare su dispositivo insets/barre di sistema e proseguire il redesign per incrementi, mantenendo sei tab finché non viene eseguito un incremento dedicato;
2. installare quella APK sul Redmi senza disinstallare dati importanti;
3. eseguire la checklist fisica: onboarding, navigazione, Health Connect, manual body, pasti/camera, grafici, backup file/restore di prova, dark/font grande;
4. provare S400/BLE e companion→Health Connect con dati di test;
5. solo dopo decidere firma privata/migrazione per aprire Xiaomi cloud login.

Non chiamare prodotto finito prima di questi gate.
