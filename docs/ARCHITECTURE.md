# Fitness Hub — Architettura attuale e obiettivo

**Specifica:** [MASTER_PLAN.md](MASTER_PLAN.md), sezioni 3–5.  
**Stato/prove:** [PROGRESS.md](PROGRESS.md).  
**Aggiornamento:** 6 ottobre 2026, incremento 0.3.5.

## 1. Vincoli

Una sola app Android Kotlin/Compose, Hilt, Room e Health Connect. Archivio proprio consultabile offline; nessun account Fitness Hub o backend obbligatorio. Integrazione vendor autorizzata facoltativa, senza sostituire Xiaomi Home o la companion della band.

Il prodotto non è ancora un hub completo: la presenza di componenti e test non dimostra compatibilità account/hardware.

## 2. Componenti attuali

Un modulo `app`, package `io.github.hebadenys.fitnesshub`, database Room 6 e migrazioni v1–v5→v6. Gateway HC e coordinamento sync, nutrizione, CSV/BLE, workout, analytics, backup e AI rimangono in fasi diverse di sviluppo.

Xiaomi ha ora quattro confini eseguibili:

- **Protocollo:** parser puro/JSON limitato, contratti di scope e paginazione, metriche/unità/metodi/qualità.
- **Accesso:** autenticazione normale, HTTP HTTPS limitato e read-only, sessione AES-GCM/Keystore fuori da Room/backup; challenge solo rilevati.
- **Archivio:** identità locale, binding persona/device immutabile, snapshot sorgente e checkpoint nella stessa transazione.
- **Presentazione:** `XiaomiSourceGateway`, repository e ViewModel; route Impostazioni -> Xiaomi Home con stato, discovery, selezione e storico offline. Hilt fornisce un solo runtime/client/store.

Il runtime è bloccato da `AwaitingPrivateSigning`; la UI di test non richiede credenziali. Non è ancora una verifica automatica del certificato privato o un login operativo sul telefono.

## 3. Flusso Xiaomi implementato dietro il gate

```text
XiaomiSourceScreen / ViewModel
             |
      XiaomiSourceGateway
             |
     XiaomiSourceRepository ---------> query Room paginata -> dettagli offline
             |
       XiaomiCloudClient
        /          \
 auth/sessione   richieste storico
                     |
             XiaomiDiscoveryReader -> candidati effimeri -> conferma esplicita
                     |
             XiaomiHistoryReader
                     |
             RoomXiaomiArchive -> binding + snapshot + checkpoint
```

Discovery usa lo storico del modello: non un endpoint famiglia inventato. Prima della conferma conserva solo metadati in memoria, non misure di altre persone. I nomi facoltativi servono come etichette, non chiavi; non sono aggiunti ai payload persistiti.

Il repository fornisce soltanto metadati permessi all'interfaccia, mai serviceToken/ssecurity. I comandi sono serializzati, interrompibili e verificano di nuovo la sessione/binding quando necessario. Il logout non cancella archivio o associazione della persona.

Le query locali mostrano pagine da 20 snapshot e un dettaglio per metrica. Un contenuto modificato può rappresentare la stessa pesata: il conteggio degli snapshot non è il numero di eventi canonici.

## 4. Flusso obiettivo da completare

```text
Companion band -> Health Connect gateway --+
                                           |
Xiaomi Home -> Xiaomi Cloud connector ------+-> ingestione/staging
                                           |        |
Barcode/OCR/manuale/workout ----------------+        v
                                         identità + normalizzazione
                                                    |
                                     validazione + riconciliazione
                                                    |
                                          Room e checkpoint
                                                    |
                          +-------------------------+-------------------+
                          |                         |                   |
                   repository canonici       outbox HC opt-in    backup/export
                          |
                 UI / obiettivi / analytics / report
```

La vista sorgente Xiaomi non è il resolver condiviso. Dashboard, Corpo e Insights conservano ancora logiche diverse: il prossimo blocco FH-DATA-05/FH-BODY-02/FH-REC-03 deve unificarle senza riscrivere gli originali.

HC legge un sottoinsieme di tipi, con finestra applicativa 30/365; dettaglio fasi sonno e write-back restano incompleti. Nessun worker periodico Xiaomi è ancora registrato.

## 5. Identità, provenienza e tempo

Chiavi comprendono persona, connessione, subject vendor, tipo e identità sorgente. Nomi/peso/giorno non sostituiscono un ID. Binding legacy HC/CSV/BLE non è automaticamente risolto dal nuovo binding cloud.

Separare trasporto e metodo: importato non significa misurato; grasso corporeo stimato dal vendor rimane stima vendor. Conservare unità/originali e qualità per metrica, senza dedurre revisioni certe da campi non provati.

Più misure in un giorno restano più misure. L'ultimo peso mantiene la sua data anche se oggi arrivano soltanto passi. Intervalli dei grafici basati sul calendario, non numero di righe. Medie mobili/correzioni sono viste/derivati distinti dall'originale.

## 6. Affidabilità e sicurezza

Rete fuori dalle transazioni. Commit della pagina e cursore atomici, replay idempotente, generazioni impediscono a operazioni vecchie di avanzare un nuovo backfill. Errori non vengono convertiti in zero dati o completamento. Il limite per batch manuale non prova completezza del vendor.

Sessione in AtomicFile/noBackupFilesDir con chiave Keystore separata. Nessun token/cookie nel database, backup, UI state o log. Schermata segreti con masking, niente stato ripristinabile e FLAG_SECURE mentre visibile. Queste protezioni non garantiscono cancellazione delle copie JVM o comportamento OEM.

La chiave test pubblica non autentica il publisher: firma privata/custodia/migrazione sono una decisione del proprietario prima del login reale. Il gate non va rimosso per dimostrare una schermata funzionante.

## 7. Portabilità e prove

Backup v2 contiene colonne/relazioni delle 19 tabelle dati registrate, con snapshot coerente e ripristino transazionale conservativo. Non comprende preferenze/media/credenziali/checkpoint; richiede schema identico, limite 32 MiB decifrati. V1 parziale solo su archivio salute vuoto. Nessuna disinstallazione basata su trasferimento completo non verificato.

Test Jupiter, Room/SQLite nativo e Compose sotto Robolectric verificano contratti, migrazioni, UI e rollback. Non sostituiscono device Redmi, account Xiaomi, BLE, TLS remoto, hardware Keystore o trasferimento tra telefoni. I risultati effettivi/SHA sono nel registro.

Estrarre nuovi moduli Gradle solo per un problema misurato. Nessuna infrastruttura server soltanto perché una feature potrebbe usarla.
