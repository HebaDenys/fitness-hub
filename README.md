# Fitness Hub

[![Android CI](https://github.com/HebaDenys/fitness-hub/actions/workflows/android.yml/badge.svg)](https://github.com/HebaDenys/fitness-hub/actions/workflows/android.yml)

App Android local-first per unificare salute, composizione corporea, alimentazione, attività, sonno, allenamenti, obiettivi, analytics e report.

**Stato: pre-alpha, build di test.** Codice e funzioni prototipali non equivalgono a compatibilità hardware verificata o a prodotto pronto per dati sensibili.

## Piano e continuità del lavoro

- **[Piano completo](docs/MASTER_PLAN.md):** obiettivi, decisioni, architettura, backlog con ID e criteri verificabili, scenari di accettazione e rischi.
- **[Stato operativo](docs/PROGRESS.md):** attività da riprendere, risultati, blocchi e prove effettive.
- **[Roadmap](docs/ROADMAP.md):** ordine e dipendenze dei traguardi.
- **[Istruzioni agenti](AGENTS.md):** cosa leggere e come avanzare in ogni sessione.

Il prossimo incremento programmato è `FH-XIA-01`: contratti/parser Xiaomi eseguibili con fixture, senza credenziali reali. Il piano viene usato nelle sessioni avviate dall'utente; non attiva da solo esecuzioni automatiche.

## Obiettivo

Conservare il proprio hardware e usare una sola app come archivio e interfaccia di follow-up. Registrare pasti e allenamenti, importare dati autorizzati dalle fonti, ottenere grafici coerenti e report esportabili, poter recuperare l'archivio e condividere dati compatibili con Android Health Connect.

Il core deve funzionare senza account Fitness Hub, backend obbligatorio, pubblicità, tracking o abbonamento necessario. Le integrazioni vendor e AI sono esplicite e facoltative. Quanto già archiviato rimane consultabile offline.

## Decisione Xiaomi aggiornata

Il flusso principale **da implementare** è:

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

Nessun root, script, container, server o secondo APK ponte richiesto. Xiaomi Home resta installata; il nostro connettore sarà integrato nell'app. L'import CSV e il ricevitore BLE presenti rimangono opzioni secondarie, non il percorso principale richiesto.

**La versione 0.3.1 non implementa ancora questo collegamento cloud.** [Strategia e limiti](docs/connectors/xiaomi-s400.md).

Per la Mi Band il percorso previsto è companion compatibile -> Health Connect -> Fitness Hub. Modello/companion e metriche effettivamente condivise vanno verificati: non ogni dato mostrato nell'app del produttore è automaticamente esposto a HC.

## Stato verificato alla baseline 0.3.1

| Area | Stato e limiti |
|---|---|
| Fondazione | Kotlin/Compose/Material 3, Hilt, Room e un modulo Android. |
| Health Connect | Lettura di 11 tipi, sync e permessi prototipali. Fasi sonno, tipi aggiuntivi, gestione completa dello storico e write-back da completare. |
| Storico | Finestra HC applicativa di 30/365 giorni; non è l'intero archivio possibile. |
| Bilancia | CSV SmartScaleConnect con controlli/transazioni e BLE sperimentale; nessun login cloud integrato. |
| Nutrizione | Catalogo locale, diario, barcode/OCR e Open Food Facts prototipali. |
| Training | Esercizi, sessioni, serie, RPE/RIR, stime 1RM/PR prototipali. |
| Analytics | Grafici e correlazioni iniziali; canonicalizzazione comune a tutte le viste ancora da completare. |
| Portabilità | Cifratura backup, file picker e CSV presenti; backup non ancora completo/lossless. |
| AI | BYOK/configurazione/trasporto iniziali, non un sistema completo di riconoscimento pasti. |
| Distribuzione | APK di test e checksum prodotti dalla CI; chiave pubblica test-only, non firma privata di produzione. |

La [CI della baseline](https://github.com/HebaDenys/fitness-hub/actions/runs/37499457080) è passata. Questo non sostituisce prove Room, telefono, Xiaomi Cloud o hardware. Lo stato successivo è nel [registro operativo](docs/PROGRESS.md), non nelle promesse di questa tabella.

## Installazione e protezione dei dati

L'APK di sviluppo è pubblicato nella [prerelease test-latest](https://github.com/HebaDenys/fitness-hub/releases/tag/test-latest). Controllare versione, commit e note della release prima dell'installazione.

La chiave di firma di test presente nel repository è deliberatamente pubblica: consente continuità fra build di test compatibili, ma non autentica l'autore contro chi possiede la stessa chiave. Non è una chiave da usare per produzione. Installare soltanto APK provenienti dal repository e seguire i gate di sicurezza prima del normale utilizzo con credenziali sensibili.

**Non disinstallare una vecchia build che contiene dati importanti soltanto per risolvere un errore di firma.** Il backup attuale non copre ancora tutti i dati. Il vecchio package Open Health Hub e `io.github.hebadenys.fitnesshub` sono identità distinte: non esiste una migrazione automatica dimostrata fra i due.

## Architettura

Room è l'archivio locale; Health Connect è l'interoperabilità Android, non l'unico database. Connettori isolati, provenienza per metrica, identità persona/sorgente, deduplicazione, controlli di qualità e repository canonici sono i confini da completare. [Architettura](docs/ARCHITECTURE.md).

Mancante non significa zero. Importato non significa misurato. Una stima vendor resta una stima vendor e una correzione utente non falsifica la sorgente. I grafici devono dichiarare smoothing, copertura e assunzioni.

## Build

Baseline: JDK 21, Gradle Wrapper 8.13, compile SDK 36, target SDK 35, min SDK 28. Verificare le versioni nel codice prima di aggiornarle.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
python3 -m unittest discover -s ci -p 'test_*.py' -v
```

Su Windows usare `gradlew.bat`. L'APK locale è generato in `app/build/outputs/apk/debug/`. La CI verifica anche firma e checksum prima della pubblicazione. Un aggiornamento solo documentale non richiede una nuova versione APK.

## Privacy e licenza

Nessun backend Fitness Hub obbligatorio o SDK di tracking. Le policy di accesso ai servizi vendor, consenso, storage, backup di sistema e trasferimento dati devono corrispondere a ciò che l'app fa realmente; il nuovo piano include la loro verifica prima del login Xiaomi.

**Licenza del progetto: ancora da finalizzare.** La direzione approvata è uso personale privato gratuito e licenza commerciale separata. Non è una licenza OSI open source se limita l'uso commerciale, e il piano non concede automaticamente diritti o applica PolyForm/MIT/Apache al codice. Le dipendenze e l'eventuale codice upstream conservano le proprie licenze.

[Contributi](CONTRIBUTING.md) e [sostenibilità](docs/SUSTAINABILITY.md). Nessuna monetizzazione tramite vendita di dati sanitari o paywall obbligatorio sul core locale.

## Limite sanitario

Fitness Hub è uno strumento personale di benessere e fitness, non una diagnosi o un dispositivo medico. Misure consumer, formule e associazioni statistiche non sono prove cliniche; correlazione non significa causalità.
