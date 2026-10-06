# Fitness Hub — Istruzioni per le sessioni di sviluppo

## Fonti di verità

Leggere dal repository reale, all'inizio di ogni sessione:

1. `docs/MASTER_PLAN.md`: obiettivo, decisioni approvate, contratti e backlog con ID stabili.
2. `docs/PROGRESS.md`: cursore operativo, lavoro in corso, blocchi e prove di completamento.
3. `README.md`, `docs/ROADMAP.md`, `docs/ARCHITECTURE.md` e documentazione della parte coinvolta.
4. Codice, test, migrazioni, HEAD, modifiche recenti, issue/PR e CI effettivi.

Non ricostruire lo stato dai ricordi o dalle descrizioni dei commit. Il piano è una specifica; la presenza di una voce nel piano non dimostra l'esistenza della funzione.

## Decisione prioritaria aggiornata

Il proprietario ha approvato questa direzione:

`S400 -> Xiaomi Home -> servizi Xiaomi -> connettore integrato in Fitness Hub -> Room -> UI/analytics -> Health Connect opt-in`.

Il connettore Xiaomi diretto deve stare nello stesso APK. Non richiedere root, script, CLI SmartScaleConnect, container, server o CSV manuali per l'uso normale. SmartScaleConnect può essere un riferimento di interoperabilità/codice MIT, con attribuzione e verifica dell'upstream. CSV e BLE esistenti restano ripieghi e non vanno rimossi distruggendo dati.

La precedente preferenza per CSV storico + BLE live è superata. Non rifiutare il connettore Xiaomi perché «local-first vieta il cloud»: non c'è un nostro backend obbligatorio; una sorgente vendor è una connessione facoltativa esplicita.

## Vincoli non negoziabili

- Kotlin + Compose + Hilt + Room + Health Connect; niente rifacimento architetturale immotivato o esplosione di moduli Gradle.
- Un archivio locale proprio, consultabile offline. Nessun account Fitness Hub obbligatorio, pubblicità, tracking o backend obbligatorio.
- Nessun dato inventato. Mancante, zero misurato, permesso negato e tipo non supportato sono distinti.
- Provenienza per metrica: import è trasporto, non accuratezza; una stima vendor rimane una stima vendor. Originali e correzioni separati.
- ID, revisioni, tempi/unità originali, persona, connessione e fonte conservati; deduplica non basata solo su valore/giorno.
- Dashboard, Corpo, Insights e report devono usare i repository canonici, non merge divergenti.
- Health Connect non rende automaticamente disponibili tutti i dati delle altre app. Gestire esplicitamente tipi, permessi, storico, background e write-back.
- AI esclusivamente opzionale, autorizzata, revisionabile e disattivabile. Non riutilizzare sessioni consumer come API occulte.
- Non pubblicare credenziali, token, chiavi private, dati sanitari, nomi dei soggetti sanitari o identificativi reali nei log/fixture/repository.
- Le misurazioni Xiaomi sono read-only nel connettore; nessuna modifica/cancellazione dell'archivio vendor.
- Il backup della baseline è incompleto: niente suggerimenti di disinstallazione/cancellazione basati sulla presunzione che ripristini tutto.
- La chiave CI pubblica esistente è TEST-ONLY e non autentica il publisher. Non usarla per produzione o considerarla una protezione per il normale uso con credenziali sensibili; seguire il gate SAFE.
- Uso personale gratuito e commercial licensing sono la direzione. Non applicare MIT/Apache/GPL/PolyForm al progetto né inventare una licenza senza decisione del proprietario. Rispettare le licenze separate delle dipendenze.

## Come scegliere il lavoro

Riprendere il task attivo o il primo task pronto in `docs/PROGRESS.md`. Verificare dipendenze e accettazione nel master. In assenza di una regressione, la prima attività programmata è `FH-XIA-01`: parser/contratti Xiaomi eseguibili con fixture, senza login reale.

Il proprietario ha autorizzato le attività di questo piano per le sessioni che richiede e il lavoro diretto su `main`. Non richiedere di nuovo il consenso per «Phase 3» o altre fasi già incluse. Non rieseguire la pianificazione al posto dello sviluppo a ogni «continua».

Una sessione deve chiudere una vertical slice utile o un prerequisito eseguibile verificabile. Evitare molte feature parziali contemporanee. Se un test hardware o un login è bloccato, completare lavoro indipendente previsto dal piano e registrare il blocco.

## Esecuzione, commit e prove

- Usare task ID nel commit e nel registro; mantenere commit multi-file atomici.
- Rileggere HEAD prima dell'aggiornamento di `main`; se è cambiato, confrontare e integrare senza sovrascrivere. Mai force-push.
- Eseguire test pertinenti e `./gradlew testDebugUnitTest lintDebug assembleDebug` per codice/config Android; anche `python3 -m unittest discover -s ci -p 'test_*.py' -v` quando pertinente alla CI.
- Test Room/dispositivo non sostituibili con mock. Migrazioni, backup e outbox fanno parte di ogni cambiamento persistente.
- Non disabilitare lint/test per ottenere verde e non dichiarare eseguito un comando non eseguito.
- Per soli documenti, verificare link, ID, dipendenze e diff. È consentito evitare la build Android quando nessun file eseguibile cambia e le regole di branch lo consentono; dichiararlo nel riepilogo.
- Verificare il commit finale e l'APK/release effettivo, non una run di una versione precedente. Non sostituire una release funzionante con un APK non verificato.
- Aggiornare `docs/PROGRESS.md` con risultati, file/commit, test e livello di verifica, limite residuo e prossimo passo. Nessun task VERIFIED solo perché esiste una classe o la build compila.

## Limiti dell'autonomia

Chiedere prima di usare segreti/account reali, avviare spese, scegliere la licenza finale, pubblicare dati sanitari, fare cancellazioni/restore sostitutivi, cambiare identità/firma con effetti sugli aggiornamenti, introdurre un backend obbligatorio o pubblicare negli store. Il piano non autorizza lavoro schedulato automatico dopo la sessione: quello richiede una richiesta distinta.
