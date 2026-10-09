# Verifica sul telefono (Windows / PowerShell)

1. Installa Android SDK Platform-Tools dal sito Android ufficiale, oppure usa quelli inclusi in Android Studio: https://developer.android.com/tools/releases/platform-tools .
2. Sul telefono abilita Opzioni sviluppatore e Debug USB, collega un cavo dati e approva la chiave RSA solo del tuo computer. Questa autorizzazione dà al computer accesso di debugging; revocala quando non serve.
3. Dalla cartella platform-tools esegui `./adb.exe devices`. Deve apparire un device autorizzato. Non pubblicare il numero seriale.
4. Installa l’APK verificata dal telefono oppure con `./adb.exe install "percorso\\FitnessHub-release.apk"`. Una firma diversa dalla TEST può richiedere disinstallazione manuale della vecchia app: elimina i suoi dati locali. Il proprietario ha dichiarato questi dati di test sacrificabili; non viene eseguita alcuna cancellazione automatica.
5. Apri Impostazioni → Xiaomi Home, seleziona la regione configurata in Xiaomi Home, inserisci le credenziali sul telefono e collega. Cerca il modello, conferma persona/bilancia e importa. Prima conferma osserva timestamp, fonte e valori, non solo conteggio snapshot.
6. Per un crash riproducibile puoi usare `./adb.exe logcat -b crash -d`. Controlla e rimuovi eventuali dati personali prima di condividere estratti. Non catturare password, token, cookie, dump completi o backup. Per errori Xiaomi basta il messaggio statico mostrato a schermo e la fase in cui compare.

La release è intenzionalmente non debuggable: ADB può installarla e leggere diagnostica consentita dal sistema, ma non abilita breakpoints o ispezione della memoria privata. Non rendere debuggable una build usata con credenziali reali. Il debug pubblico resta offline e usa fixture.

Guida ufficiale dispositivo/ADB: https://developer.android.com/studio/run/device e https://developer.android.com/tools/adb .
