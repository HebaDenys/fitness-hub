# Fitness Hub — Istruzioni per le sessioni di sviluppo

## Fonti di verità

Leggere dal repository reale all'inizio di ogni sessione:

1. `docs/MASTER_PLAN.md`: obiettivo, decisioni, contratti e backlog con ID stabili.
2. `docs/PROGRESS.md`: cursore operativo, attività, blocchi e prove.
3. `README.md`, `docs/ROADMAP.md`, `docs/ARCHITECTURE.md` e documenti della parte coinvolta.
4. Codice, test, migrazioni, HEAD, modifiche recenti, issue/PR e CI effettivi.

Non ricostruire lo stato dai ricordi o dai titoli dei commit. Il piano è una specifica, non la prova che una funzione esista.

## Decisione prioritaria

Il proprietario ha approvato:

`S400 -> Xiaomi Home -> servizi Xiaomi -> connettore Fitness Hub -> Room -> UI/analytics -> Health Connect opt-in`.

Un solo APK: niente root, script, CLI SmartScaleConnect, container, server o CSV manuale obbligatorio. SmartScaleConnect può essere riferimento MIT con attribuzione e verifica upstream. CSV/BLE restano ripieghi e non vanno rimossi distruggendo dati.

La precedente strategia CSV storico + BLE live è superata. Local-first non vieta il cloud del vendor autorizzato; vieta introdurre un nostro backend obbligatorio senza decisione.

## Vincoli

- Kotlin + Compose + Hilt + Room + Health Connect; niente riscritture o esplosione di moduli senza un problema reale.
- Archivio proprio offline, nessun account Fitness Hub obbligatorio, tracking, pubblicità o backend obbligatorio.
- Mancante, zero misurato, permesso negato e tipo non supportato sono distinti. Nessun dato inventato.
- Provenienza per metrica: import è trasporto, non accuratezza; stima vendor, stima locale e originale non sono intercambiabili.
- Preservare identità persona/connessione/fonte, tempi/unità originali e revisioni solo quando il protocollo le dimostra.
- Deduplica non basata solo su peso/giorno/nome. Snapshot e pesate non sono automaticamente uno-a-uno.
- Dashboard, Corpo, Insights e report devono convergere sui repository canonici, non su merge divergenti.
- HC non espone automaticamente tutto ciò che compare nelle altre app; tipi, permessi, storico e write-back vanno verificati.
- AI facoltativa, autorizzata e revisionabile; niente sessioni consumer usate come API occulte.
- Non pubblicare token, password, chiavi private, dati sanitari o identificativi reali in log/fixture/repository.
- Archivio Xiaomi remoto read-only; nessuna modifica/cancellazione vendor.
- Backup v2: tabelle dati, non preferenze/media/credenziali; restore stesso schema senza conflitti. V1 parziale. Niente disinstallazioni basate su recupero completo non provato.
- Chiave CI pubblica TEST-ONLY: non autentica il publisher e non è un canale per credenziali reali. Seguire FH-SAFE-03.
- Uso personale gratuito/commercial licensing sono la direzione, non una licenza già scelta. Non applicare la licenza definitiva senza decisione; rispettare le dipendenze.

## Cosa è già implementato

0.3.2–0.3.5: protocollo Xiaomi, archivio/sessione/UI e resolver corporeo condiviso. 0.3.6: schema 7 con record Health Connect peso/body-fat a ID/timestamp esatti, migrazione/backup e fallback legacy giornaliero. Leggere esiti/limiti in PROGRESS, non rifare questi blocchi da zero.

Discovery non è un catalogo della famiglia Xiaomi: mostra solo identità osservate, non persiste pesate prima della selezione. I nomi sono effimeri; chiavi includono ID esatti. Il binding corrente è unico/immutabile. La UI non riceve XiaomiSession o cookie. Un solo runtime/client/store per app.

Il runtime usa ancora `AwaitingPrivateSigning`. La UI di test mostra il blocco e NON chiede password. Non rimuovere il gate o aggiungere un flag permissivo. Prima dei login reali occorre decisione di firma privata/custodia/migrazione. `xiaomi-auth.md` documenta che CAPTCHA/2FA sono solo rilevati e redirect STS non sono seguiti. Fixture non equivalgono a una prova reale.

## Prossimo lavoro

Riprendere il primo task pronto di PROGRESS. In assenza di cambiamenti, il blocco tecnico successivo è **FH-UX-02/03/04/06 + FH-HC-01/13**: centro Sorgenti, diagnostica Health Connect read-only e rifinitura Material 3/accessibilità. Le regole complete sono nel master.

1. Leggere BodyViewModel, DashboardViewModel, InsightsRepository, CSV/export, store HC/scale e snapshot Xiaomi prima di progettare il resolver.
2. Conservare tutti gli originali. Non scegliere una revisione Xiaomi solo perché dataVersion/sn sembrano ID certi.
3. Gestire ultimi valori per metrica e intervalli di calendario; non presentare il peso di ieri come misurato oggi né limitare 30 giorni a 30 righe sparse.
4. Testare coerenza tra viste con stesse fixture, origine/metodo/unità, zeri/mancanti, versioni e sovrapposizioni; segnalare ambiguità anziché sommare/cancellare.
5. Non estendere raccolta a nuove categorie sensibili senza scope approvato. Se la decisione firma resta aperta, completare il lavoro indipendente senza dichiarare il login utilizzabile.

Il proprietario ha autorizzato il piano nelle sessioni richieste e il lavoro diretto su `main`. Non richiedere di nuovo consenso per le fasi tecniche né ripianificare invece di implementare. Ogni sessione deve chiudere un incremento utile verificabile, non molte feature scollegate.

## Commit e verifica

- Commit multi-file atomici con task ID. Rileggere HEAD immediatamente prima di update_ref, usare expected_sha; mai force-push. Integrare cambiamenti concorrenti anziché sovrascriverli.
- Eseguire test pertinenti e `./gradlew testDebugUnitTest lintDebug assembleDebug` per codice/config Android. Eseguire i controlli Python CI/privacy pertinenti.
- Preservare Jupiter e Vintage/Robolectric nativi/Compose. DAO mock non sostituiscono test Room. Robolectric non sostituisce il telefono.
- Schemi Room da KSP, senza inventare identityHash. Migrazioni, backup e outbox fanno parte di ogni cambiamento persistente.
- Non disabilitare lint/test per ottenere verde; non dichiarare comandi mai eseguiti. Distinguere harness, JUnit, HTTP fake, TLS reale, Keystore simulato e hardware.
- Verificare SHA software, run, artifact e release effettivi. Un commit finale solo documentale o che adotta l'esatto schema generato non richiede un APK identico.
- Per soli documenti controllare link, ID e diff; è consentito `[skip ci]` se nessun file eseguibile cambia.
- Aggiornare PROGRESS con risultati, prove, limiti e primo passo successivo. Nessun task VERIFIED dalla sola esistenza di una classe o compilazione.

## Limiti dell'autonomia

Chiedere prima di usare segreti/account reali, spendere, scegliere licenza finale, pubblicare dati sanitari, cancellare/effettuare restore sostitutivi, cambiare identità/firma con effetti sugli aggiornamenti, introdurre backend obbligatorio o pubblicare negli store. Nessuna esecuzione futura automatica è autorizzata dal solo piano.
