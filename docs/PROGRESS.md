# Fitness Hub — Stato operativo e passaggio di consegne

**Aggiornamento:** 6 ottobre 2026.  
**Piano vincolante:** [MASTER_PLAN.md](MASTER_PLAN.md), revisione 1.  
**Baseline software verificata:** `addbc7dd003b691dabcbe2d671dd646c2f73930b` — 0.3.1 di test.  
**Questa consegna:** piano e documentazione; non una nuova versione dell'app.

## 1. Prossima attività precisa

**`FH-XIA-01` — Parser e contratti Xiaomi integrati e testabili, senza credenziali.**

Prima di iniziare, rileggere HEAD/CI. Se è emersa una regressione, risolverla prima. Non ripartire dal CSV/BLE come strategia principale: il proprietario ha approvato il connettore cloud dentro l'APK.

### Risultato atteso della prossima sessione

Un incremento di codice Kotlin che interpreti le risposte Xiaomi pertinenti, conservi identità/campi/unità disponibili, produca un risultato tipizzato e gestisca errori/paginazione tramite fixture. Non è ancora la promessa di un login funzionante.

1. Fissare il commit upstream SmartScaleConnect esaminato e verificare la licenza dei file riutilizzati.
2. Leggere `pkg/xiaomi/client.go`, `auth.go` e strutture correlate. Documentare soltanto ciò che è dimostrato dal protocollo, non endpoint inventati.
3. Introdurre un package isolato per Xiaomi cloud coerente con l'architettura attuale; nessuna dipendenza su UI/Room nella logica pura di parsing.
4. Fixture sintetiche per pagina valida CN/globale, assenza dati, campi extra, valori/unità mancanti, subject differenti, cursore ripetuto e risposta d'errore.
5. Conservare campi sanitari non standard con provenienza; escludere segreti/header/cookie. Non attribuire unità o accuratezza non documentate.
6. Testare il comportamento con JUnit e costruire l'app; verificare il risultato effettivo della CI.
7. Aggiornare questa pagina con file/commit, test, limiti e task successivo. Probabile seguito: `FH-DATA-01`/`FH-DATA-02` e i prerequisiti SAFE per il login.

### Non fare nel prossimo incremento

Non chiedere password Xiaomi in chat, non inserire credenziali in CI, non promettere S400 Pro supportata per analogia con S400 EU, non pubblicare un bottone «connesso» che non autentica niente. Non attivare il login reale senza aver affrontato firma, session storage, rete, backup OS e log.

## 2. Decisioni già prese, da non riaprire inutilmente

- Un solo APK Fitness Hub; companion Xiaomi/Health Connect rimangono componenti del setup, non un nostro secondo bridge.
- Niente root e niente lettura forzata dei file privati Xiaomi Home.
- Xiaomi Cloud diretto come percorso principale per storico e nuove pesate; CSV/BLE restano facoltativi.
- Room è l'archivio proprio. Lettura ampia e write-back HC da completare con permessi e outbox.
- Tutti i dati realmente disponibili e autorizzati, con capability registry; non ogni dato di ogni app a prescindere.
- Controlli di duplicazione, identità, qualità e unità comuni; niente correzioni silenziose dei valori.
- Nutrizione, training, analytics, backup/report e AI facoltativa sono nel piano approvato.
- Autorizzato lavoro diretto su `main` per le sessioni richieste, senza force-push.

## 3. Baseline verificata e limiti

