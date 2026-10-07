# Fitness Hub — Stato operativo e passaggio di consegne

**Aggiornamento:** 7 ottobre 2026. Piano vincolante: [MASTER_PLAN.md](MASTER_PLAN.md).  
**Branch operativo:** `main`.  
**HEAD runtime verificato:** `c184b63261198bbc4a3b7c9d734f078c21adfeb3` — 0.3.6, versionCode 8, database 7.  
**CI finale:** [37637256417](https://github.com/HebaDenys/fitness-hub/actions/runs/37637256417), job `112846609531`, conclusione **success**.  
**Commit successivo:** adozione schema/documentazione soltanto; non cambia l'APK verificato.

## 1. Consolidamento branch

La PR #2 `feat/canonical-body-metrics-resolver` è stata verificata e mergiata in `main` con merge commit `f4c7c185a972242c5067ecbbdb9104d3935c4fc0`. La CI del merge, run [37635000450](https://github.com/HebaDenys/fitness-hub/actions/runs/37635000450), è passata incluse firma/checksum/release.

Il vecchio branch `feat/phase-1-2-foundation-health-connect` era già confluito storicamente tramite PR #1. Non è stato ri-mergiato perché oggi diverge dal progetto corrente e contiene storia/file obsoleti; un secondo merge non è necessario per conservare il lavoro già integrato.

Il proprietario ha autorizzato la prosecuzione tecnica direttamente su `main`. Nessun force-push. Il login Xiaomi reale resta bloccato da `AwaitingPrivateSigning` finché firma privata, custodia e migrazione non vengono approvate esplicitamente.

## 2. Dati corporei canonici consolidati

`CanonicalBodyMetricResolver` usa la **recenza come regola primaria tra eventi distinti**. Una misura Xiaomi vecchia non può battere una Health Connect più recente solo per priorità sorgente. La priorità sorgente/metodo interviene soltanto per lo stesso `eventKey` esplicito o come tie-break a timestamp uguale.

`CanonicalBodyRepository` alimenta Body, Dashboard e Insights/export. Originali non vengono modificati o cancellati. Più pesate reali nello stesso giorno restano eventi distinti. Versioni Xiaomi con stesso eventKey possono collassare nella vista canonica ma restano tutte nell'archivio sorgente.

Peso e grasso hanno ultimo valore e data indipendenti. I range UI usano giorni di calendario, non il numero di righe sparse. La cache giornaliera `daily_health` resta compatibile con installazioni precedenti.

## 3. Incremento 0.3.6 — record corporei Health Connect esatti

Schema 7 aggiunge due tabelle additive:

- `hc_weight_samples`
- `hc_body_fat_samples`

Per WeightRecord e BodyFatRecord vengono conservati `metadata.id`, timestamp originale, valore e package origine. Non vengono inventati ID: campioni senza identità stabile possono continuare a contribuire alla cache giornaliera ma non vengono trasformati in record esatti fittizi.

Il resolver canonico usa i record esatti quando presenti e usa `daily_health` soltanto come fallback per quella metrica/data finché un vecchio archivio non viene risincronizzato. La cache giornaliera originale rimane intatta.

Un full sync sostituisce atomicamente i record esatti nell'intervallo letto; sync incrementali fanno upsert per record ID senza cancellare l'intera serie. Il backup v2 registra ora **21 tabelle dominio** e include le nuove tabelle; resta stesso-schema e conservativo sui conflitti.

## 4. Verifica effettiva

### Tentativo iniziale

Commit `c6fad3430ad30e7c291164bcb0a4e187186f5046`, run [37636543743](https://github.com/HebaDenys/fitness-hub/actions/runs/37636543743):

- compilazione/lint arrivati alla suite;
- **391 test, 1 failure**;
- failure: `DatabaseBackupIdentityTest.forgedOwnerInsideSnapshotRollsBackWholeRestore`;
- causa osservata: il test assumeva `xiaomi_snapshots` come ultima tabella del backup. Le due nuove tabelle rendevano falsa l'assunzione posizionale;
- schema/release correttamente saltati; nessun gate disabilitato.

Correzione: `c184b63261198bbc4a3b7c9d734f078c21adfeb3` cerca `xiaomi_snapshots` per nome, mantenendo la stessa prova anti-tampering.

### CI finale 0.3.6

Run [37637256417](https://github.com/HebaDenys/fitness-hub/actions/runs/37637256417):

- `./gradlew testDebugUnitTest lintDebug assembleDebug --stacktrace`: **BUILD SUCCESSFUL**;
- **391 test app / 43 suite, 0 failures, 0 errors, 0 skipped**;
- **11 test Python CI/privacy passati**;
- lint e assembleDebug passati;
- certificato APK confrontato con la chiave test esistente: passato;
- checksum passato;
- pubblicazione rolling test riuscita.

Schema KSP 7: blob `4499932af47e58d3eea06aadf68a8f64b0a11f79`, identityHash `b308e71525b3cb0de3b34822fcaa68e7`. Il file `app/schemas/io.github.hebadenys.fitnesshub.core.database.HealthDatabase/7.json` viene adottato byte-per-byte da quell'output CI.

## 5. APK verificato

**FitnessHub-v0.3.6-debug.apk**, versionCode **8**, package `io.github.hebadenys.fitnesshub`, database **7**.

[APK diretto](https://github.com/HebaDenys/fitness-hub/releases/download/test-latest/FitnessHub-v0.3.6-debug.apk) — [Checksum](https://github.com/HebaDenys/fitness-hub/releases/download/test-latest/FitnessHub-v0.3.6-debug.apk.sha256)

SHA-256: `917562343a8caff3ee80df321966ff8b6cca289c5e0ff0820a8ae41a8792e219`.  
Dimensione: **143578608 byte**.  
Release `test-latest` punta al runtime commit `c184b63261198bbc4a3b7c9d734f078c21adfeb3`.

## 6. Limiti attuali

- Il login Xiaomi reale è ancora disabilitato. CAPTCHA/2FA, redirect STS e compatibilità S400 Pro/account/regione/firmware non sono testati dal vivo.
- Health Connect resta a 11 tipi; il nuovo lavoro migliora identità/timestamp di peso e body-fat, non estende ancora tutte le categorie.
- La Changes API non ha ancora checkpoint/tombstone per tipo: una cancellazione non identificata può ancora richiedere full resync.
- Deduplica cross-source è intenzionalmente conservativa: non fonde valori soltanto perché simili nel tempo/peso.
- Backup v2 include righe DB e relazioni, non preferenze/media/credenziali; restore richiede lo stesso schema. Trasferimento fisico resta da provare.
- Nessuna prova Redmi/S400/Mi Band, hardware Keystore o trasferimento tra telefoni in questa sessione.

## 7. Prossimo passo

Il prossimo blocco pronto è soprattutto **FH-UX-02/03/04/06 + FH-HC-01/13**:

1. trasformare Impostazioni in un centro Sorgenti più leggibile: Health Connect, Xiaomi, companion/Google Fit tramite HC, stato, capacità, ultima sync, permessi e azioni;
2. rifinire gerarchia visiva, card, spacing, tipografia, dark/light, schermi piccoli e accessibilità;
3. aggiungere diagnostica read-only per tipi/origini HC osservati e test connessione senza promettere dati che l'app sorgente non espone;
4. mantenere Xiaomi gated fino alla decisione FH-SAFE-03;
5. dopo la UX, proseguire su HC per tipo/checkpoint e riconciliazione copie.

Riferimenti: [MASTER_PLAN.md](MASTER_PLAN.md), [ARCHITECTURE.md](ARCHITECTURE.md), [backup-format-v2.md](backup-format-v2.md).
