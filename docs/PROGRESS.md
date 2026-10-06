# Fitness Hub — Stato operativo e passaggio di consegne

**Aggiornamento:** 6 ottobre 2026. Piano vincolante: [MASTER_PLAN.md](MASTER_PLAN.md).  
**HEAD iniziale:** `47049fdd78a45f53d99e3ffb20e6a5c2788b7229`.  
**Ultimo software verificato:** `f841579d222faf7df358b9d7472631b299198f80` — **0.3.5**, versionCode **7**, database **6**.  
**CI finale:** [37538929760](https://github.com/HebaDenys/fitness-hub/actions/runs/37538929760), job `112526860176`, conclusione **success**.  
**Chiusura sessione:** solo documentazione; non modifica runtime o APK del commit verificato.

## 1. Prossima attività precisa

**FH-DATA-05 + FH-BODY-02 + FH-REC-03 — repository canonico delle metriche corporee, ultimi valori datati e precedenza delle fonti.**

La schermata Xiaomi ora esiste ed è collegata a client/archivio. Non rifare onboarding o protocollo da zero. Il prossimo incremento deve risolvere la divergenza fra BodyViewModel, DashboardViewModel, InsightsRepository e CSV, usando gli stessi record/sorgenti selezionati.

1. Rileggere HEAD, AGENTS, master, CI e codice effettivo; una regressione reale viene prima delle nuove funzioni.
2. Leggere le viste Body/Dashboard/Insights, mapper DailySummary, store HC/CSV/BLE, XiaomiSnapshotCodec e modello degli snapshot prima di progettare il resolver.
3. Definire osservazioni corporee con origine/persona/tempo/unità/metodo e selezione deterministica per metrica. Conservare gli originali e rendere esplicite le alternative/ambiguità.
4. Non dedurre una revisione certa da `dataVersion` o `sn`; più snapshot possono essere lo stesso evento cambiato. Non contarli automaticamente come pesate separate, non cancellarli e non scegliere solo il contenuto letto per ultimo senza una regola motivata.
5. Ultimo peso/grasso ciascuno con data propria; un record dei passi odierno non rende odierno il peso. Intervalli dei grafici in giorni di calendario, non numero di righe. Più misure reali nello stesso giorno restano conservate.
6. Collegare progressivamente lo stesso repository a Corpo, Dashboard e Insights/export; testare uguaglianza dei valori a parità di fonti/intervallo, unità, stime e misure mancanti. La vista sorgente Xiaomi rimane un dettaglio grezzo, non sostituisce il resolver.
7. Evitare migrazioni generiche/EAV non necessarie. Se cambia lo schema, includere migrazioni, backup, test Room e JSON KSP reale nello stesso incremento.
8. Verificare test/build/lint, release e aggiornare questo registro senza gonfiare gli stati dal numero di classi.

**Decisione aperta FH-SAFE-03:** firma privata, custodia e percorso di migrazione prima dei login Xiaomi reali. La decisione deve essere esplicita: non togliere il gate, non cambiare firma/package e non chiedere password in chat. Mantenere installazione/dati correnti finché il trasferimento non è verificato. Il lavoro canonico/test sintetici non è bloccato da questa decisione.

## 2. Consegna 0.3.5 — FH-XIA-05/06/10

### Interfaccia e componenti

Nuova route **Impostazioni -> Xiaomi Home** (`sources/xiaomi`) collegata alla navigazione. Hilt fornisce un solo XiaomiCloudRuntime/client/store e XiaomiSourceGateway/Repository condiviso. La UI riceve solo metadati previsti, mai XiaomiSession, serviceToken o ssecurity.

La schermata mostra stato account/accesso, sorgente selezionata, ultima pagina salvata, intervallo archiviato e conteggio snapshot. Implementa i comandi login, discovery, scelta/conferma, import manuale/ripresa, stop, refresh e logout. Il motore è collegato, non sostituito con messaggi di successo finti.

**La build scaricabile mostra il blocco della firma privata e non rende disponibili campi username/password/login.** La route e la consultazione di record cloud già presenti funzionano senza sessione. Nessun account/record dimostrativo è inserito nel database dell'utente. L'assenza di dati cloud resta uno stato vuoto onesto; CSV/BLE/HC rimangono nelle sezioni esistenti.

### Scoperta e separazione delle persone

Regione scelta esplicitamente, non GPS/lingua/residenza. Modello scelto fra identificativi di protocollo noti o stringa vendor validata; modello noto non significa compatibilità hardware provata, soprattutto S400 Pro/varianti.

Discovery usa pagine del percorso storico già verificato di SmartScaleConnect. **Non enumera tutti i membri di una famiglia Xiaomi**: mostra solo subject/device osservati nelle risposte accessibili. Conta pagine/righe/identità irrisolte, consente ricerca precedente e limita l'operazione a 250 pagine/512 candidati. Prima della selezione nessuna pesata viene persistita.

La chiave del candidato include connessione, regione, login UID, modello, subject UID/accountId e device ID. Nomi opzionali da data.user.name rimangono effimeri e non determinano ownership; etichette con caratteri di controllo/bidi o troppo lunghe sono scartate. Due persone con stesso nome/peso restano distinte.

Nessun candidato preselezionato, neanche quando è unico. Occorrono scelta e seconda conferma. Chiave inventata/stale rifiutata; conferma verifica di nuovo account/sessione e binding Room. Riconnettere un UID/regione diverso non sostituisce silenziosamente l'archivio. Binding iniziale ancora singolo/immutabile, non rebind automatico.

### Stato, cancellazione e storico

Import manuale limitato a 20 pagine per click, ripresa dal checkpoint durevole per un backfill incompleto. Fine passaggio e pausa per limite sono distinti: arrivare al confine delle pagine non prova completezza del vendor. Un login non implica un import e un gate bloccato non viene descritto come account necessariamente errato.

ViewModel impedisce doppi avvii, cancella operazioni foreground uscendo, ignora risposte tardive e pulisce i segreti passati al client. Scadenza/401 aggiorna lo stato locale senza lasciare l'etichetta connesso. Logout rimuove sessione e lavoro, non record o proprietario. Errori tradotti e statici, niente body/cookie/eccezioni private nella UI.

Le nuove query Room leggono lo storico in pagine da 20 e calcolano l'intervallo salvato, senza caricare tutto. Dettagli per metrica: valore, unità, metodo vendor e flag qualità; record non interpretabile segnalato senza modificarlo. Il numero di snapshot è etichettato come tale, non come numero di pesate deduplicate. Questa vista non è ancora la serie canonica condivisa.

### Protezioni UI

Password mascherata, input limitati, nessuna credenziale in saved state/navigation/DTO della UI. Campi puliti su invio e stop; FLAG_SECURE impostato durante la schermata e stato precedente ripristinato quando viene rimossa. Non si promette cancellazione di tutte le copie String JVM né comportamento di ogni OEM.

## 3. Verifica effettiva

### Commit e CI

- Incremento principale: `ac804caaed9a3ae20ce8c0b96aa8f50448cc08ac`, [run 37538198094](https://github.com/HebaDenys/fitness-hub/actions/runs/37538198094), job `112524431901`: **success**, 376 test app/41 suite e 11 Python, lint/build/firma/release passati.
- Ritocco di sole quattro stringhe EN/IT: `f841579d222faf7df358b9d7472631b299198f80`. Distingue indisponibilità cloud da mismatch certo e login da import; nessuna modifica alla protezione del gate.
- **Run finale 37538929760**, job `112526860176`: **success**; `./gradlew testDebugUnitTest lintDebug assembleDebug --stacktrace`: **BUILD SUCCESSFUL**.
- **376 test app / 41 suite, 0 failures, 0 errors, 0 skipped**: 346 preesistenti + **30 nuovi test**.
- **11 controlli Python CI/privacy passati**. Nessuna soppressione nuova di lint/test; deprecazioni/warning preesistenti restano.
- Certificato APK confrontato con quello test esistente, checksum verificato e pubblicazione completata.
- [Report finali](https://github.com/HebaDenys/fitness-hub/actions/runs/37538929760/artifacts/11448136658) — [artifact APK](https://github.com/HebaDenys/fitness-hub/actions/runs/37538929760/artifacts/11448196670), soggetti alla retention.

### Copertura dei nuovi test

**8 discovery:** identificativi distinti con nome/peso uguali, candidato ripetuto, UID mancante non inferito, nome non sicuro, pagina piena/continuazione, vuoto, scope della chiave e redaction.

**8 repository con Room/SQLite nativo:** discovery senza salvataggio di altre persone, import solo del subject scelto, chiave inventata/stale, binding immutabile, logout/riapertura offline, paginazione 45 record con metodi/unità, replay, gate e regione diversa prima della rete.

**8 ViewModel:** scelta mai automatica, doppio tap, cancellazione/risposta tardiva, pulizia password su esito e rifiuto busy, sessione scaduta, logout conservativo, errori sanitizzati e pausa distinta da completamento.

**6 Compose sotto Robolectric:** campi credenziali assenti con gate, selezione e dialogo di conferma separati, dettaglio salvato accessibile da disconnesso, password mascherata/non ripristinata, ciclo FLAG_SECURE e callback di navigazione della card Impostazioni. Sono interazioni della UI Compose nei test, non screenshot o verifiche su Redmi fisico.

### Schema e ambiente

KSP rigenera lo stesso **schema 6**, blob `f9436689375524da4623aaba7a868e52c317a875`, identityHash `174b9871badd38370879c18980481f46`. Nessuna tabella/colonna nuova: query e DTO non richiedono migrazione. Suite migrazioni/backup preesistenti ancora passate.

Tentativo clone locale non riuscito perché github.com non risolveva nel container; **nessuna build Android locale dichiarata**. Codice e fonti letti tramite GitHub; verifica Android tramite CI. Consultati contratto upstream fissato a `a9e5c04f1079b65d456c8a5fd296775a1ef29e8f` e documentazione primaria Android/Robolectric per lifecycle e test. Nessun account reale, credenziale, TLS remoto o hardware usato.

Commit finale solo documentale: README, AGENTS, ROADMAP, ARCHITECTURE e questo registro allineati al software verificato, senza ricompilare un APK identico. Non attribuire i risultati a un altro SHA runtime.

## 4. APK verificato

**FitnessHub-v0.3.5-debug.apk**, versionCode **7**, package **io.github.hebadenys.fitnesshub**, database **6**. Dimensione **143493616 byte**. Certificato test invariato.

[APK diretto](https://github.com/HebaDenys/fitness-hub/releases/download/test-latest/FitnessHub-v0.3.5-debug.apk) — [Checksum](https://github.com/HebaDenys/fitness-hub/releases/download/test-latest/FitnessHub-v0.3.5-debug.apk.sha256).

SHA-256: `cd9977b7a1d6c358c623d6f0fc2606046cde51be6792f7ea30663180b14694ec`.

Asset `616800855`, caricato il 6 ottobre 2026 alle 22:14:49 UTC; tag `test-latest` verificato su `f841579d222faf7df358b9d7472631b299198f80`. La release rolling può cambiare: verificare metadati prima di citarla in futuro. Il successivo commit documentale non cambia l'APK.

## 5. Registro attività

| ID | Stato riferito all'incremento, non all'intero prodotto |
|---|---|
| FH-XIA-01 | VERIFIED — parser/contratti e fixture; reader/committer collegati. |
| FH-XIA-02/03/04 | IN_PROGRESS — motore e sessione testati; challenge/HTTPS vendor/Keystore fisico non validati. |
| FH-XIA-05 | IN_PROGRESS — regione/modello espliciti e discovery di storico nella UI testata; modello/account reale ancora da verificare. |
| FH-XIA-06 | IN_PROGRESS — identità esatte, doppia conferma, binding e sessione testati; rebind consapevole/live restano. |
| FH-XIA-10 | IN_PROGRESS — stato, comandi e dettaglio offline testati; login reale bloccato e validazione fisica/scheduler restano. |
| FH-DATA-01/02 | IN_PROGRESS — archivio cloud e ownership selezionata; legacy/resolver universale non completati. |
| FH-DATA-04 | VERIFIED — migrazioni v1–v5→v6 sotto Room/SQLite nativo; upgrade fisico non dimostrato. |
| FH-DATA-05 / FH-BODY-02 / FH-REC-03 | TODO — prossimo incremento di serie corporee canoniche condivise. |
| FH-SAFE-01/04/05 | IN_PROGRESS — trasporto/log/sessione e UI segreti testati; audit globale/OEM restano. |
| FH-SAFE-02 | AWAITING_DEVICE — policy OS testate in XML, non comportamento OEM. |
| FH-SAFE-03 | TODO — decisione esplicita firma privata/custodia/migrazione prima del login reale. |
| FH-PORT-01/02/03/04/06 | IN_PROGRESS — database backup v2 verificato; preferenze/media/cross-schema/prova fisica restano. |
| FH-QA-02 | IN_PROGRESS — Room e nuovo percorso sorgente passati; test fisici non sostituiti. |
| FH-UX-05 | TODO — ES completo; nuovi testi EN/IT, traduzioni ES parziali conservate fuori runtime. |

Gli ID non elencati mantengono lo stato del master/registro precedente o TODO. Nessuna percentuale globale dedotta dal numero di file/test.

## 6. Limiti da non nascondere

**T1 non è completo sul telefono.** La schermata esiste, ma AwaitingPrivateSigning blocca account reali; CAPTCHA/2FA non completati, redirect STS non seguiti e S400 Pro/regioni/firmware non provati. Nessun worker periodico Xiaomi. Binding iniziale unico/immutabile, vecchi dati CSV/BLE/HC non attribuiti automaticamente dal nuovo binding.

HC invariato: 11 tipi, finestra app 30/365, fasi sonno e write-back incompleti. Nessuna raccolta universale o deduplica semantica cross-source completata qui. Nessun cambiamento alle misure originali.

Backup v2: colonne/relazioni di 19 tabelle, non preferenze/media/credenziali/cursori; stesso schema e massimo 32 MiB decifrati, conflitti annullano tutto. V1 parziale. Non suggerire disinstallazione o cambio firma basandosi su un recupero completo non verificato.

Nessun test su Redmi/S400/Mi Band reali, Keystore hardware, challenge, sincronizzazione cloud reale o trasferimento fisico. Nessuna licenza finale, spesa, backend, firma nuova o attività futura automatica introdotta.

Evidenze precedenti: 0.3.4 SHA `1436986`, run 37530351539, 346 test/37 suite; 0.3.3 SHA `55ae7c0`, run 37525676439, 297 test/29 suite; entrambe più 11 Python e build/lint/firma/release verificati.

[Onboarding Xiaomi](connectors/xiaomi-onboarding.md) — [Autenticazione](connectors/xiaomi-auth.md) — [Archivio](connectors/xiaomi-storage.md) — [Backup](backup-format-v2.md) — [Note 0.3.5](releases/0.3.5.md).
