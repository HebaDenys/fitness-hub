# Fitness Hub

[![Android CI](https://github.com/HebaDenys/fitness-hub/actions/workflows/android.yml/badge.svg)](https://github.com/HebaDenys/fitness-hub/actions/workflows/android.yml)

App Android local-first per unificare salute, composizione corporea, alimentazione, attività, sonno, allenamenti, obiettivi, analytics e report.

**Stato: pre-alpha, candidate di test 0.3.12.** Codice e test non equivalgono a compatibilità hardware verificata o prodotto pronto per credenziali sensibili.

## Piano e continuità

- **[Piano completo](docs/MASTER_PLAN.md):** decisioni, architettura, backlog con ID, criteri verificabili e rischi.
- **[Stato operativo](docs/PROGRESS.md):** attività da riprendere, commit, risultati, blocchi e prove.
- **[Roadmap](docs/ROADMAP.md):** ordine e dipendenze dei traguardi.
- **[Istruzioni agenti](AGENTS.md):** cosa leggere e come avanzare in ogni sessione.

La 0.3.12 è una candidate funzionale: dati corporei canonici condivisi, record Health Connect con timestamp/ID, inserimento manuale offline, Centro sorgenti, onboarding, grafici interattivi, pasti con basi nutrizionali esplicite, backup/restore e CSV grezzo. **Xiaomi cloud login resta bloccato dalla firma privata.** La candidate non richiede INTERNET: lookup Open Food Facts e AI remota sono disabilitati invece di simulare una connessione.

## Obiettivo

Conservare il proprio hardware e usare una sola app come archivio e interfaccia di follow-up. Registrare pasti e allenamenti, importare dati autorizzati, ottenere grafici coerenti e report esportabili, recuperare l'archivio e condividere metriche compatibili con Android Health Connect.

Core senza account Fitness Hub, backend obbligatorio, pubblicità, tracking o abbonamento necessario. Integrazioni vendor e AI esplicite e facoltative; quanto già archiviato consultabile offline. Uso personale gratuito e licenza commerciale separata sono la direzione, non una licenza definitiva già applicata.

## Xiaomi nello stesso APK

Flusso principale da validare end-to-end sul telefono:

```text
S400 -> Xiaomi Home -> servizi Xiaomi -> connettore dentro Fitness Hub
                                                  |
                                                  v
                                              Room locale
                                                  |
                                  Sorgente Xiaomi / storico / backup
                                                  |
                         UI canonica / analytics / Health Connect (da completare)
```

Nessun root, script, container, server o secondo APK ponte. Xiaomi Home resta installata. CSV e BLE presenti sono opzioni secondarie.

0.3.3: archivio/binding/committer. 0.3.4: motore autenticazione, HTTPS e sessione cifrata. **0.3.5: runtime Hilt condiviso e schermata collegata a questi componenti**. Le persone/dispositivi sono scoperti nelle pagine dello storico accessibile, non attraverso un catalogo famiglia inventato; identificativi esatti e doppia conferma impediscono assegnazioni basate solo su nome/peso. I nomi facoltativi rimangono effimeri.

Il runtime rimane bloccato in attesa di firma privata/custodia/migrazione approvate. Challenge sono rilevati, non completati. Sincronizzazione periodica, resolver comune, prova vendor/Redmi e write-back HC restano incompleti. Snapshot/versioni dello stesso evento non sono dichiarati automaticamente pesate distinte.

[Protocollo](docs/connectors/xiaomi-protocol.md) — [Autenticazione/sessione](docs/connectors/xiaomi-auth.md) — [Onboarding](docs/connectors/xiaomi-onboarding.md) — [Archivio](docs/connectors/xiaomi-storage.md) — [Strategia](docs/connectors/xiaomi-s400.md).

Per la Mi Band: companion compatibile -> Health Connect -> Fitness Hub. Modello/app e metriche realmente condivise vanno verificati. Il dato mostrato dal produttore non è automaticamente esposto a HC.

## Stato del software

| Area | Stato e limiti |
|---|---|
| Fondazione | Kotlin/Compose/Material 3, Hilt, Room schema 9, un modulo Android. |
| Health Connect | Lettura di 11 tipi; peso e grasso ora conservano anche record ID/timestamp esatti oltre alla cache giornaliera. Fasi sonno, tipi aggiuntivi, storico completo e write-back restano da completare; finestra applicativa 30/365 giorni. |
| Xiaomi | Protocollo, archivio, autenticazione/sessione e UI sorgente verificati con fixture, Room e test Compose. Login reale gated; binding iniziale singolo e immutabile. |
| Nutrizione | Diario, manuale, barcode locale e OCR on-device; basi per 100g/porzione esplicite. Open Food Facts remoto disabilitato nella candidate no-INTERNET. |
| Training | Esercizi, sessioni, serie, RPE/RIR e stime 1RM/PR prototipali. |
| Analytics | Peso corporeo condiviso fra Dashboard, Corpo, Insights/export tramite repository canonico; riconciliazione cross-source resta conservativa e da estendere alle altre metriche. |
| Backup | Formato v2 di 22 tabelle dominio, ripristino transazionale e conflitti verificati con SQLite nativo. Esclude credenziali, preferenze e media; stesso schema DB richiesto. |
| AI | Codice BYOK prototipale presente ma rete/UI disabilitate nella candidate no-INTERNET; nessuna chiave richiesta per il test. |
| Distribuzione | APK e checksum in release; chiave pubblica TEST-ONLY, non firma privata di produzione. |

Esiti CI, SHA software e limiti di verifica sono in [PROGRESS.md](docs/PROGRESS.md). Test con connessioni simulate, AES di test, Room/SQLite nativo e interazioni Compose sotto Robolectric non sostituiscono account Xiaomi, handshake remoto, hardware Keystore o S400 fisica.

## Installazione e recupero

[APK 0.3.12](https://github.com/HebaDenys/fitness-hub/releases/download/test-latest/FitnessHub-v0.3.12-debug.apk) dalla [prerelease test-latest](https://github.com/HebaDenys/fitness-hub/releases/tag/test-latest). Controllare versione, commit, checksum e note; la release rolling può essere aggiornata.

**Impostazioni -> Xiaomi Home** mostra blocco di accesso e archivio locale. Le credenziali non vengono richieste nel canale test attuale. Nessun account o dato dimostrativo è aggiunto al database dell'utente.

La chiave test pubblica mantiene continuità fra build compatibili ma non autentica il distributore contro chi possiede la stessa chiave. Firma privata/custodia/migrazione restano un gate prima del normale uso con login sensibili.

**Non disinstallare una versione con dati importanti per risolvere un errore di firma.** Il vecchio package Open Health Hub e `io.github.hebadenys.fitnesshub` sono identità distinte, senza migrazione automatica dimostrata. La 0.3.12 mantiene package e chiave test e porta il database a schema 9 con migrazioni additive per identità corporea, basi nutrizionali e misure manuali.

Backup v2 in **Insights -> Backup**: file cifrato, apertura e ripristino. Copre righe DB, non l'intero ambiente app. Massimo 32 MiB decifrati, stesso schema; conflitti annullano tutto senza sovrascrivere. V1 parziale solo in archivio salute vuoto; catalogo esercizi e impostazioni del profilo possono rimanere. Prova fisica di trasferimento e portabilità fra schemi ancora da completare. [Formato e limiti](docs/backup-format-v2.md).

## Architettura e qualità

Room è l'archivio proprio; Health Connect è interoperabilità, non l'unico database. Connettori isolati, provenienza per metrica, identità, qualità e repository canonici sono i confini da completare. [Architettura](docs/ARCHITECTURE.md).

Mancante non significa zero. Importato non significa misurato: una stima vendor resta una stima vendor. Originali e correzioni distinti; nessuna fusione basata solo sul valore o giorno. L'archivio conserva contenuti cambiati come snapshot separati, non inventa revisioni vendor. Media mobile e dato grezzo non sono intercambiabili.

## Build

JDK 21, Gradle Wrapper 8.13, compile SDK 36, target SDK 35, min SDK 28. Verificare le versioni effettive nei file Gradle.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
python3 -m unittest discover -s ci -p 'test_*.py' -v
```

Su Windows usare `gradlew.bat`. APK locale in `app/build/outputs/apk/debug/`. I test includono Jupiter e Vintage/Robolectric con SQLite nativo e Compose; conservarli. Gli schemi JSON devono provenire da KSP. La CI verifica certificato e checksum prima della pubblicazione.

## Privacy e licenza

Nessun backend Fitness Hub obbligatorio o SDK di tracking. Backup OS e device transfer esclusi; file esportati dall'utente possono essere salvati presso un document provider cloud. Sessione Xiaomi cifrata in noBackupFilesDir e non inclusa nel backup dati. La schermata Xiaomi maschera la password, non la conserva nello stato ripristinabile e usa FLAG_SECURE mentre è visibile. Ciò non garantisce cancellazione delle copie String in memoria o comportamento di ogni OEM.

**Licenza del progetto da finalizzare.** Direzione source-available, uso personale gratuito e licenza commerciale separata: non presentarla come OSI se limita l'uso commerciale. Il piano non applica automaticamente PolyForm/MIT/Apache al progetto; upstream e dipendenze mantengono i loro avvisi.

[Contributi](CONTRIBUTING.md) e [sostenibilità](docs/SUSTAINABILITY.md). Nessuna monetizzazione tramite vendita di dati sanitari o paywall obbligatorio sul core locale.

## Limite sanitario

Strumento personale di benessere e fitness, non diagnosi o dispositivo medico. Misure consumer, formule e associazioni statistiche non sono prove cliniche; correlazione non significa causalità.
