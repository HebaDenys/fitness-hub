# Xiaomi S400 — Connettore integrato, storico e dati nuovi

**Decisione aggiornata:** 6 ottobre 2026.  
**Piano completo:** [MASTER_PLAN.md](../MASTER_PLAN.md), workstream `FH-XIA-*`.  
**Prossima attività:** [PROGRESS.md](../PROGRESS.md).

## 1. Decisione del proprietario

Il flusso principale da costruire è:

```text
Bilancia -> Xiaomi Home -> servizi Xiaomi
                              |
                              v
                 connettore dentro Fitness Hub
                              |
                       database sul telefono
                              |
                storico / grafici / analytics / export
                              |
                 Health Connect, solo tipi compatibili
```

Un solo APK nostro, nessun root, backend/server o script esterno. Xiaomi Home continua a funzionare normalmente. L'accesso ai servizi Xiaomi serve a importare le informazioni già presenti e le nuove misurazioni; quanto salvato rimane disponibile offline.

**Questa integrazione non è ancora implementata nella 0.3.1.** Il progetto ha un importer CSV e un ricevitore BLE sperimentale: non vanno presentati come login o sincronizzazione Xiaomi Cloud.

## 2. Perché cambia la strategia

Il CSV manuale non soddisfa il requisito di sincronizzazione automatica. Il BLE non recupera lo storico vendor e può richiedere formule locali per valori non restituiti dal dispositivo. Leggere i file privati di Xiaomi Home non è il percorso previsto su un telefono non rooted.

La decisione sostituisce esplicitamente la precedente scelta «storico CSV + live BLE». Local-first non vieta servizi del produttore autorizzati dall'utente: vieta rendere obbligatorio un nostro backend e perdere il controllo dell'archivio locale.

## 3. Riferimento tecnico

[SmartScaleConnect](https://github.com/AlexxIT/SmartScaleConnect) documenta lettura Xiaomi Home e supporto per S400 EU, con differenze tra regioni/modelli. [Client](https://github.com/AlexxIT/SmartScaleConnect/blob/master/pkg/xiaomi/client.go), [autenticazione](https://github.com/AlexxIT/SmartScaleConnect/blob/master/pkg/xiaomi/auth.go), [licenza MIT](https://github.com/AlexxIT/SmartScaleConnect/blob/master/LICENSE).

Prima di adattare codice fissare il commit upstream, verificare licenza/attribuzione e protocollo. Non incorporare una CLI da far configurare a mano all'utente. La strada iniziale è un adattatore Kotlin isolato con test di contratto; eventuali altre opzioni devono comunque produrre lo stesso flusso in un APK.

Il modello riferito dall'utente come S400/S400 Pro, la regione account e la companion della band devono essere confermati. Il nome commerciale non prova la compatibilità di tutti i modelli. Non scegliere la regione in base alla posizione geografica del telefono.

## 4. Flusso utente richiesto

1. Aprire Fonti -> Xiaomi e leggere quali connessioni/dati verranno usati.
2. Autenticarsi attraverso un flusso realmente supportato; completare normalmente eventuali verifiche Xiaomi.
3. Selezionare regione, dispositivo e proprio subject/profilo accessibile.
4. Vedere anteprima, intervallo storico ottenibile e capacità.
5. Importare lo storico paginato con progresso, annullamento e ripresa.
6. Sincronizzare le nuove misurazioni manualmente, all'apertura e periodicamente nei limiti Android/vendor.
7. Consultare localmente valori, campi estesi, unità, provenienza e qualità.
8. Abilitare separatamente l'export dei tipi compatibili a Health Connect.
9. Disconnettere l'account senza perdere silenziosamente i dati già importati.

Niente credenziali in chat o GitHub. Niente CAPTCHA/2FA aggirati e niente endpoint, OAuth o QR dichiarati supportati senza verifica.

## 5. Persona e completezza

Una bilancia condivisa non rende tutti i profili automaticamente accessibili da un account. Importare esclusivamente quelli autorizzati e selezionati. Il binding deve usare un'identità persistente, non soltanto il nome o un filtro sul singolo file.

Conservare tutti i campi sanitari disponibili, anche quelli non ancora tipizzati, con semantica/unità note o marcate come sconosciute. Non inventare unità e non scambiare massa muscolare con massa magra. Una stima Xiaomi importata resta una stima vendor, non un risultato clinico o una formula Fitness Hub.

L'indicatore di completezza deve descrivere l'intervallo realmente letto e i campi disponibili: nessuna promessa di «tutto» se l'endpoint restituisce un sottoinsieme.

## 6. Affidabilità e privacy

Sessione protetta, password non persistita se evitabile, segreti esclusi da backup/telemetria/log. Endpoint e redirect autorizzati, HTTPS verificato, risposte limitate, timeout/cancel e errori sanitizzati. Le policy di Auto Backup e firma privata vanno risolte prima del normale uso con credenziali sensibili.

Il connettore è read-only verso le misurazioni Xiaomi. Page commit e checkpoint coerenti; pagine ripetute e revisioni gestite; disconnessione cancella sessione e job. Una pagina assente durante un errore non prova che il vendor abbia cancellato i record.

## 7. CSV e BLE esistenti

Mantenerli come funzionalità secondarie compatibili con l'archivio, non ripiegarvi di nascosto al posto del connettore richiesto.

- CSV: import/migrazione; dati già presenti da riconciliare con le copie cloud senza distruggerli.
- BLE: sperimentale; firmware, bindkey, permessi e attribuzione della persona richiedono test hardware. Niente formule locali al posto dei dati Xiaomi disponibili.
- Rimozione di una connessione/chiave non cancella lo storico senza una scelta esplicita.

## 8. Gate di completamento

Il connettore non è VERIFIED finché login sul dispositivo, selezione del profilo, import storico, nuova pesata, ri-sync idempotente, correzioni e gestione degli errori non sono stati provati. Distinguere unit test, test Room, test del protocollo e prova reale dell'account/hardware.

Riferimenti: task `FH-XIA-01..14`, `FH-DATA-01..04`, `FH-REC-01..08`, `FH-HC-11..12` e scenari ACC-02..11 del master. Il piano non introduce una garanzia di disponibilità permanente delle API non ufficiali Xiaomi.
