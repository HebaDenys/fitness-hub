# Fitness Hub — Stato operativo

**Aggiornamento:** 6 ottobre 2026.  
**Piano:** [MASTER_PLAN.md](MASTER_PLAN.md), revisione 1.  
**HEAD letto all'inizio:** `be12732813e9d3c3732f9c36ef6e95ded7335257`.  
**Ultimo codice verificato prima di questa sessione:** `b757f8e7c3df84ac444476874e990098154e710a`, APK 0.3.2.  
**Questa consegna in verifica:** persistenza Xiaomi / schema 6 / backup database v2, candidata 0.3.3. Non considerare la nuova CI passata finché non è registrato l'esito effettivo.

## 1. Lavoro corrente

`FH-DATA-01/02`, incremento di persistenza cloud; prerequisiti `FH-DATA-04`, `FH-QA-02` e `FH-PORT-01..04/06`.

Implementata l'associazione persistente di un singolo subject/device Xiaomi alla connessione, archivio di snapshot sanitizzati e checkpoint transazionali. Il reader esistente ora ha un committer Room concreto. Non contiene ancora il trasporto autenticato, login o UI live.

Il percorso di backup nella UI ora usa il formato database v2. Copre tutte le colonne delle 19 tabelle dei dati, conserva ID/relazioni, legge uno snapshot coerente e ripristina transazionalmente con conflitti fail-closed. Esclusi credenziali, preferenze, media e cursori operativi. Il formato precedente resta leggibile e dichiaratamente parziale.

### Verifica da chiudere in questa sessione

1. Eseguire CI sul commit software finale e correggere i problemi effettivi senza disabilitare test/lint.
2. Verificare che i test Jupiter preesistenti e i nuovi test Robolectric/Vintage siano entrambi eseguiti.
3. Acquisire lo schema 6 JSON generato realmente da KSP, tramite artifact/blob CI, e adottarlo con commit revisionato.
4. Verificare APK, checksum, certificato e release del medesimo SHA.
5. Registrare esito e prossimo task senza fingere test Xiaomi/telefono non eseguiti.

## 2. Registro

| ID | Stato | Prove e limiti |
|---|---|---|
| PLAN-R1 | VERIFIED — documentazione | Piano R1 nel commit `81b4f85`; non una funzione applicativa. |
| FH-XIA-01 | VERIFIED — contratti/fixture | `b757f8e`, CI 37518253962. Protocollo Kotlin con upstream MIT annotato; nessuna chiamata reale. |
| FH-SAFE-01 | IN_PROGRESS | Audit iniziale e logging allowlist nella 0.3.2; resto dell'audit e trasporti da completare. |
| FH-SAFE-02 | AWAITING_DEVICE | Regole backup OS esplicite/test XML; comportamento OEM non verificato. |
| FH-SAFE-03 | TODO — decisione prima dei login | Firma privata/custodia/migrazione richiedono scelta del proprietario. Nessuna chiave cambiata. |
| FH-DATA-01 | IN_PROGRESS — nuova persistenza | Binding immutabile regione/login UID/subject/device e profilo locale. Resta migrazione identità legacy e UI di rebind esplicito. |
| FH-DATA-02 | IN_PROGRESS — nuova persistenza | Snapshot con raw/unità/metodo/qualità per metrica. Non è ancora un resolver globale o una revisione vendor dimostrata. |
| FH-DATA-04 / FH-QA-02 | IN_PROGRESS — test aggiunti | Migrazioni v1–v5→v6, compilato Room/SQLite nativo sotto Robolectric; attendere risultati CI effettivi. |
| FH-PORT-01..04/06 | IN_PROGRESS — incremento database | Backup v2, registro tabelle, snapshot/transazioni, merge conservativo, IO limitato fuori UI. Non include media/preferenze; prove fisiche e conflitti avanzati restano. |

Gli altri task rimangono nello stato del master (`TODO` se non registrati). Un risultato verificato per un sottosistema non completa l'intero prodotto.

## 3. File e contratti

- `core/xiaomi/storage/XiaomiArchiveEntities.kt`: identità, binding, snapshot e checkpoint; stringificazione redatta.
- `core/xiaomi/RoomXiaomiArchive.kt`: associazione, ripresa, replay e commit pagina atomico.
- `core/xiaomi/XiaomiSnapshotCodec.kt`, `XiaomiArchiveIntegrity.kt`: envelope, hash e controllo identità/integrità nel recupero.
- `core/database/XiaomiArchiveMigration.kt`: migrazione additiva 5→6.
- `core/backup/DatabaseBackupService.kt`: formato nuovo collegato a Insights, con adattatore del formato vecchio.
- Helper backup per IO, limiti JSON e relazioni; nuova UI file/passphrase e stringhe EN/IT/ES.
- Test Room nativi: migrazioni, riapertura, replay concorrente, rollback/checkpoint, confronto di tutte le colonne e restore con conflitti.
- `ci/export_room_schema.py`: espone schema KSP come blob senza modificare branch; il suo risultato deve essere revisionato e committato.

[Archivio Xiaomi](connectors/xiaomi-storage.md) — [Formato backup](backup-format-v2.md) — [Note 0.3.3](releases/0.3.3.md).

## 4. Limiti e gate

- Il collegamento Xiaomi live e il login non sono presenti. Nessuna credenziale reale richiesta o usata.
- Un solo binding Xiaomi immutabile per installazione in questo incremento. Non rinominare connessioni per aggirare la separazione delle persone.
- Il binding nuovo non assegna retroattivamente una persona ai vecchi dati CSV/BLE/HC. Non dichiarare risolta la riconciliazione universale.
- Hash dei contenuti significa snapshot identico, non identità vendor dimostrata. Dati cambiati conservati separatamente, non ordinati automaticamente come revisioni.
- Backup v2: tabelle dei dati, stesso schema DB, massimo 32 MiB decifrati, conflitti interrompono tutto. Nessun replace distruttivo. V1 resta parziale e non garantito idempotente.
- Preferenze, allegati e segreti esclusi; backup OS resta disattivato. Nessun consiglio di disinstallazione alla cieca.
- HC non modificato da questa consegna: 11 tipi, sonno/storico/write-back ancora incompleti.
- Nessuna prova fisica Redmi/S400/Mi Band; Robolectric con SQLite nativo non è un telefono reale.
- Chiave pubblica TEST-ONLY conservata; firma privata e migrazione da approvare prima dei login sensibili. Nessuna scelta di licenza definitiva.

## 5. Prossimo passo dopo verifica

Procedere con i prerequisiti del trasporto e della sessione Xiaomi (`FH-XIA-02/03/04`, SAFE), usando server simulati/fixture e senza chiedere password in chat. Prima leggere il registro finale: una regressione o uno schema non adottato ha precedenza. Collegare successivamente il selettore del subject e i dati persistiti alla UI/canonicalizzazione; il gate firma resta per l'uso reale delle credenziali.

## 6. Evidenze precedenti

La CI software 0.3.2 [37518253962](https://github.com/HebaDenys/fitness-hub/actions/runs/37518253962), SHA `b757f8e`, aveva 261 test app/21 suite e 11 test Python passati, build/lint/firma/pubblicazione verdi. Questo riferimento NON attesta i nuovi test.

La verifica Android completa non è stata eseguita localmente in questa sessione: la validazione del nuovo toolchain/Room viene demandata alla CI effettiva. Non trasformare un'ispezione del codice in una dichiarazione di test eseguito.