| Elemento | Evidenza/stato |
|---|---|
| Codice esaminato | [Baseline](https://github.com/HebaDenys/fitness-hub/tree/addbc7dd003b691dabcbe2d671dd646c2f73930b). |
| CI baseline | [Run 37499457080](https://github.com/HebaDenys/fitness-hub/actions/runs/37499457080): build, test, lint, firma e pubblicazione completati. |
| Stato runtime | Nessuna nuova prova fisica o login Xiaomi è stata eseguita per questa pianificazione. |
| HC | Gateway legge 11 tipi. Le fasi sonno non sono preservate nell'attuale mapping; export HC non implementato. |
| Storico HC | Finestra applicativa 30/365 giorni: non è «tutto lo storico» né una descrizione del limite assoluto della piattaforma. |
| Xiaomi | CSV transazionale e BLE sperimentale presenti; connettore cloud assente. |
| Dati canonici | Merge bilancia nella schermata Corpo, non ancora repository comune completo a tutte le viste. |
| Backup | Protetto da cifratura ma incompleto come copertura/relazioni; non considerarlo lossless. |
| Firma | Chiave pubblica di test esistente; nessuna chiave privata di produzione impostata da questa sessione. |
| Identità hardware | Famiglia Redmi Note 12 indicata dall'utente; modello/versione effettivi da rilevare. Mi Band e S400/S400 Pro da identificare esattamente. Non pubblicare identificativi personali. |
| Issue/PR aperte | Nessuna al controllo del 6 ottobre 2026. Rileggere GitHub nelle sessioni successive. |

## 4. Registro delle attività

Gli ID del master non elencati qui sono `TODO`. Non duplicare l'intero backlog in questo file. Aprire una riga quando si inizia o si verifica un task, e conservare la prova del risultato.

| ID | Stato | Risultato / criterio residuo |
|---|---|---|
| PLAN-R1 | VERIFIED — documentazione | Piano con 17 workstream, 138 task e 24 scenari di accettazione; decisione Xiaomi e istruzioni di continuità allineate. Controllo redazionale di ID, link principali, ordine/dipendenze e limiti; diff GitHub limitato a 8 file Markdown. Nessuna funzionalità applicativa dichiarata completata da questo task. |
| FH-XIA-01 | TODO | Prossimo incremento eseguibile; nessun login richiesto per iniziare. |
| FH-SAFE-01 | TODO | Audit prima di connessioni reali: manifest, auto-backup, logger, segreti e firma. |
| FH-DATA-01 | TODO | Binding persistente della persona/subject, oltre al filtro del singolo file CSV. |
| FH-DATA-02 | TODO | Separare trasporto import e metodo/accuratezza per metrica. |

Stati: `TODO`, `IN_PROGRESS`, `BLOCKED`, `IMPLEMENTED`, `AWAITING_DEVICE`, `VERIFIED`, `DEFERRED`.

- `IMPLEMENTED`: codice presente, ma uno o più criteri di verifica possono essere ancora mancanti.
- `AWAITING_DEVICE`: completati i controlli automatici previsti; resta la prova hardware/account/telefono esplicitamente descritta.
- `VERIFIED`: criteri della specifica attività verificati, con livello di prova e riferimenti. Non significa automaticamente produzione pronta.
- `BLOCKED`: indicare dipendenza concreta e percorso alternativo utile; non ripetere lo stesso lavoro senza registrare il motivo.

## 5. Gate e dati ancora necessari

**Non bloccano parser, modelli e test:** versione Android esatta, modello della band, companion effettiva, regione/account Xiaomi, codice modello bilancia.

**Bloccano la validazione reale Xiaomi:** normale login effettuato dall'utente nell'app sicura, selezione del proprio subject, eventuali verifiche aggiuntive del vendor e confronto di alcune misurazioni sul dispositivo. Non chiedere di pubblicare password, token o l'intero storico.

**Bloccano il normale uso sensibile/distribuzione affidabile:** identità di firma privata e processo di custodia/migrazione, verifiche SAFE, policy dei segreti e backup. Sono richieste decisioni esplicite quando si arriva a quel punto, non ora per produrre fixture.

**Bloccano commercializzazione/licenza definitiva:** scelta del proprietario sul perimetro commerciale e revisione del testo. Questa pianificazione non cambia la licenza del software.

## 6. Template per chiudere ogni sessione

```text
Data:
HEAD letto all'inizio:
Task ID e obiettivo della sessione:
Modifiche/file:
Commit consegnati:
Test locali realmente eseguiti:
CI: URL, SHA, conclusione oppure ancora non verificata:
APK/release: URL e SHA se applicabile:
Test Room / emulatori / dispositivo: eseguiti oppure mancanti:
Cosa funziona e con quali limiti:
Problemi o blocchi nuovi:
Stato aggiornato del task:
Prossimo task pronto e sua prima azione:
```

Per sessioni solo documentali: test Android non rieseguiti salvo cambiamenti eseguibili; controllare invece link, task ID, dipendenze, contraddizioni e diff. Non generare un APK identico solo per aumentare versioni o conteggi.

## 7. Cronologia minima

### 6 ottobre 2026 — Piano R1

Riletti repository, istruzioni, roadmap/architettura, manifest/gateway/sync/backup disponibili alla baseline, issue/PR e stato CI. Il nuovo piano rende esplicito Xiaomi Cloud integrato, completezza per capacità, outbox HC, integrità cross-source e recupero. Sostituite le istruzioni obsolete che vietavano il cloud Xiaomi o richiedevano nuova approvazione per ogni fase. Nessun cambiamento al codice applicativo e nessun login effettuato.

Consegna documentale in un unico commit su `main`, con controllo del diff e protezione dal sovrascrivere HEAD concorrenti. Non rieseguita la build Android: `[skip ci]` evita una nuova pipeline/APK identica per soli Markdown. La CI verde citata sopra appartiene alla baseline software, non a un nuovo test del piano.

L'ultima build software verificata rimane la baseline indicata sopra finché una successiva sessione non modifica e verifica il codice. Non confondere questo aggiornamento del piano con l'implementazione delle nuove funzioni.
