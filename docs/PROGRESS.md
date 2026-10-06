# Fitness Hub — Stato operativo

Aggiornamento: 6 ottobre 2026. Piano vincolante: MASTER_PLAN.md.
HEAD iniziale: `1fce6ed88fa33619c54229214eecfbceb5555450`.

## Attività corrente

**FH-XIA-02/03/04 + SAFE — trasporto, autenticazione e sessione.**

Implementazione 0.3.4 preparata; **CI del nuovo commit da verificare**. La precedente versione verificata è 0.3.3, SHA software `55ae7c0a9c3a5db55f81f6343633897f4b8b285b`, run 37525676439 (297 test app + 11 CI/privacy, build/lint/firma/release passati).

Prima azione: verificare test/lint/build del commit software 0.3.4, correggere eventuali errori senza sopprimere controlli, poi registrare SHA/run/risultati e release reali. Non dichiarare già passati i nuovi test.

## Nuovo incremento

- `XiaomiAccess`: reason code statici, DTO redatti, gate rete e policy URL/headers stretta.
- `XiaomiHttpsTransport`: HTTPS verificato, niente redirect automatici/cache/logger, IO limitato e cancellabile.
- `XiaomiWireCrypto`: protocollo nonce/signatura/RC4-drop1024 compatibile con upstream verificato, distinto dalla cifratura locale.
- `XiaomiAuthentication`: scambio a tre passi; challenge espliciti senza aggiramento; password/passToken non persistiti.
- `XiaomiSession`, codec e protected store: AES-GCM/AndroidKeyStore in noBackupFilesDir, limite durata, invalidazione e cancellazione.
- `XiaomiAuthenticatedPageSource` e `XiaomiCloudClient`: collegamento al reader/committer Room già esistenti; logout cancella operazioni correnti/in coda senza toccare i record.
- Test con risposte sintetiche, connessioni HTTPS fake, AES reale con chiave di test e Room/SQLite nativo; nessuna credenziale/dato sanitario reale.

**Gate effettivo:** `XiaomiCloudRuntime.create` usa AwaitingPrivateSigning; non può eseguire login reali. Nessuna UI o flag permette di aggirarlo. Package/chiave test non modificati; nuova versione APK 0.3.4/versionCode 6, database 6 invariato.

## Prossimo blocco dopo la verifica

**FH-XIA-05/06/10: onboarding e selezione esplicita regione/modello/device/subject, con stato delle sorgenti e integrazione del client.** Riutilizzare i nuovi componenti, non riscriverli o tornare al CSV. UI e test sintetici possono avanzare prima delle credenziali reali.

Prima del login reale, FH-SAFE-03 richiede decisione esplicita su chiave privata, custodia e migrazione. Non cambiare firma/package o chiedere password in chat. Completamento dei challenge deve seguire il normale flusso vendor; oggi è solo rilevato. Nessun nuovo worker periodico Xiaomi è registrato.

## Registro sintetico

| ID | Stato e prova |
|---|---|
| FH-XIA-01 | VERIFIED protocollo/fixture nella 0.3.2; committer reale aggiunto in 0.3.3. |
| FH-XIA-02/03/04 | IMPLEMENTED — nuovo incremento da validare in CI; nessun login fisico. Challenge continuation/UI/hardware restano. |
| FH-DATA-01/02 | IN_PROGRESS — binding/snapshot cloud nativo verificato nella 0.3.3; ownership legacy/resolver universale/rebind restano. |
| FH-DATA-04 | VERIFIED — migrazioni v1–v5→v6 con Room/SQLite nativo; upgrade fisico ancora mancante. |
| FH-SAFE-01/04/05 | IN_PROGRESS — logging/trasporto/sessione e segreti backup; audit completo di tutte le feature non concluso. |
| FH-SAFE-02 | AWAITING_DEVICE — policy backup OS testate in XML, non comportamento OEM. |
| FH-SAFE-03 | TODO — firma privata/custodia/migrazione da approvare; gate bloccante solo per login reali. |
| FH-PORT-01/02/03/04/06 | IN_PROGRESS — backup DB v2 e IO verificati nella 0.3.3; preferenze/media/cross-schema/prova fisica restano. |
| FH-QA-02 | IN_PROGRESS — suite Room e nuovi test rete sintetici; nessuna validazione hardware sostitutiva. |
| FH-UX-05 | TODO — ES completo; traduzioni parziali conservate in docs/localization. |

Gli ID non elencati mantengono lo stato del master o TODO. Non usare numero di classi/test come percentuale prodotto.

## Verifiche di questa sessione

Letture GitHub reali: HEAD, AGENTS, progress, master pertinente, README, architettura, codice/sessione/Room, issue aperte e upstream. Upstream SmartScaleConnect confermato su `a9e5c04f1079b65d456c8a5fd296775a1ef29e8f`, MIT già attribuita nell'APK.

Locale: eseguito generatore indipendente Go con crypto/rc4, sha1, sha256 per il golden vector (output riportato nei test). Il tentativo iniziale Python richiedeva un modulo non installato; non usato come prova. Il container non risolve github.com e non ha un toolchain Android completo: nessuna build Android locale dichiarata. Build/test reali demandati alla CI, esito da registrare.

## Evidenze precedenti da conservare

0.3.3: SHA `55ae7c0a9c3a5db55f81f6343633897f4b8b285b`, run https://github.com/HebaDenys/fitness-hub/actions/runs/37525676439, 297 test app/29 suite e 11 test Python passati. Schema KSP 6 adottato nel commit `1fce6ed`; blob `f9436689375524da4623aaba7a868e52c317a875`, identityHash `174b9871badd38370879c18980481f46`.

Backup v2 comprende tutte le colonne di 19 tabelle dati e relazioni; merge transazionale, righe identiche ignorate e conflitto annulla tutto. Nessun backup di preferenze/media/credenziali/cursori; massimo 32 MiB decifrati e schema identico. Backup v1 parziale solo su archivio salute vuoto. Non consigliare disinstallazioni basandosi su un trasferimento completo non verificato.

## Limiti invariati

HC: 11 tipi, finestra app 30/365, fasi sonno e write-back incompleti. Nessuna nuova sincronizzazione universale. Xiaomi: compatibilità account/regione/S400 Pro/firmware ancora non verificata; profilo/device binding iniziale singolo e immutabile. Resolver comune UI/analytics e deduplica semantica cross-source restano da sviluppare.

Nessun test su Redmi/S400/Mi Band, TLS remoto reale, Keystore hardware, challenge reale o trasferimento fisico fra telefoni. Una suite verde non rimuove questi limiti. Nessuna licenza finale scelta, spesa introdotta o esecuzione futura schedulata.

Riferimenti: connectors/xiaomi-auth.md, connectors/xiaomi-storage.md, backup-format-v2.md, releases/0.3.4.md.
