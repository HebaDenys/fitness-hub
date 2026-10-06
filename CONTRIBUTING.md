# Contribuire a Fitness Hub

## Prima di iniziare

Leggere [AGENTS.md](AGENTS.md), [piano completo](docs/MASTER_PLAN.md) e [stato operativo](docs/PROGRESS.md). Scegliere un task ID e verificare codice/issue/PR/CI reali. Le attività già previste nel piano sono la direzione approvata: non ripristinare i vecchi divieti di avviare Phase 3 o la strategia CSV obbligatoria.

I contributori esterni devono coordinarsi tramite issue/PR; l'autorizzazione al lavoro diretto su `main` riguarda il flusso del proprietario e dei suoi assistenti autorizzati, non concede accesso di scrittura pubblico.

## Ambiente e verifiche

Alla baseline: JDK 21, Gradle Wrapper 8.13, min SDK 28, target SDK 35 e compile SDK 36. Leggere Gradle/manifest correnti prima di modificare il toolchain.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
python3 -m unittest discover -s ci -p 'test_*.py' -v
```

Per cambiamenti solo documentali verificare link, riferimenti, ID e diff. Non dichiarare eseguiti test Android non eseguiti. Non saltare test/lint su cambiamenti di codice per ottenere un badge verde.

## Principi

- Local-first: nessun backend o account Fitness Hub obbligatorio. Connettori vendor facoltativi approvati, isolati e dichiarati.
- Un solo APK nostro; no root, exploit o app modificate per estrarre dati privati.
- Identità e provenienza per record/metrica. Trasporto import, misura e stima sono concetti separati.
- Dati mancanti nullable/stati espliciti; niente valori finti in produzione.
- Deduplicazione, aggiornamenti e cancellazioni reversibili/documentati; niente fusioni sulla sola somiglianza dei numeri.
- Migrazioni e backup aggiornati insieme ai modelli persistenti; test Room reali oltre ai mock.
- Nessuna credenziale, chiave privata, dato sanitario reale o identificativo personale in fixture/log/repository.
- AI opzionale e revisionabile; rispetto delle API e dei termini del provider.

## Commit e criteri di completamento

Usare commit atomici e descrittivi, per esempio `feat(xiaomi): FH-XIA-01 parse scale history response`. Un incremento deve chiudere un percorso o un prerequisito eseguibile, con error handling e test; una classe stub o un bottone senza effetto non completano un task.

Nella PR o nel registro del lavoro indicare ID, file/commit, test eseguiti, limiti, eventuale prova dispositivo ancora necessaria e prossimo passo. Non confondere unit test con validazione hardware o rilasciabilità in produzione.

## Licenze e diritti

La licenza definitiva del progetto non è ancora stata scelta. La direzione è source-available per uso personale/noncommerciale, con licenza separata per uso commerciale; la definizione precisa deve essere approvata dal proprietario.

Non copiare codice con licenze incompatibili e non applicare automaticamente una nuova licenza al progetto. SmartScaleConnect è un riferimento MIT: mantenere attribuzione e avviso sui file riutilizzati. Dati/immagini Open Food Facts e librerie OCR possono avere condizioni diverse dal codice dell'app.

Prima di integrare contributi esterni destinati al dual licensing definire diritti e accordo appropriato. Un DCO non va considerato automaticamente un CLA di relicensing. Nessun testo di questa guida sostituisce la futura scelta legale.

## Sicurezza

La chiave CI pubblica test-only non è una credenziale personale, ma non deve essere riutilizzata per produzione. Chiavi private di firma e sessioni Xiaomi richiedono un percorso separato e protetto. Segnalazioni di sicurezza non devono contenere segreti o esportazioni sanitarie in issue pubbliche.
