# Fitness Hub — Piano completo di prodotto e sviluppo

**Revisione:** 1 — 6 ottobre 2026.  
**Titolare del progetto:** Denys Heba.  
**Repository:** https://github.com/HebaDenys/fitness-hub  
**Baseline del codice esaminato:** `addbc7dd003b691dabcbe2d671dd646c2f73930b`, versione di test 0.3.1.  
**Stato operativo e prossima attività:** [PROGRESS.md](PROGRESS.md).  
**Ordine dei traguardi:** [ROADMAP.md](ROADMAP.md).  
**Istruzioni per ogni sessione:** [AGENTS.md](../AGENTS.md).

> Questo documento descrive ciò che dobbiamo ottenere, non ciò che è già implementato. Le decisioni approvate qui sostituiscono la precedente strategia che richiedeva CSV manuali per Xiaomi e privilegiava BLE. Nessuna funzionalità diventa completa perché è nominata nel piano o perché esiste una classe con quel nome.

## Indice

1. [Obiettivo e decisioni](#1-obiettivo-e-decisioni)
2. [Baseline reale e lacune](#2-baseline-reale-e-lacune)
3. [Significato di tutti i dati](#3-significato-di-tutti-i-dati)
4. [Architettura e modello dati](#4-architettura-e-modello-dati)
5. [Sincronizzazione e qualità](#5-sincronizzazione-e-qualità)
6. [Backlog eseguibile](#6-backlog-eseguibile)
7. [Traguardi e dipendenze](#7-traguardi-e-dipendenze)
8. [Scenari di accettazione](#8-scenari-di-accettazione)
9. [Metodo di lavoro continuativo](#9-metodo-di-lavoro-continuativo)
10. [Rischi e decisioni ancora aperte](#10-rischi-e-decisioni-ancora-aperte)
11. [Fonti e verifiche](#11-fonti-e-verifiche)

## 1. Obiettivo e decisioni

### 1.1 Risultato da ottenere

Una sola applicazione Android per raccogliere, conservare, consultare e analizzare alimentazione, allenamenti, peso, composizione corporea, attività, sonno e altri dati salute disponibili. L'app deve rendere possibile un follow-up personale nel tempo, con obiettivi modificabili, statistiche e report esportabili.

Il primo caso reale è un telefono Android non rooted, una Xiaomi Mi Band e una bilancia Xiaomi della famiglia S400 usata tramite Xiaomi Home. Non si cambia hardware per adattarlo al software. Le informazioni precise sul modello della band, sulla variante della bilancia e sulla versione Android devono essere confermate nell'app: non vanno indovinate.

La misura del successo è un percorso completo: nuova pesata o nuovo pasto -> importazione/inserimento -> storico locale -> grafico coerente -> esportazione utilizzabile -> eventuale condivisione con Health Connect. Non è il numero di schermate, connettori dichiarati, commit o test.

### 1.2 Decisioni approvate

| Decisione | Requisito vincolante |
|---|---|
| D01 — Android locale | Kotlin, Compose, Room/SQLite e confini di responsabilità chiari. Nessun backend obbligatorio, server, Docker, Firebase obbligatorio o account Fitness Hub. |
| D02 — Un APK nostro | Connettori, diario, statistiche e report dentro Fitness Hub. Xiaomi Home e l'app companion della band restano installate; Health Connect resta il componente Android separato quando richiesto dal sistema. Nessun Python, Go CLI o bridge esterno da avviare per l'uso normale. |
| D03 — Xiaomi diretto | Il flusso principale da costruire è Xiaomi Home -> servizi Xiaomi -> connettore integrato -> database Fitness Hub. Storico disponibile e nuove misurazioni attraverso lo stesso collegamento. |
| D04 — Niente root | Non leggere la sandbox privata di Xiaomi Home tramite root, exploit, mod dell'app o automazione dell'accessibilità. Il connettore cloud usa soltanto l'account autorizzato dall'utente. |
| D05 — CSV/BLE secondari | Conservare gli import e il codice esistente senza distruggere i dati. CSV diventa migrazione/ripiego; BLE resta sperimentale e facoltativo. Non deve impedire il collegamento cloud. |
| D06 — Archivio proprio | Health Connect è una sorgente/destinazione interoperabile. L'app conserva i propri record, metadati e storia senza dipendere dall'apertura delle altre app per consultare quanto già acquisito. |
| D07 — Nessun dato inventato | Mancante, negato, non supportato e zero misurato sono stati diversi. Valori originali, normalizzazioni, correzioni manuali e stime rimangono distinguibili. |
| D08 — Interoperabilità | Leggere progressivamente i tipi pertinenti esposti da Health Connect e condividere soltanto i dati eleggibili, con consenso e prevenzione dei loop. Non promettere di leggere ogni database di ogni app. |
| D09 — Uso personale gratuito | Niente pubblicità, abbonamento obbligatorio o telemetria comportamentale. Codice pubblico; uso commerciale da disciplinare con licenza separata. Il testo legale finale richiede una decisione esplicita. |
| D10 — AI facoltativa | Barcode, inserimento manuale e funzioni fondamentali indipendenti da AI cloud. AI con provider/API autorizzate o modello locale, consenso, revisione e controllo dei costi. Nessun riuso clandestino di sessioni consumer. |
| D11 — Continuità | Nelle sessioni richieste si procede dal piano senza richiedere nuovamente l'approvazione delle fasi già incluse. Rimangono necessarie approvazioni per credenziali, costi, cancellazioni, licenza e pubblicazione di dati privati. |
| D12 — Main | Il proprietario ha autorizzato il lavoro diretto su `main`. Commit coerenti e atomici, senza force-push e senza sovrascrivere modifiche concorrenti. |

### 1.3 Cosa non significa local-first

Local-first non vieta di collegarsi a Xiaomi, Open Food Facts o a un provider scelto dall'utente. Significa che l'archivio e le funzioni fondamentali sono locali e che le dipendenze remote sono esplicite. Il connettore Xiaomi richiede Internet per aggiornarsi e un account Xiaomi per quella funzione; non richiede un account Fitness Hub o un nostro server.

### 1.4 Confini di prodotto

L'app è uno strumento personale di monitoraggio e benessere, non un dispositivo diagnostico. Non deve prescrivere terapie, dedurre malattie o promettere una composizione corporea esatta. Obiettivi numerici personali, credenziali e dati della famiglia non vanno codificati nel repository pubblico. Tutte le fixture devono essere sintetiche o realmente anonimizzate.

## 2. Baseline reale e lacune

La verifica parte dal codice alla baseline, non dai vecchi README che descrivevano come complete funzioni ancora parziali. Il [workflow della baseline](https://github.com/HebaDenys/fitness-hub/actions/runs/37499457080) ha completato build, test, lint, verifica della firma e pubblicazione. Questo non certifica l'esecuzione sul telefono o la compatibilità della bilancia.

| Area | Presente alla baseline | Lavoro ancora necessario |
|---|---|---|
| App | Kotlin/Compose/Hilt, un modulo Android, Room con schema v5, schermate principali. | Migrazioni realmente eseguite nei test, accessibilità, navigazione e flussi completi. |
| Health Connect | Lettura di 11 tipi: passi, distanza, energie attiva/totale, esercizio, HR, HR a riposo, SpO2, sessioni sonno, peso, grasso. | Registro delle capacità, altri tipi, dettaglio sonno, persistenza completa e write-back. |
| Storico HC | Finestra applicativa di 30 giorni oppure 365 con accesso storico. | Non confondere questa scelta con il limite della piattaforma. Backfill progressivo di tutto lo storico autorizzato disponibile. |
| Sync HC | Changes token e WorkManager presenti. | Gestione indipendente dei permessi/tipi, accesso in background, checkpoint, cancellazioni, modifiche e intervalli multipli. |
| Xiaomi | Import SmartScaleConnect CSV e ricezione BLE sperimentale. | Nessun login cloud integrato. Nessun recupero automatico Xiaomi nella 0.3.1. |
| Deduplicazione | Unicità locale, import CSV transazionale, alcune protezioni contro riscrittura delle composizioni importate. | Deduplicazione semantica cross-source, identità persistenti delle persone, aggiornamenti e audit. |
| Corpo | Merge HC/bilancia nella schermata Corpo. | Dashboard, Corpo, Insights e report devono usare un unico resolver; niente precedenze improvvisate per schermata. |
| Nutrizione | Catalogo locale, OCR/barcode, diario e Open Food Facts prototipali. | Quantità/basi nutrizionali corrette, ingredienti, ricette, revisione, storico modifiche, export HC. |
| Allenamenti | Esercizi, sessioni, serie, RPE/RIR e calcoli PR/1RM. | Sessioni complete editabili, template, corpo libero/assistenza, cardio, export e riconciliazione con wearable. |
| Backup | Contenitore cifrato e file picker. | Non copre tutte le tabelle e relazioni; restore non dimostrato lossless. Non raccomandare disinstallazione contando su questo backup. |
| Analytics | Grafici e correlazioni di base. | Serie canoniche, copertura, finestre reali di calendario, statistiche riproducibili e report. |
| AI | Configurazione BYOK e trasporto prototipale. | Risposte strutturate, revisione integrata nel diario, privacy e policy dei provider. |
| Distribuzione | APK di test con chiave deliberatamente pubblica, checksum e release mobile. | Una chiave pubblica non autentica il distributore: canale privato firmato prima dell'uso normale con credenziali sensibili. |
| Licenza | Direzione commerciale documentata, licenza finale non scelta. | Non presentare il progetto come OSI open source o come già licenziato PolyForm. |

Riferimenti al codice: [gateway HC](../app/src/main/java/io/github/hebadenys/fitnesshub/core/healthconnect/HealthConnectManager.kt), [sync HC](../app/src/main/java/io/github/hebadenys/fitnesshub/core/sync/HealthSyncRepository.kt), [database](../app/src/main/java/io/github/hebadenys/fitnesshub/core/database/HealthDatabase.kt), [backup](../app/src/main/java/io/github/hebadenys/fitnesshub/core/backup/BackupService.kt), [note 0.3.1](releases/0.3.1.md). Rivalutare questi file prima di ciascun intervento: il piano non è una copia immutabile dello stato del codice.

## 3. Significato di tutti i dati

### 3.1 Contratto di completezza

L'obiettivo è acquisire tutti i dati pertinenti che le fonti autorizzate rendono effettivamente disponibili, conservarne il significato e rendere visibili quelli non ancora supportati. Non è una promessa di recuperare informazioni che Xiaomi o una companion app non esportano.

Per ogni metrica e connettore registrare: supporto della sorgente, supporto del dispositivo, permesso, tipo/endpoint, intervallo storico, precisione, unità, ultima acquisizione, aggiornamenti/cancellazioni disponibili, persistenza, visualizzazione, export. Stati separati: `SUPPORTED`, `NO_DATA`, `PERMISSION_DENIED`, `UNSUPPORTED_DEVICE`, `SOURCE_NOT_EXPOSED`, `NOT_IMPLEMENTED`, `TEMPORARY_ERROR`, `UNVERIFIED`.

La schermata Fonti deve spiegare, per esempio, se le fasi del sonno mancano perché il gateway non le importa ancora oppure perché la Mi Band/companion non le scrive. Una singola etichetta «sincronizzato» non basta.

### 3.2 Matrice iniziale delle fonti

| Fonte | Flusso principale | Completezza richiesta |
|---|---|---|
| Bilancia Xiaomi | Xiaomi Home -> Xiaomi Cloud -> APK | Storico paginato disponibile, profilo corretto, nuovi record e correzioni, campi vendor non standard conservati senza perderli. |
| Mi Band | Companion realmente usata -> HC -> APK | Tutti i tipi condivisi e autorizzati; modello/app/firmware non presunti. |
| Google Fit / Google Health | App compatibile -> HC -> APK | Verificare integrazione attuale e direzione per tipo. Nessun nuovo SDK Fit legacy come dipendenza centrale. |
| Altre app Android | HC prima; connettore dedicato solo per lacune concrete | Non sommare copie dello stesso dato trasferito fra più app. |
| Inserimento Fitness Hub | Diario, allenamenti e misure -> Room | Dati utilizzabili offline, correzioni e cronologia, export HC opt-in. |
| Archivi locali | CSV/JSON e formati documentati -> import con anteprima | Migrazione e recupero, non requisito quotidiano. |
| AI | Foto/testo scelto -> bozza da confermare | Stima sempre riconoscibile; nessuna scrittura automatica non revisionata. |

Health Connect e Google Health sono prodotti distinti. L'annuncio Google del maggio 2026 descrive Google Health come evoluzione dell'app Fitbit e una migrazione degli utenti Fit: nomi e disponibilità devono essere verificati durante l'onboarding, non congelati nel codice. [S04, S05]

### 3.3 Famiglie di metriche

**Priorità immediata:** peso, percentuale grasso, composizione disponibile da Xiaomi, passi, distanza, energie, sonno con fasi, HR/HR riposo, SpO2, sessioni esercizio, alimentazione e idratazione.

**Estensione successiva:** altezza, massa magra/ossea ove supportate, BMR, frequenza respiratoria, HRV, temperatura, pressione, glicemia, VO2 max, velocità, cadenza, potenza, dislivello, intensità e altri tipi pertinenti del registro HC corrente. Leggere ogni tipo solo con una funzione concreta e consenso; non abilitarli tutti alla cieca. [S01]

**Campi Xiaomi:** oltre a peso e grasso conservare, quando restituiti, BMI, massa muscolare, acqua, massa ossea, proteine, grasso viscerale, metabolismo basale, età corporea, punteggi e misure segmentali. Un campo JSON chiamato `protein` non prova che sia grammi: verificare unità e significato. Non confondere massa muscolare con massa magra o percentuali con masse.

**Ulteriore salute a 360°:** note sul benessere, sintomi e misure manuali possono essere aggiunti come diario personale. Dati riproduttivi, referti o cartelle cliniche richiedono un'estensione esplicita con permessi, privacy e modello separati; non sono prerequisiti della prima versione utile e non vanno raccolti preventivamente.

## 4. Architettura e modello dati

### 4.1 Architettura obiettivo

```text
Mi Band -> companion -> Health Connect ----+                         +-> Health Connect
                                           |                         |   (export opt-in)
Xiaomi Home -> Xiaomi Cloud -> connector ---+-> ingestione -> Room ----+-> Dashboard / Corpo
                                           |              |          +-> Nutrizione / Training
Barcode / OCR / manuale / ricette ----------+              |          +-> Sonno / Attività
                                                          |          +-> Analytics / Report
Archivi di migrazione -------------------->+              +----------+-> Backup / export

Xiaomi, cataloghi e AI: rete solo per connettori esplicitamente abilitati.
Dati già archiviati: sempre consultabili senza rete.
```

Mantenere un modulo Gradle finché l'estrazione di moduli non risolve un problema misurato. Preferire modelli Kotlin testabili, repository, gateway, Use Case mirati e UI reattiva; niente framework nuovo per ogni dominio.

### 4.2 Schema concettuale da introdurre per passi

| Concetto | Campi/semantica minimi |
|---|---|
| `LocalProfile` | ID interno stabile; preferenze locali; nessuna identità personale pubblicata. Inizialmente un profilo attivo per installazione. |
| `SourceConnection` | Connettore, account referenziato senza segreti, regione, device/model, subject vendor, capacità, consenso e stato. |
| `SourceRecord` | Profilo, connessione, tipo, ID esterno, revisione/sourceModifiedAt, timestamp originali, payload sanitario selezionato e versione del parser. |
| `Observation` | Metrica, valore/unità originale, valore/unità normalizzata, tempo o intervallo, device, metodo di acquisizione, qualità e riferimenti al record. |
| `MetricDefinition` | Unità/dimensione, scala, semantica istantanea/intervallo, validatori, possibilità di aggregazione ed export. |
| `RecordLink` | Record appartenenti allo stesso evento, copia nota, candidato dubbio, decisione dell'utente, regola/versione applicata. |
| `ResolvedMetric` | Vista canonica per analytics con fonte scelta, motivazione, alternative e copertura; non modifica l'originale. |
| `SyncCheckpoint` | Connessione/tipo/scope, cursore, token, high-water mark, ultima pagina confermata, ultimo tentativo/esito. |
| `ExportOutbox` | Record locale/revisione, tipo HC, clientRecordId/version, ID HC, tentativi, errore, stato write/update/delete. |
| `QualityIssue` | Severità, regola, versione, record coinvolti, azione scelta, eventuale esclusione dai calcoli. |
| `UserCorrection` | Valore precedente e corretto, autore locale, data, motivo e regola di precedenza, senza falsificare la sorgente. |
| `GoalRevision` | Target, unità, periodo di validità; il cambiamento di obiettivo non riscrive il passato. |

Non convertire subito tutto in un generico database EAV. Aggiungere envelope e identità comuni, mantenendo tabelle tipizzate per i domini. Il dettaglio definitivo delle migrazioni deriva dallo schema v5 reale, con test.

### 4.3 Provenienza: separare concetti diversi

`IMPORTED` descrive il trasporto, non l'accuratezza. Un grasso corporeo calcolato da Xiaomi rimane una **stima vendor importata**, non diventa una misura clinicamente diretta. Un peso letto dalla bilancia e importato è una misura hardware importata. OCR è estrazione; AI fotografica è stima; inserimento manuale è il metodo di acquisizione. Servono campi separati, non una sola stringa globale che cambia il significato di tutta la giornata.

Registrare almeno `acquisitionMethod`, `measurementMethod`, `origin`, `algorithm/version` quando noto, `validationState` e `rawReference`. I valori Xiaomi devono rimanere quelli ricevuti, senza ricalcolarli tramite formule locali per farli sembrare identici.

### 4.4 Tempo, unità e conservazione

Conservare istante UTC, offset/zona della sorgente se disponibili, timestamp non convertito e assunzione applicata quando manca un offset. I bucket giornalieri sono viste ricalcolabili. Niente cambio retroattivo silenzioso delle date durante un viaggio. Usare `Clock`/`ZoneId` iniettati nei calcoli.

Percentuali 0–100, rapporti 0–1, kg/lb, g/mg, metri/km, kJ/kcal e tempo di metabolismo devono avere conversioni esplicite e test. BMR in kcal/giorno non si mappa a un tipo di potenza senza conversione verificata.

Conservare più misurazioni reali nello stesso giorno. Solo i grafici sintetici scelgono ultima misurazione, media o altro criterio dichiarato. Un filtro «30 giorni» deve rappresentare 30 giorni di calendario, non 30 righe sparse.

Il payload locale serve a preservare i campi sanitari consentiti, non cookie, token o intere risposte HTTP con credenziali. Estensioni vendor sconosciute vanno conservate entro limiti di dimensione e rese esportabili. Eventuali politiche di riduzione dei campioni devono essere visibili e autorizzate: «prende tutto» non può nascondere la perdita irreversibile dei dettagli.

## 5. Sincronizzazione e qualità

### 5.1 Pipeline proposta

```text
consenso/capacità -> fetch paginato -> staging -> identità persona
-> parsing/unità/tempi -> validazione -> deduplica e collegamenti
-> transazione record/checkpoint -> invalidazione viste -> outbox HC
-> stato UI e report locale dell'operazione
```

Il checkpoint avanza soltanto quando la pagina è stata salvata. Un crash fra commit dei record e aggiornamento di un token esterno deve al massimo far rileggere una pagina, non perderla. Trattare l'import come consegna almeno una volta con scritture idempotenti.

Non eseguire rete dentro una transazione lunga. Separare errori di autenticazione, rate limit, rete, schema e storage. Retry con backoff/jitter e limiti; CAPTCHA/2FA vanno completati dall'utente con il normale flusso Xiaomi, non aggirati. I fallimenti non devono apparire come «nessun dato».

Per HC progettare checkpoint per tipo o gruppo realmente inseparabile, preservare metadata.id e leggere anche cancellazioni. I permessi parziali non devono invalidare tutti i tipi. Gli ID client/revisioni dell'outbox e il riconoscimento delle proprie scritture evitano il rientro infinito degli export. [S02, S03]

### 5.2 Duplicati: quattro problemi differenti

1. **Stesso ID sorgente riscaricato:** upsert per identità e revisione; ricaricare non aggiunge un evento.
2. **Stesso dato trasferito da più app:** collegare le copie quando identità e provenienza lo dimostrano; usare fonte preferita nei calcoli senza cancellare gli originali.
3. **Valori simili senza prova:** candidati da mostrare o risolvere conservativamente; due pesate uguali a orari diversi rimangono due pesate.
4. **Aggregati sovrapposti:** non sommare totali giornalieri indipendenti. Per categorie coperte usare aggregazioni HC e priorità; non estendere automaticamente quella garanzia a tutte le metriche. [S06]

Chiavi e regole devono includere la persona. La selezione del nome in un singolo CSV non impedisce da sola di importare domani il CSV di un'altra persona nella stessa installazione.

### 5.3 Validazione, non manipolazione

**Errori strutturali:** date impossibili, unità sconosciuta, numeri non finiti, record senza identità sufficiente, intervalli invertiti o payload corrotti. Mettere in quarantena o rifiutare la scrittura normalizzata; riportare il problema senza inventare un valore.

**Anomalie plausibili:** variazioni forti di peso, HR insolito, energia inconsistente o sonno molto lungo. Conservare l'originale e indicare il dubbio. Le soglie devono essere configurabili/versionate, motivate e non spacciate per diagnosi o verità universali.

**Incoerenze cross-field:** grasso >100%, masse/componenti non compatibili, kcal/porzione che sembrano kcal/100g, somma fasi oltre la durata. Alcune sono errori matematici, altre dipendono dalle definizioni vendor: gestirle con severità distinta.

Nessun riempimento automatico dei buchi, nessuna interpolazione non etichettata e nessuna correzione silenziosa per ottenere un grafico bello. Media mobile, formula 1RM e stima AI sono viste/derivati separati.

## 6. Backlog eseguibile

Tutte le attività sotto sono **da implementare o verificare**, non dichiarazioni di completamento. Gli ID sono stabili; lo stato vive in `PROGRESS.md`, con `TODO` implicito per gli ID non ancora registrati. Ogni riga definisce un risultato osservabile e deve essere completata con test pertinenti. Le dipendenze a livello di traguardo sono nella sezione 7; i blocchi di sicurezza valgono anche quando una riga non li ripete.

### 6.1 SAFE — Protezione del dato e prerequisiti

| ID | Lavoro | Criterio di completamento |
|---|---|---|
| FH-SAFE-01 | Inventario reale dei rischi prima delle nuove connessioni: manifest, auto-backup, storage, logger e segreti. | Checklist collegata ai file, test/regole per log privi di dati; nessuna affermazione «solo locale» in conflitto con il comportamento effettivo. |
| FH-SAFE-02 | Impostare esplicitamente esclusioni backup OS/device transfer per token, chiavi e dati sanitari secondo le policy scelte. | Test su varianti Android e policy documentate; nessuna esportazione involontaria delle sessioni Xiaomi tramite backup di sistema. |
| FH-SAFE-03 | Definire canale test per sviluppo e canale con firma privata per uso sensibile. | Nessuna chiave privata nel repository; identità APK e migrazione spiegate; credenziali vere non richieste in build dimostrative pubblicamente firmabili. |
| FH-SAFE-04 | Errori e diagnostica sanitizzati, timeout, cancellazione coroutine e limiti input condivisi. | Eccezioni di rete/SQL non includono valori sanitari o token nell'UI/log; cancellazione non viene convertita in successo. |
| FH-SAFE-05 | Proteggere UI di password/token/backup: masking, niente clipboard automatica o salvataggio stato dei segreti. | Rotazione/background non espongono credenziali; scomparsa dei segreti quando il flusso termina; limiti JVM di cancellazione memoria documentati. |
| FH-SAFE-06 | Correggere istruzioni distruttive e indicazioni troppo ottimistiche. | Nessun «disinstalla prima» senza recupero verificato; label prototipo e limiti del backup presenti nel punto di utilizzo. |

### 6.2 DATA — Archivio canonico

| ID | Lavoro | Criterio di completamento |
|---|---|---|
| FH-DATA-01 | Identità locale e binding account/device/subject sorgente. | L'import di una seconda persona non si mescola alla prima; account con nomi uguali o rinominati distinguibili tramite ID. |
| FH-DATA-02 | Envelope record/revisione/provenienza per metrica. | Misura importata, stima vendor, stima locale e correzione manuale restano distinti in DB e dump. |
| FH-DATA-03 | Metriche tipizzate e archivio delle estensioni vendor. | Un nuovo campo sconosciuto non va perso né viene mappato a una metrica errata; unità sconosciute restano tali. |
| FH-DATA-04 | Migrazioni dalla versione installata e vincoli indice/relazioni. | Database preesistenti v1–v5 validati tramite Room; niente fallback distruttivo; dati e relazioni preservati. |
| FH-DATA-05 | Repository canonico condiviso da UI, analytics, export e obiettivi. | La stessa selezione di date/fonti produce gli stessi valori in Dashboard, Corpo, Insights e CSV. |
| FH-DATA-06 | Dettaglio persistente di campioni, intervalli e più misure giornaliere. | Lo storico non è ricostruito soltanto dai totali; un export conserva tempi originali e tutte le osservazioni previste dal contratto. |
| FH-DATA-07 | Correzioni, esclusioni, tombstone e provenienza della decisione. | Undo e ricalcolo sono possibili senza falsificare il record originale; una cancellazione confermata non riappare al sync successivo. |
| FH-DATA-08 | Indicizzazione e letture paginabili del database. | Intervalli lunghi non richiedono caricare tutto in RAM; nessun limite silenzioso a 500 pesate nei report. |

### 6.3 XIA — Xiaomi Cloud integrato nello stesso APK

Dipendenze: SAFE per connessioni reali; DATA per persistenza definitiva. La ricerca del protocollo e i parser con fixture possono iniziare subito senza credenziali.

| ID | Lavoro | Criterio di completamento |
|---|---|---|
| FH-XIA-01 | Adattatore di protocollo e parser Kotlin basati su upstream verificato: contratti, risposte CN/globali, errori e fixture. | Codice eseguibile e test, commit upstream e licenza annotati; nessuna autenticazione reale necessaria e nessun semplice placeholder. |
| FH-XIA-02 | Analisi autenticazione utilizzabile da Android e normale gestione dei challenge. | Scelta motivata fra flussi effettivamente supportati; nessun OAuth/QR inventato; UI distingue credenziali errate, verifica utente e protocollo cambiato. |
| FH-XIA-03 | Implementare trasporto HTTP Xiaomi isolato e read-only sulle misurazioni. | HTTPS verificato, endpoint/redirect autorizzati, risposta limitata, timeout/cancel, niente log di header/body privati e test di host non ammesso. |
| FH-XIA-04 | Session store protetto dal Keystore e disconnessione. | Password non persistita se evitabile; token salvati cifrati; logout annulla job/cancella sessione; invalidazione chiave richiede nuovo login senza cancellare lo storico. |
| FH-XIA-05 | Onboarding regione, modello, dispositivo e account effettivamente disponibili. | Regione non dedotta dalla posizione GPS; modello rilevato o confermato, stato non verificato per S400 Pro/varianti non testate. |
| FH-XIA-06 | Elenco/selezione subject e binding persistente. | Mostra solo profili accessibili all'account autorizzato; obbliga a scegliere il proprio; cambio account sospende il binding finché riconfermato. |
| FH-XIA-07 | Recupero storico paginato, riprendibile e misurabile. | Pagina duplicata, cursore fermo, crash, rete interrotta e storico lungo non producono loop o buchi nascosti; intervallo realmente recuperato visibile. |
| FH-XIA-08 | Preservare campi e unità del report Xiaomi. | Peso, composizione e campi estesi disponibili confrontabili con il report originale; nessuna sostituzione con formule openScale o BMI. |
| FH-XIA-09 | Sincronizzazioni successive con finestra sovrapposta e confronto revisioni. | Nuova pesata compare senza CSV; modifiche retroattive gestite nei limiti del protocollo; assenza da pagina incompleta non viene trattata come cancellazione. |
| FH-XIA-10 | UI completa Fonti -> Xiaomi -> stato -> storico e dettagli. | Collega, sincronizza, riprova, cambia intervallo e disconnetti sono azioni reali; errori distinguibili e ultimo successo visibile. |
| FH-XIA-11 | Scheduler Xiaomi rispettoso della batteria. | Manuale e apertura app funzionano anche se il job periodico viene ritardato; nessuna promessa di real-time; rate limit e login scaduto non causano richieste infinite. |
| FH-XIA-12 | Export HC delle misure Xiaomi tramite outbox comune. | Dati salvati prima localmente, export opt-in per tipi compatibili, round-trip senza duplicati e nessuna cancellazione su Xiaomi. |
| FH-XIA-13 | Prova reale del modello/account senza condividere segreti. | Login effettuato dall'utente nell'app, confronto di storico e nuova pesata sul suo dispositivo; protocollo non funzionante segnalato come blocco reale. |
| FH-XIA-14 | Migrazione dei CSV già importati e BLE facoltativo. | Vecchie pesate non spariscono; equivalenze CSV/cloud revisionate; duplicati probabili non cancellati automaticamente; BLE non acquisisce dati altrui senza selezione affidabile. |

**Regola fondamentale:** SmartScaleConnect è un riferimento MIT, non un programma da chiedere all'utente di installare. Si può adattare il protocollo in Kotlin o valutare una libreria integrata, ma il risultato operativo resta un solo APK. Conservare attribuzione/licenza per il codice riutilizzato. Non copiare componenti con licenze incompatibili e non usare le credenziali del maintainer in CI. [S07]

### 6.4 HC — Health Connect esteso e bidirezionale

| ID | Lavoro | Criterio di completamento |
|---|---|---|
| FH-HC-01 | Registro tipo -> feature disponibile -> scope read/write -> stato osservato. | Permessi richiesti soltanto se supportati/utilizzati; UI dice perché una metrica manca; nessun supporto promesso in base al solo Android SDK. |
| FH-HC-02 | Persistere ID HC, origine, versione e metadati dei record letti. | Cancellazione per solo ID e aggiornamento di un record che cambia data identificano i vecchi e nuovi dati da ricalcolare. |
| FH-HC-03 | Token per tipo/scope e checkpoint sicuri. | Revoca di un permesso non blocca gli altri; cambiare fonti/tipi invalida solo il cursore pertinente; crash non salta modifiche. |
| FH-HC-04 | Backfill configurabile oltre l'attuale finestra 365 giorni. | Intervallo richiesto e ottenuto distinti, paginazione/progresso/cancel; storia non accessibile documentata, senza aggirare permessi. |
| FH-HC-05 | Accesso foreground/background con capability e permesso dedicati. | Worker non presume accesso perché schedulato; manca permesso -> sync quando l'app è aperta, senza crash né retry infiniti. |
| FH-HC-06 | Sonno con sessioni, fasi, intervalli e metadati completi. | Fasi mantenute, sovrapposizioni e notti a cavallo di mezzanotte testate; durata sessione distinta da tempo effettivo di sonno e tempo a letto. |
| FH-HC-07 | Corpo esteso: altezza, massa magra/ossea, BMR e tipi pertinenti. | Mappatura documentata per unità e semantica; assenza di tipo HC non forza una metrica vendor in un campo non equivalente. |
| FH-HC-08 | Nutrizione e acqua in lettura con strategia per diario esterno. | Un totale importato non duplica i pasti già registrati; dati senza dettaglio alimento indicati come aggregati, non ricette inventate. |
| FH-HC-09 | Vitals e attività estesi dal registro corrente. | Implementazioni incrementali per HRV, respirazione, temperatura, pressione, glicemia, VO2, cadenza/potenza/velocità quando disponibili; test e permessi per ogni gruppo. |
| FH-HC-10 | Gestire insert/update/delete e record su intervalli multipli. | Correzioni di record distribuiti su più giorni aggiornano tutti i bucket coinvolti; non soltanto la data iniziale. |
| FH-HC-11 | Outbox locale per scritture autorizzate. | clientRecordId/version stabili, risposta ID salvata, retry idempotente, stato per record, revisione e revoca del consenso non perdono dati locali. |
| FH-HC-12 | Read-back, copie e cancellazioni dell'export. | I nostri record rientrati da HC sono riconosciuti; eliminazioni remote riguardano solo record scritti dall'app e autorizzati, mai dati di altre applicazioni. |
| FH-HC-13 | Diagnostica configurazione companion/Fit/Google Health. | Procedura coerente con app/versione presenti; mostra tipi osservati e ultima lettura; nessuna garanzia di copertura dell'intero archivio esterno. |
| FH-HC-14 | Test integrazione HC e controlli sul telefono. | Matrice permessi, tipo mancante, storia limitata, backend HC assente/aggiornabile e round-trip realmente esercitati oltre ai mock. |

### 6.5 REC — Riconciliazione e validazione comuni

| ID | Lavoro | Criterio di completamento |
|---|---|---|
| FH-REC-01 | Idempotenza per identità sorgente e revisione. | Reimport n volte non cambia il numero di eventi; revisione nuova aggiorna lo stesso evento conservando la storia necessaria. |
| FH-REC-02 | Collegamento copie Xiaomi -> HC e archivi precedenti. | Matching certo separato da probabilistico; ragioni e fonti consultabili; nessuna fusione basata sul solo valore o giorno. |
| FH-REC-03 | Politica di selezione per metrica. | Priorità configurabili e deterministiche, originali preservati, zero e dato mancante non confusi; correzione utente non ignorata. |
| FH-REC-04 | Deduplicazione activity/sleep e doppio conteggio energia. | Totali HC non sommati ai loro componenti; calorie attive non aggiunte di nuovo al totale; no somma di copie da tre app. |
| FH-REC-05 | Validazione schema, unità, percentuali e tempi. | Nessun NaN/Infinity o conversione implicita nel DB normalizzato; input ambiguo genera issue, non numero plausibile inventato. |
| FH-REC-06 | Rilevazione anomalie temporali/cross-field. | Regole versionate, soglie spiegate, record sospetti conservati; l'utente può confermare/escludere senza riscrivere la sorgente. |
| FH-REC-07 | Schermata Qualità dati e risoluzione conflitti. | Mostra anomalia, candidati duplicati, motivazione, preview effetti e undo; nessuna cancellazione massiva automatica. |
| FH-REC-08 | Ricalcolo consistente dopo correzione o cambio priorità. | Tutte le schermate/report usano la nuova vista; invalidazioni testate e risultati riproducibili a parità di input e versione regole. |

### 6.6 BODY — Corpo e bilancia utili davvero

| ID | Lavoro | Criterio di completamento |
|---|---|---|
| FH-BODY-01 | Cronologia canonica con tutte le misurazioni e dettagli vendor. | Filtri persona/fonte/periodo, dettaglio campi non standard, unità e provenienza; nessun limite nascosto ai record storici. |
| FH-BODY-02 | Ultimo valore per singola metrica, con propria data. | Il peso di ieri resta visibile come peso di ieri anche se oggi arrivano solo passi; non etichettarlo come misurato oggi. |
| FH-BODY-03 | Confronti e grafici di composizione. | Peso, grasso, massa e acqua con unità corrette; non confondere percentuale e kg o cambio algoritmo con cambiamento del corpo. |
| FH-BODY-04 | Misure manuali e correzioni locali. | Inserisci/modifica/esporta peso, circonferenze e altre misure supportate; origine manuale evidente e undo. |
| FH-BODY-05 | Formule opzionali con provenienza. | BMI usa altezza applicabile alla data; formule locali disattivabili, mai al posto dei valori Xiaomi importati. |
| FH-BODY-06 | Report corpo e controllo coerenza tra viste. | Stessi record/delta fra dettaglio, grafico, Dashboard e report; formula e criterio di aggregazione visibili. |

### 6.7 NUT — Alimentazione completa senza FatSecret obbligatorio

| ID | Lavoro | Criterio di completamento |
|---|---|---|
| FH-NUT-01 | Modello alimento con base 100g/100ml/porzione e quantità mangiata. | Conversioni e arrotondamenti testati; niente equivalenza ml=g senza densità; prodotto non perde base/unità. |
| FH-NUT-02 | Macronutrienti, grassi dettagliati, fibre, zuccheri, sodio/sale e micronutrienti. | Campi nullable, unità specifiche, definizioni regionali annotate; sale e sodio distinti senza doppi conteggi. |
| FH-NUT-03 | Barcode on-device e identità prodotto. | EAN/UPC validati, zeri iniziali preservati, scansioni ripetute non creano alimenti duplicati; barcode non trovato apre un flusso utilizzabile. |
| FH-NUT-04 | Catalogo Open Food Facts robusto e cache locale. | Consenso rete, User-Agent conforme, rate-limit/backoff, dati incompleti gestiti, attribuzione presente; niente fallback che inventa macro. |
| FH-NUT-05 | Fotografia etichetta nutrizionale e OCR. | Acquisizione/rotazione/ritaglio, più colonne e lingue ES/IT/EN; bozza confrontabile con immagine e conferma obbligatoria. |
| FH-NUT-06 | Ingredienti, allergeni dichiarati e conservazione immagini. | Estrazione ingredienti distinta dalla tabella; testo originale conservato; assenza di allergene nel testo non equivale a certificazione di sicurezza. |
| FH-NUT-07 | Apprendimento prodotto sconosciuto. | Barcode + label + conferma -> prodotto riutilizzabile offline al successivo scan; nessun reinserimento manuale ripetuto. |
| FH-NUT-08 | Diario pasti, ricerca, recenti, preferiti, quantità e modifica. | Giornata completa registrabile offline; snack/pasti personalizzati, modifica/cancellazione/undo e totale corretto. |
| FH-NUT-09 | Snapshot nutrienti e versioni degli alimenti. | Cambiare oggi l'etichetta di un prodotto non riscrive automaticamente ciò che era stato mangiato il mese precedente. |
| FH-NUT-10 | Ricette, resa cotta, porzioni e pasti riutilizzabili. | Ingredienti in unità miste, perdita/acquisto acqua e resa finale gestiti senza dedurre nutrienti dalla sola foto. |
| FH-NUT-11 | Acqua, obiettivi e copertura del diario. | Giorno non compilato non equivale a digiuno; totale parziale etichettato; target calorie/proteine e macro sono modificabili. |
| FH-NUT-12 | Migrazione da tracker/archivi disponibili e export HC. | Anteprima e mappatura, duplicati gestiti, unità e data preservate; nessuna API FatSecret o disponibilità export presunta. |

### 6.8 TRAIN — Allenamenti e progressione

| ID | Lavoro | Criterio di completamento |
|---|---|---|
| FH-TRAIN-01 | Modello esercizi e categorie estensibili. | Pesi, corpo libero, assistenza, tempo/distanza e custom non forzati in un unico schema «kg x reps». |
| FH-TRAIN-02 | Sessione offline persistente. | Avvio, serie, warmup, note, RPE/RIR, pause e chiusura sopravvivono a rotazione/process death senza perdere l'allenamento. |
| FH-TRAIN-03 | Template e routine riutilizzabili. | Ordine esercizi, target, recuperi e copia ultimo allenamento; modificare template non altera le sessioni passate. |
| FH-TRAIN-04 | Modifica/cancellazione di sessioni concluse. | Ricalcolo PR/volume e invalidazione export; undo e collegamenti mantenuti. |
| FH-TRAIN-05 | Semantica carico per powerlifting e calisthenics. | Peso corporeo/zavorra/assistenza distinti; volume totale non somma grandezze incompatibili o duplicati. |
| FH-TRAIN-06 | Cardio e sessioni importate da wearable. | Durata, distanza e metriche disponibili; collegamento della sessione locale a quella del wearable senza contare due allenamenti. |
| FH-TRAIN-07 | PR, stime 1RM e progressione. | Formule nominate e dominio valido, warmup esclusi dove corretto, PR realmente misurato distinto da 1RM stimato. |
| FH-TRAIN-08 | Export HC e report allenamenti. | Sessione pubblicata una volta, revisioni gestite; dettagli non supportati restano nel DB e nel report locale. |

### 6.9 LIFE — Attività, sonno, vitali e diario personale

| ID | Lavoro | Criterio di completamento |
|---|---|---|
| FH-LIFE-01 | Attività giornaliera/settimanale con provenienza. | Passi, distanza, energie e allenamenti reconciliati; date, fonte e stato parziale visibili. |
| FH-LIFE-02 | Sonno con fasi, pisolini e turni. | Non presume notte 00–08; intervalli sovrapposti e fasi ignote trattati esplicitamente; giorni di calendario coerenti. |
| FH-LIFE-03 | Serie HR/SpO2 e vitali supportati. | Dettaglio campioni, copertura e statistiche con algoritmo dichiarato; HR medio non viene chiamato HR a riposo. |
| FH-LIFE-04 | Nuovi tipi dal registro capacità. | Widget/storia/export introdotti per ogni gruppo verificato, senza dati casuali per riempire le schermate vuote. |
| FH-LIFE-05 | Diario note, recupero e benessere soggettivo. | Input volontario, scala definita, edit/export e privacy; nessuna deduzione clinica dalle note. |
| FH-LIFE-06 | Estensione salute avanzata separata. | Prima di referti/dati riproduttivi: proposta di scope, permessi e trattamento privato approvati; nessuna raccolta preventiva. |

### 6.10 GOAL — Obiettivi e follow-up

| ID | Lavoro | Criterio di completamento |
|---|---|---|
| FH-GOAL-01 | Profilo e obiettivi configurabili, non hard-coded. | L'utente sceglie mantenimento/dimagrimento/performance e target senza pubblicare peso o altri dati nel codice. |
| FH-GOAL-02 | Storia dei target. | Un cambio calorie/peso/proteine/data obiettivo vale dal momento scelto e non altera l'aderenza storica. |
| FH-GOAL-03 | Monitoraggio di calorie, macro, passi, sonno e frequenza training. | Copertura e giornate parziali distinte; nessuna penalizzazione calcolata su dati assenti. |
| FH-GOAL-04 | Andamento peso/forza e proiezioni facoltative. | Proiezione con intervallo/assunzioni e sufficienza dati, non data certa di raggiungimento né consiglio medico automatico. |
| FH-GOAL-05 | Promemoria locali opt-in. | Frequenza configurabile, permessi Android gestiti, disattivazione semplice; nessuna notifica remota o calendario esterno obbligatorio. |

### 6.11 ANA — Analytics deterministiche

| ID | Lavoro | Criterio di completamento |
|---|---|---|
| FH-ANA-01 | Un motore analitico sopra i repository canonici. | Nessuna schermata calcola una variante diversa dello stesso indicatore; test su dataset fissati. |
| FH-ANA-02 | Range 7/14/30/90 giorni e periodi personalizzati reali. | Missing days non compressi; medie mobili in giorni, non in numero di misure sparse. |
| FH-ANA-03 | Trend grezzi, medie mobili e delta. | Grezzo esportabile separatamente dal trend; delta con segno singolo e baseline spiegata. |
| FH-ANA-04 | Introito, dispendio e andamento corporeo. | Totale vs attivo/BMR distinti; stima TDEE solo con dati sufficienti e assunzioni dichiarate; nessuna calibrazione forzata per far tornare le calorie. |
| FH-ANA-05 | Sonno, allenamenti e progressione. | Metriche training confrontabili, ritardi temporali espliciti, sample size e buchi visibili. |
| FH-ANA-06 | Correlazioni con integrità statistica. | Variabili costanti/pochi campioni non generano correlazioni false; niente headline «si muovono insieme» quando non è dimostrato; associazione non causalità. |
| FH-ANA-07 | Dashboard configurabile e approfondimento. | Ogni card conduce a dati/fonti/periodo/metodo; preferenze locali e dato più recente datato. |
| FH-ANA-08 | Riproducibilità e performance. | Versione regole nei report, stessi input stessi output; range estesi gestiti senza stalli della UI. |

### 6.12 PORT — Backup, esportazione e report

| ID | Lavoro | Criterio di completamento |
|---|---|---|
| FH-PORT-01 | Inventario copertura backup e manifest versionato. | Tabelle, relazioni, preferenze e allegati elencati; segreti esclusi; backup parziale chiamato parziale. |
| FH-PORT-02 | Snapshot coerente del DB durante backup. | Scritture concorrenti non producono archivi con pasti/serie orfani; versione schema e conteggi verificabili. |
| FH-PORT-03 | Backup completo cifrato con passphrase. | Tutti i record sanitari previsti inclusi, chiavi/sessioni esclusi per default; test di password errata, modifica e troncamento. |
| FH-PORT-04 | Restore validato e transazionale. | Validazione prima di modificare DB; fallimento non lascia metà archivio importato; foreign key, ID e record custom ripristinati. |
| FH-PORT-05 | Modalità merge/replace e migrazione fra versioni. | Anteprima effetti e conferma per replace; reimport senza duplicazioni; incompatibilità versione non ignorata silenziosamente. |
| FH-PORT-06 | File picker, limiti archivio e sicurezza import. | I/O fuori main thread, limiti streaming, niente zip traversal/bomb; cancel/errori visibili e nessun file privato su clipboard automatica. |
| FH-PORT-07 | Export JSON/CSV grezzo e normalizzato. | Provenienza, tempi, unità, correzioni e campi vendor disponibili; CSV impedisce formule malevole nei campi testuali per spreadsheet. |
| FH-PORT-08 | Report giornaliero/settimanale/mensile PDF/CSV/JSON. | Filtri e assunzioni indicati; report leggibile e rigenerabile dai dati; anteprima prima di condividere informazioni private. |
| FH-PORT-09 | Trasferimento telefono e round-trip indipendente. | Backup -> installazione pulita -> restore -> stessi dati/relazioni/report; login esterni rifatti consapevolmente, nessun segreto trasferito per caso. |

### 6.13 UX — Uso dal telefono, lingue e accessibilità

| ID | Lavoro | Criterio di completamento |
|---|---|---|
| FH-UX-01 | Onboarding locale con hardware e fonti. | Si può saltare ogni connettore; funzionamento offline immediato; versione Android/modello rilevati senza root e senza telemetria. |
| FH-UX-02 | Centro Fonti e sincronizzazioni. | Account/subject scelto, permessi, capacità, storico ottenuto, ultimo successo/errore, retry e disconnect visibili nello stesso posto. |
| FH-UX-03 | Navigazione compatta. | Diario, workout, salute e impostazioni raggiungibili senza barra sovraffollata; ritorno, stato e rotazione corretti. |
| FH-UX-04 | Stati uniformi e azioni funzionanti. | Loading, vuoto, permesso, non supportato, errore e dati parziali distinti; ogni bottone porta a un compito concluso. |
| FH-UX-05 | Spagnolo, italiano e inglese. | Stringhe/valori/formati localizzati, accenti senza mojibake, decimali locali e date non ambigue. |
| FH-UX-06 | Accessibilità, temi e schermi piccoli. | TalkBack, font grandi, contrasto, dark/light e tastiera non nascondono azioni; grafici hanno equivalente testuale. |
| FH-UX-07 | Prestazioni e protezione del contesto. | Nessuna lettura di file o parsing voluminoso nel callback UI; form critici resistono a process death senza salvare segreti. |

### 6.14 AI — Aiuto opzionale, non infrastruttura obbligatoria

| ID | Lavoro | Criterio di completamento |
|---|---|---|
| FH-AI-01 | Astrazione provider con policy e consenso. | API key per utente protetta, provider/modello selezionabile, nessuna sessione consumer o key del maintainer incorporata. |
| FH-AI-02 | Etichetta OCR -> bozza strutturata facoltativa. | Output JSON validato, separazione 100g/porzione e unità, campi incerti evidenziati; conferma prima del salvataggio. |
| FH-AI-03 | Foto del pasto -> proposta alimenti/quantità. | Dichiarare incertezza porzioni e ingredienti nascosti; chiedere conferma quantità; niente presentazione come misura esatta. |
| FH-AI-04 | Inserimento naturale di pasti e allenamenti. | Parsing/lookup deterministico quando possibile; risultato editabile; crudo/cotto e unità non presunti. |
| FH-AI-05 | Domande sul diario e riassunti. | Solo contesto minimo selezionato, riferimenti ai dati locali, niente istruzioni eseguite da etichette/payload e nessuna diagnosi. |
| FH-AI-06 | Budget, privacy dei free tier e offline fallback. | Limite richieste/spesa, nessun failover a pagamento automatico, termini di training/retention verificati; disabilitare AI lascia tutto il core usabile. |

Non promettere che un account Google AI Pro o ChatGPT consumer fornisca le chiamate API richieste dall'app. Verificare il provider scelto prima dell'implementazione. Il costo zero di un free tier non significa automaticamente trattamento adatto ai dati sanitari. [S10]

### 6.15 QA — Verifica tecnica e sul dispositivo

| ID | Lavoro | Criterio di completamento |
|---|---|---|
| FH-QA-01 | Suite unit/contract per logica reale. | Parser, conversioni, sync, qualità, ricette e statistiche con fixture; nessun test che prova solo `average()` della libreria. |
| FH-QA-02 | Test Room di migrazione, transazione e concorrenza. | Eseguiti contro SQLite/Room reale; rollback, indici, relazioni e conflitti non simulati soltanto con Mockito. |
| FH-QA-03 | Corpus casi avversi su dati e archivi. | BOM, unità, NaN, date/DST, offset, righe troncate, pagine ripetute, owner duplicati/ignoti e CSV con formule. |
| FH-QA-04 | Test UI end-to-end dei flussi principali. | Primo avvio, permessi parziali, diario, workout, import, export, errori e restore con contenuti sintetici. |
| FH-QA-05 | Matrice Android e telefono reale. | Almeno API minima e API recenti supportate più dispositivo target; modalità offline, risparmio batteria, revoche e aggiornamento documentati. |
| FH-QA-06 | Test hardware/cloud autorizzati. | Login Xiaomi e dati reali rimangono sul telefono; evidenza minima non sensibile per confronto valori, profili, regione e sync. |
| FH-QA-07 | Performance, batteria e storage. | Benchmark ripetibile su storico lungo e campioni densi; nessun polling aggressivo o caricamento intero senza limiti. |
| FH-QA-08 | Revisione sicurezza e dipendenze. | Threat model, secret scanning, supply-chain, manifest esportato, rete/redirect, import ostili e privacy dei log verificati. |

### 6.16 DIST — CI, APK e distribuzione

| ID | Lavoro | Criterio di completamento |
|---|---|---|
| FH-DIST-01 | Una modifica coerente -> una pipeline utile. | Commit multi-file atomici; concorrenza cancella build obsolete; nessuna raffica di commit intermedi non compilabili. |
| FH-DIST-02 | Separare documentazione da build Android quando sicuro. | Check doc dedicati e trigger verificati; modifiche Gradle/manifest/codice/workflow attivano sempre test/lint/build. |
| FH-DIST-03 | Versione APK, commit, firma e checksum affidabili. | Metadati estratti dall'APK/build, versionCode crescente quando serve, download corrispondente al commit verificato; nessun test count inventato. |
| FH-DIST-04 | Pubblicazione race-safe e release recuperabili. | Build vecchia non sostituisce quella nuova; interruzione upload non lascia canale senza APK; release stabili/versionate non sovrascritte. |
| FH-DIST-05 | Privilegi minimi dei job e segreti protetti. | Build non necessita write token; pubblicazione separata/limitata; PR non autorizzate non ricevono chiavi private. |
| FH-DIST-06 | Peso APK e budget infrastruttura. | Analisi delle librerie/ABI prima di ottimizzare, core OCR offline preservato; retention/caching controllati, nessun runner a pagamento aggiunto. |
| FH-DIST-07 | Percorso di release privata e pubblica. | Scelta APK/AAB e canali, policy Health Connect/Play, upgrade e firma verificati; nessuna promessa di accettazione negli store. |

### 6.17 LEGAL — Licenza, contributi e sostenibilità

| ID | Lavoro | Criterio di completamento |
|---|---|---|
| FH-LEGAL-01 | Formalizzare il perimetro della licenza desiderata. | Uso personale privato gratuito; uso commerciale/licenze separati; casi aziendali interni, nonprofit, redistribuzione e consulenza chiariti con il proprietario. |
| FH-LEGAL-02 | Scegliere e applicare il testo legale dopo revisione. | Non inventare clausole spacciandole per standard; PolyForm candidato verificato rispetto agli obiettivi, niente conversione commerciale automatica non voluta. |
| FH-LEGAL-03 | Dipendenze, upstream e dati con licenze proprie. | Attribuzione MIT SmartScaleConnect mantenuta; ODbL/immagini OFF e componenti OCR valutati separatamente; il progetto non rivendica esclusività sull'upstream permissivo. |
| FH-LEGAL-04 | Contributi e diritti per eventuale dual licensing. | CONTRIBUTING e accordo adeguato scelti; non assumere che un DCO equivalga automaticamente a un permesso di relicensing. |
| FH-LEGAL-05 | Sponsor, donazioni e sviluppo commissionato. | Canali/paesi/pagamenti verificati prima dell'attivazione; roadmap sostenibile senza promettere ricavi o vendere dati sanitari. |
| FH-LEGAL-06 | Policy privacy e disclosure. | Connettori cloud opzionali descritti chiaramente, dati inviati e cancellabili, limiti del software e consensi coerenti con app e distribuzione. |

La direzione è **source-available**, non OSI open source se l'uso commerciale viene limitato. PolyForm Noncommercial non equivale automaticamente a «qualsiasi soggetto aziendale deve pagare»: il testo include permessi specifici che vanno confrontati con la decisione del proprietario. Nessuna scelta legale viene resa effettiva da questo piano. [S08, S09]

## 7. Traguardi e dipendenze

Nessuna data di consegna inventata. Avanzamento misurato dai percorsi funzionanti, non da una percentuale di task.

| Traguardo | Risultato per l'utente | Dipendenze principali e gate |
|---|---|---|
| T0 — Base verificabile | Installo la build giusta, so cosa salva e cosa non è ancora sicuro. | SAFE e verifica baseline; conservare CI verde. |
| T1 — Xiaomi senza CSV | Collego Xiaomi nell'app, scelgo il mio profilo e vedo storico + nuova pesata. | XIA-01..11/13, DATA-01..04/06, REC-01/05, UX-02; gate sicurezza prima di credenziali reali. |
| T2 — Un solo dato coerente | Corpo, Dashboard e Insights concordano; vedo copie e anomalie. | DATA-05/07/08, REC, BODY; non richiede nuovi dispositivi. |
| T3 — HC completo per il setup | Importo tutti i tipi realmente disponibili e condivido quelli compatibili senza loop. | HC, XIA-12/14, outbox, QA round-trip; nessun export automatico prima del consenso. |
| T4 — Diario alimentare completo | Imparo un prodotto sconosciuto e registro tutta la giornata offline. | NUT, UX camera/lingue, export HC successivo quando pronto. |
| T5 — Training completo | Registro, modifico e riuso una sessione pesi/corpo libero/cardio. | TRAIN e collegamenti sessioni importate; non serve AI. |
| T6 — Follow-up e report | Obiettivi, statistiche e report usano le stesse serie e dichiarano copertura. | GOAL, LIFE, ANA e PORT export. |
| T7 — Recupero affidabile | Sposto l'archivio su un altro telefono senza perdere dati/relazioni. | PORT e QA Room/upgrade; primo incremento di backup parte già in T0/T1. |
| T8 — AI facoltativa | Foto/testo diventano bozze verificabili, non misure inventate. | NUT/TRAIN deterministici, AI, SAFE/LEGAL privacy. |
| T9 — Rilascio responsabile | Canale firmato, documentazione vera, licenza scelta e permessi conformi. | DIST, LEGAL, QA, accettazione telefono; blocca l'etichetta production-ready, non lo sviluppo locale. |

### Ordine operativo iniziale

1. Rileggere stato/CI; correggere subito eventuali regressioni o rischio di perdita dati.
2. `FH-XIA-01`: implementare i contratti/parser Xiaomi e test senza login reale. In parallelo solo analisi dei gate SAFE, senza moltiplicare i task attivi.
3. `FH-DATA-01` e `FH-DATA-02`: legare record a persona/connessione e distinguere origine/metodo; aggiungere migrazioni testate.
4. `FH-SAFE-01..05`, `FH-XIA-02..04`: completare i prerequisiti di autenticazione sicura prima di coinvolgere credenziali vere.
5. `FH-XIA-05..10`: profilo, storico paginato, salvataggio e UI; poi schedulazione e test dispositivo.
6. `FH-REC`/`FH-BODY` e `FH-HC` procedono per vertical slice, facendo arrivare lo stesso dato a UI, export e test.
7. Nutrizione, training e report si completano sui repository stabilizzati. Non riscrivere da zero le feature esistenti.

Se il login Xiaomi è bloccato da una verifica reale, si lavora su DATA, HC, PORT e test indipendenti. Non si torna al CSV come scelta principale senza nuova decisione del proprietario. Un piano ampio non autorizza a lasciare decine di implementazioni iniziate e inutilizzabili.

## 8. Scenari di accettazione

Ogni scenario deve essere collegato a test automatici o a un verbale dispositivo esplicito. Dove necessario usare dataset sintetici; nessuna fixture contiene account o misure personali.

| Scenario | Esito richiesto |
|---|---|
| ACC-01 — Primo avvio senza permessi/rete | Diario manuale e storico locale utilizzabili; setup connettori rinviabile. |
| ACC-02 — Xiaomi, sola persona A | Selezione stabile del subject; storico disponibile conservato e fonte leggibile. |
| ACC-03 — Bilancia condivisa A/B | Nessuna misura B entra in A; nomi uguali/rinominati non aggirano il binding. |
| ACC-04 — Ripetere sync/import 10 volte | Stessi eventi e analytics; eventuali aggiornamenti riconosciuti come revisioni. |
| ACC-05 — Nuova pesata Xiaomi | Compare in app dopo sync senza file intermedi, con timestamp e valori vendor. |
| ACC-06 — Record corretto o spostato di giorno | Vecchia e nuova data ricalcolate; nessuna copia residua o perdita dei dati originali. |
| ACC-07 — Rete interrotta a pagina N | Ripartenza senza saltare N e senza duplicare le pagine già confermate. |
| ACC-08 — Login scaduto/CAPTCHA/2FA | Avviso e normale intervento utente, niente loop o aggiramento del challenge. |
| ACC-09 — Peso Xiaomi e sua copia HC | Un solo evento canonico, due origini collegate; due vere pesate uguali rimangono due. |
| ACC-10 — Passi copiati tra tre app | Nessuna somma tripla; priorità HC/per-metrica dichiarate. |
| ACC-11 — Export HC e rilettura | Nessun nuovo record locale falso e nessuna seconda scrittura duplicata. |
| ACC-12 — Permesso sonno revocato | Gli altri tipi continuano; storia locale gestita secondo policy, non cancellata di nascosto. |
| ACC-13 — Sonno diurno, pisolino, DST, viaggio | Tempi/fasi/sessioni e giorni attribuiti correttamente, senza durata negativa. |
| ACC-14 — Zero contro mancante | Zero misurato visibile come zero; dato non letto non partecipa alle medie come zero. |
| ACC-15 — HR/percentuale sospetta | Originale conservato, issue visibile; nessuna correzione automatica a un valore medio. |
| ACC-16 — Prodotto paraguaiano sconosciuto | Barcode + etichetta/ingredienti + revisione -> riuso offline, nutrienti nella base corretta. |
| ACC-17 — Ricetta e alimento aggiornati | I pasti passati mantengono snapshot; nuovi pasti usano nuova versione consapevolmente. |
| ACC-18 — Allenamento interrotto/duplicato wearable | Sessione recuperata senza perdita; eventuale copia importata collegata, non conteggiata due volte. |
| ACC-19 — Backup multi-dominio | Restore su DB pulito conserva cibo/entry, esercizi/serie/template, composizione, campioni e relazioni. |
| ACC-20 — Backup errato/malformato/oversize | Nessuna scrittura parziale, blocco UI o esposizione di dati nei messaggi. |
| ACC-21 — Stesso report in quattro viste | Numeri coerenti e stesse unità/fonti/date tra Dashboard, grafico, CSV e PDF. |
| ACC-22 — App aggiornata | Dati conservati, versione/firma corrette; incompatibilità diagnosticata senza suggerire cancellazioni cieche. |
| ACC-23 — AI disattivata o quota finita | Core invariato; nessuna chiamata remota o fatturazione automatica di fallback. |
| ACC-24 — Log e file pubblici | Nessun account, token, seriale identificativo o dato sanitario reale in repository, report CI o release. |

### Gate minimi per considerare finita una funzionalità

- Percorso UI completo dall'input all'effetto persistente e a un'uscita utilizzabile.
- Test pertinenti passati; test mock distinti da prove Room/dispositivo.
- Permessi parziali, errori, cancel, unità, identità e dati mancanti gestiti.
- Migrazione/backup aggiornati se cambia lo schema o un dominio persistito.
- Documentazione e capability registry coerenti; nulla venduto come supportato se non implementato.
- Build/lint/test del commit finale verificati, artifact/release controllati quando previsti.
- Per funzionalità hardware/cloud: stato `AWAITING_DEVICE` finché non esiste la prova reale.

## 9. Metodo di lavoro continuativo

### 9.1 Stato e memoria nel repository

`MASTER_PLAN.md` contiene obiettivi, contratti e backlog. `PROGRESS.md` contiene il cursore operativo, attività in corso, blocchi, risultati e prove. `ROADMAP.md` è l'indice dei traguardi. Non mantenere tre liste di stato concorrenti o copiare centinaia di checkbox in issue senza necessità.

Stati ammessi: `TODO`, `IN_PROGRESS`, `BLOCKED`, `IMPLEMENTED`, `AWAITING_DEVICE`, `VERIFIED`, `DEFERRED`. `VERIFIED` significa verificato rispetto ai criteri e al livello di test dichiarati, non genericamente production-ready. Un task del master senza riga nel registro è `TODO`.

### 9.2 Apertura di ogni sessione

1. Leggere dal repository `AGENTS.md`, piano, progressi, README e documenti pertinenti.
2. Verificare HEAD reale, modifiche recenti, issue/PR aperte e CI. Non usare ricordi o descrizioni dei commit come prova di funzionamento.
3. Riprendere prima un task incompleto o una regressione, poi il primo task pronto indicato dal cursore.
4. Fissare un risultato piccolo ma completo per la sessione. Non aprire più di due task correlati senza ragione.
5. Non chiedere di nuovo «posso fare Phase 3?» quando il lavoro rientra nel piano approvato. Chiedere solo decisioni davvero non risolvibili o autorizzazioni sensibili.

### 9.3 Implementazione e chiusura

- Ispezionare il codice coinvolto, verificare fonti ufficiali/upstream attuali e riutilizzare ciò che funziona.
- Modificare e testare un flusso completo; aggiornare schema, migrazioni, export e policy dove necessario.
- Usare commit multi-file atomici e rileggere HEAD prima di aggiornare `main`; nessun force-push.
- Non cancellare test, disabilitare lint o aggiungere return finti per ottenere CI verde.
- Eseguire verifiche locali disponibili e CI; se l'ambiente non permette un test dichiararlo, non inventarne l'esito.
- Aggiornare `PROGRESS.md` con ID, file/commit, test/comandi, URL run quando noto, limitazioni, prossimo passo e motivo degli eventuali blocchi.
- Distinguere «codice scritto», «compila», «test automatici superati», «provato sul telefono» e «rilasciato».
- Se non si può completare, lasciare il progetto coerente e indicare precisamente il pezzo restante. Non marcare il task VERIFIED.

### 9.4 Regola di autorizzazione

Le sessioni avviate dall'utente possono proseguire le attività qui previste e scrivere su `main`. Il piano non è un'automazione schedulata: nessun lavoro viene promesso dopo la fine della sessione. Per esecuzioni periodiche o notifiche future serve una richiesta distinta.

Richiedono conferma specifica: chiavi/password/account reali, pagamento/runner a costo, scelta finale della licenza, caricamento di dati sanitari, sostituzione distruttiva dell'archivio, modifica della firma/app ID con conseguenze sugli aggiornamenti, nuovo cloud obbligatorio e pubblicazione negli store. Connettore Xiaomi read-only e reti opzionali sono già nella direzione approvata.

## 10. Rischi e decisioni ancora aperte

| Rischio/decisione | Trattamento e cosa non blocca |
|---|---|
| Variante esatta S400/S400 Pro, modello band e companion non confermati | Rilevare/mostrare nell'onboarding; sviluppo dei contratti continua con supporto dichiarato e fixture, senza fingere compatibilità. |
| Regione Xiaomi non nota | Selezione della regione dell'account, non inferenza dalla localizzazione; test locali del client non richiedono tale dato. |
| Endpoint o login Xiaomi cambiati | Adattatore isolato, schema/errori espliciti, test contract e retry limitato; mai accesso all'account fuori dal flusso autorizzato. |
| Storico non interamente restituito dal vendor | Mostrare copertura, ultimo cursore e limiti; non chiamare «completo» un import troncato. |
| Dati HC non scritti dalla companion | Distinguere limite della fonte da limite nostro; eventuale connettore aggiuntivo solo se motivato e verificato. |
| Firma di test pubblica già distribuita | Non autentica l'autore dell'APK; preparare canale privato e migrazione controllata senza cancellare dati. |
| Backup incompleto attuale | Gate PORT; avviso esplicito finché non passa il round-trip completo. |
| Scelta crittografia dell'intero DB | Threat model e gestione chiavi prima di aggiungere dipendenze: distinguere sandbox/encryption-at-rest OS, DB cifrato, segreti Keystore e backup portabile. |
| Finestre e qualità dei dati | Nessuna diagnosi o modifica automatica; regole trasparenti e reversibili. |
| Licenza personale/commerciale non finalizzata | Nessuna nuova concessione introdotta dal piano; implementazione può proseguire, commercializzazione e contributi richiedono policy coerente. |
| Provider AI/condizioni free tier | Opt-in e verifica termini; non inviare dati sanitari a servizi inadatti solo perché gratuiti. |
| Crescita del progetto | Privilegiare traguardi end-to-end; niente date/promesse di copertura totale o nuove architetture non necessarie. |

Non pubblicare nome dei soggetti sanitari, peso, dieta reale, email, identificativi Xiaomi, token, device ID personali o screenshot privati nel piano. Usare persone sintetiche A/B per test e documentazione.

## 11. Fonti e verifiche

Le fonti sono state consultate il 6 ottobre 2026. Le regole di piattaforma cambiano: verificare nuovamente prima di implementare o aggiornare una dipendenza. Le proposte progettuali di questo piano non sono garanzie dei fornitori.

- **S01 — Tipi Health Connect:** https://developer.android.com/health-and-fitness/health-connect/data-types
- **S02 — Sincronizzazione, ID e token HC:** https://developer.android.com/health-and-fitness/health-connect/sync-data
- **S03 — Scrittura HC:** https://developer.android.com/health-and-fitness/health-connect/write-data
- **S04 — Integrazione Google Fit/HC:** https://support.google.com/fit/answer/12830119
- **S05 — Google Health e transizione app:** https://blog.google/products-and-platforms/products/google-health/google-health-fitbit/
- **S06 — Aggregazioni e limiti storico:** https://developer.android.com/health-and-fitness/health-connect/aggregate-data
- **S07 — SmartScaleConnect:** https://github.com/AlexxIT/SmartScaleConnect ; [licenza MIT](https://github.com/AlexxIT/SmartScaleConnect/blob/master/LICENSE), [client Xiaomi](https://github.com/AlexxIT/SmartScaleConnect/blob/master/pkg/xiaomi/client.go), [autenticazione](https://github.com/AlexxIT/SmartScaleConnect/blob/master/pkg/xiaomi/auth.go). L'upstream documenta S400 EU e altre varianti specifiche, non ogni prodotto con nome simile.
- **S08 — Candidato licenza:** https://polyformproject.org/licenses/noncommercial/1.0.0
- **S09 — Definizione OSI:** https://opensource.org/osd
- **S10 — Esempio policy/costi API AI:** https://ai.google.dev/gemini-api/docs/pricing ; ricontrollare anche termini, retention e disponibilità del provider scelto.
- **S11 — Open Food Facts, API e licenze dati:** https://openfoodfacts.github.io/openfoodfacts-server/api/
- **S12 — Keystore:** https://developer.android.com/privacy-and-security/keystore
- **S13 — Backup Android:** https://developer.android.com/identity/data/autobackup
- **S14 — Permessi HC:** https://developer.android.com/reference/kotlin/androidx/health/connect/client/permission/HealthPermission
- **Baseline verificata:** https://github.com/HebaDenys/fitness-hub/tree/addbc7dd003b691dabcbe2d671dd646c2f73930b
- **CI baseline:** https://github.com/HebaDenys/fitness-hub/actions/runs/37499457080

### Criterio finale

Il progetto raggiunge il suo primo obiettivo reale quando una persona può mantenere il proprio hardware, usare Xiaomi Home normalmente, vedere in Fitness Hub i dati disponibili della bilancia e della band, registrare pasti e allenamenti, ottenere statistiche coerenti, esportare e recuperare il proprio archivio e condividere i tipi compatibili con HC senza duplicati o perdita di dati. Tutto questo senza root, backend obbligatorio, pubblicità o abbonamento necessario al core.
