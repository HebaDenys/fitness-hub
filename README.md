# Fitness Hub

[![Android CI](https://github.com/HebaDenys/fitness-hub/actions/workflows/android.yml/badge.svg)](https://github.com/HebaDenys/fitness-hub/actions/workflows/android.yml)

App Android local-first per unificare salute, composizione corporea, alimentazione, attività, sonno, allenamenti, obiettivi, analytics e report.

**Stato: pre-alpha, build di test 0.3.4.** Codice e test non equivalgono a compatibilità hardware verificata o prodotto pronto per credenziali sensibili.

## Piano e continuità

- **[Piano completo](docs/MASTER_PLAN.md):** decisioni, architettura, backlog con ID, criteri verificabili e rischi.
- **[Stato operativo](docs/PROGRESS.md):** attività da riprendere, commit, risultati, blocchi e prove.
- **[Roadmap](docs/ROADMAP.md):** ordine e dipendenze dei traguardi.
- **[Istruzioni agenti](AGENTS.md):** cosa leggere e come avanzare in ogni sessione.

Il prossimo incremento riguarda onboarding, selezione e stato Xiaomi (`FH-XIA-05/06/10`). Parser, archivio Room, motore autenticazione/trasporto/sessione esistono e sono testati con risposte sintetiche; **non c'è ancora un login Xiaomi utilizzabile nell'interfaccia**. Il piano guida le sessioni richieste dall'utente, non attiva esecuzioni schedulate autonome.

## Obiettivo

Conservare il proprio hardware e usare una sola app come archivio e interfaccia di follow-up. Registrare pasti e allenamenti, importare dati autorizzati, ottenere grafici coerenti e report esportabili, recuperare l'archivio e condividere metriche compatibili con Android Health Connect.

Core senza account Fitness Hub, backend obbligatorio, pubblicità, tracking o abbonamento necessario. Integrazioni vendor e AI esplicite e facoltative; quanto già archiviato consultabile offline. Uso personale gratuito e licenza commerciale separata sono la direzione del progetto, non una licenza definitiva già applicata.

## Xiaomi nello stesso APK

Flusso principale **ancora da completare end-to-end sul telefono**:

```text
S400 -> Xiaomi Home -> servizi Xiaomi -> connettore dentro Fitness Hub
                                                  |
                                                  v
                                              Room locale
                                                  |
                                    UI / analytics / report / backup
                                                  |
                                      Health Connect opt-in
```

Nessun root, script, container, server o secondo APK ponte. Xiaomi Home resta installata. CSV e BLE presenti sono opzioni secondarie.

0.3.3 ha aggiunto archivio/binding/committer; **0.3.4 aggiunge motore di login normale, trasporto autenticato, sessione cifrata e collegamento alle letture Room**. Il runtime di rete rimane bloccato in attesa di firma privata/custodia/migrazione approvate. Challenge sono rilevati, non completati. Onboarding/selettore, sincronizzazione periodica, prova vendor/Redmi e write-back HC restano incompleti.

[Protocollo](docs/connectors/xiaomi-protocol.md) — [Autenticazione/sessione](docs/connectors/xiaomi-auth.md) — [Archivio](docs/connectors/xiaomi-storage.md) — [Strategia](docs/connectors/xiaomi-s400.md).

Per la Mi Band: companion compatibile -> Health Connect -> Fitness Hub. Modello/app e metriche realmente condivise vanno verificati. Il dato mostrato dal produttore non è automaticamente esposto a HC.

## Stato del software

| Area | Stato e limiti |
|---|---|
| Fondazione | Kotlin/Compose/Material 3, Hilt, Room schema 6, un modulo Android. |
| Health Connect | Lettura di 11 tipi e sync prototipale. Fasi sonno, tipi aggiuntivi, storico completo e write-back da completare; finestra applicativa 30/365 giorni. |
| Xiaomi | Protocollo, binding singolo subject/device, snapshot, committer, autenticazione e sessione verificati con fixture; rete reale gated e UI non pronta. CSV/BLE preesistenti. |
| Nutrizione | Catalogo locale, diario, barcode/OCR e Open Food Facts prototipali. |
| Training | Esercizi, sessioni, serie, RPE/RIR e stime 1RM/PR prototipali. |
| Analytics | Grafici e correlazioni iniziali; repository canonico comune e deduplica cross-source da completare. |
| Backup | Formato v2 delle 19 tabelle dati, ripristino transazionale e conflitti verificati con SQLite nativo. Esclude credenziali, preferenze e media; stesso schema DB richiesto. |
| AI | BYOK/configurazione/trasporto iniziali, non riconoscimento pasti completo. |
| Distribuzione | APK e checksum in release; chiave pubblica TEST-ONLY, non firma privata di produzione. |

[CI 0.3.4 verificata](https://github.com/HebaDenys/fitness-hub/actions/runs/37530351539): 346 test app, 11 controlli CI/privacy, build, lint, firma e pubblicazione passati. Test con connessioni simulate, AES di test e Room/SQLite nativo sotto Robolectric non sostituiscono prova account Xiaomi, handshake remoto, hardware Keystore o S400 fisica. Dettagli in [PROGRESS.md](docs/PROGRESS.md).

## Installazione e recupero

[APK 0.3.4](https://github.com/HebaDenys/fitness-hub/releases/download/test-latest/FitnessHub-v0.3.4-debug.apk) dalla [prerelease test-latest](https://github.com/HebaDenys/fitness-hub/releases/tag/test-latest). Controllare versione, commit e note. Il motore Xiaomi è incluso ma il login reale è disabilitato.

La chiave test pubblica mantiene continuità fra build compatibili ma non autentica il distributore contro chi possiede la stessa chiave. Firma privata/custodia/migrazione restano un gate prima del normale uso con login sensibili.

**Non disinstallare una versione con dati importanti per risolvere un errore di firma.** Il vecchio package Open Health Hub e `io.github.hebadenys.fitnesshub` sono identità distinte, senza migrazione automatica dimostrata. La 0.3.4 non cambia package, chiave test o database rispetto alla 0.3.3.

Backup v2 in **Insights -> Backup**: file cifrato, apertura e ripristino. Copre righe DB, non l'intero ambiente app. Massimo 32 MiB decifrati, stesso schema; conflitti annullano tutto senza sovrascrivere. V1 parziale solo in archivio salute vuoto; catalogo esercizi e impostazioni del profilo possono rimanere. Prova fisica di trasferimento e portabilità fra schemi diversi ancora da completare. [Formato e limiti](docs/backup-format-v2.md).

## Architettura e qualità

Room è l'archivio proprio; Health Connect è interoperabilità, non l'unico database. Connettori isolati, provenienza per metrica, identità, qualità e repository canonici sono i confini da completare. [Architettura](docs/ARCHITECTURE.md).

Mancante non significa zero. Importato non significa misurato: una stima vendor resta una stima vendor. Originali e correzioni restano distinti; nessuna fusione basata solo sul valore o sul giorno. Il nuovo archivio conserva contenuti cambiati come snapshot separati, non inventa revisioni vendor. Media mobile e dato grezzo non sono intercambiabili.

## Build

JDK 21, Gradle Wrapper 8.13, compile SDK 36, target SDK 35, min SDK 28. Verificare le versioni effettive nei file Gradle.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
python3 -m unittest discover -s ci -p 'test_*.py' -v
```

Su Windows usare `gradlew.bat`. APK locale in `app/build/outputs/apk/debug/`. I test includono Jupiter e Vintage/Robolectric con SQLite nativo; conservare entrambi. Gli schemi JSON devono provenire da KSP. La CI verifica certificato e checksum prima della pubblicazione.

## Privacy e licenza

Nessun backend Fitness Hub obbligatorio o SDK di tracking. Backup OS automatico e device transfer esclusi; file esportati dall'utente possono essere salvati presso un document provider cloud. La sessione Xiaomi è cifrata separatamente in noBackupFilesDir e non compare nel backup dati. Policy vendor, UI dei segreti e comportamento OEM devono essere verificati prima dei login reali.

**Licenza del progetto da finalizzare.** Direzione source-available, uso personale gratuito e licenza commerciale separata: non presentarla come licenza OSI se limita l'uso commerciale. Il piano non applica automaticamente PolyForm/MIT/Apache al progetto; upstream e dipendenze mantengono i loro avvisi.

[Contributi](CONTRIBUTING.md) e [sostenibilità](docs/SUSTAINABILITY.md). Nessuna monetizzazione tramite vendita di dati sanitari o paywall obbligatorio sul core locale.

## Limite sanitario

Strumento personale di benessere e fitness, non diagnosi o dispositivo medico. Misure consumer, formule e associazioni statistiche non sono prove cliniche; correlazione non significa causalità.
