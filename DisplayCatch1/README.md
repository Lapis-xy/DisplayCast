# AppBroadcast — README

Breve descrizione
-----------------
AppBroadcast è un'applicazione semplice per inviare messaggi di testo da una macchina di controllo ("Sender") a uno o più schermi/TV ("Listener") nella stessa rete locale. È pensata per digital signage leggero e annunci testuali.

Principali funzionalità
-----------------------
- Scoperta automatica dei dispositivi tramite UDP broadcast.
- Comunicazione affidabile via TCP per l'invio dei messaggi.
- Interfaccia grafica per inviare messaggi a tutti o a singoli dispositivi.
- Comando remoto `CMD:HIDE` per nascondere il messaggio sui client.

# AppBroadcast

Una guida completa e pronta per GitHub per il progetto di digital signage leggero.

## Descrizione
AppBroadcast consente di inviare messaggi di testo in tempo reale da una macchina di controllo ("Sender") a uno o più schermi/TV ("Listener") presenti nella stessa rete locale. È adatto per annunci, avvisi e messaggi informativi su display remoti.

## Screenshot
_(Aggiungi screenshot della GUI del `Sender` nella directory `assets/` e fai riferimento alle immagini.)_

## Caratteristiche principali
- Scoperta automatica dei dispositivi tramite UDP broadcast.
- Connessioni TCP per trasmissione affidabile dei messaggi.
- GUI per gestione dispositivi, log e invio contenuti a singoli o a tutti i display.
- Supporto per comandi remoti semplici (es. `CMD:HIDE`).

## Requisiti
- Java Runtime Environment (JRE) 8 o superiore su tutti i dispositivi.
- (Opzionale) Java Development Kit (JDK) per compilare i sorgenti.
- Rete locale (gli host devono essere nello stesso segmento / router per il broadcast UDP).

## Struttura della repository
- `Host/` — sorgenti del `Sender` e del `Server`.
- `Agent/` — sorgenti del `Listener` e del `Client`.
- `Host/Host.bat` — script Windows che compila, copia e avvia il `Sender`.
- `Agent/Agente.bat` — script Windows che compila, copia, registra l'avvio automatico e avvia il `Listener`.
- `DOCUMENTATION.txt` — documentazione tecnica dettagliata.
- `GUIDA_NON_TECH.txt` — guida semplice per utenti non tecnici.
- `LICENSE` — testo della licenza (MIT di default).
- `.gitignore` — file per escludere file generati.

## Installazione rapida (Windows)
1) Sul computer di controllo (Sender):

```powershell
cd "C:\Users\Utente\OneDrive - NIK.GE SRL\Desktop\appBr\Host"
double-click Host.bat
```

2) Su ogni display/TV (Listener):

```powershell
cd "C:\Users\Utente\OneDrive - NIK.GE SRL\Desktop\appBr\Agent"
double-click Agente.bat
```

# DisplayCatch

DisplayCatch è una piccola applicazione Java per trasmettere messaggi a schermi/TV in rete locale.

Ruoli
- **Sender** (cartella `Host`) — eseguito dalla macchina di controllo: avvia un server TCP (porta 5555), invia periodicamente broadcast UDP per farsi trovare e fornisce un'interfaccia per inviare messaggi ai display.
- **Listener** (cartella `Agent`) — eseguito sui display/TV: ascolta i broadcast, si connette al Sender, e mostra i messaggi ricevuti in fullscreen.

Principali script Windows
- `Host\Host.bat` — compila (`Server.java`, `Sender.java`), installa i `.class` in `%APPDATA%\DisplayCatch\sender`, copia `Host.bat` e `app.ico` in quella cartella e crea un collegamento desktop `DisplayCatch.lnk` con icona personalizzata.
- `Agent\InstallAgente.bat` — compila i sorgenti Agent, sposta i `.class` in `%APPDATA%\DisplayCatch\Listener`, sposta `Agente.bat` nella cartella `Startup` per avvio automatico.
- `Agent\Agente.bat` — avvia il `Listener` dalla cartella `%APPDATA%\DisplayCatch\Listener`.

Icona
- Se vuoi usare un'icona personalizzata, metti una PNG nella root del progetto (es. `app.png`) e usa lo script `tools\convert-png-to-ico.ps1` per generare `Host\app.ico` (lo script multitaglia produce 256/128/64/48/32/16).

Esempi di comandi (PowerShell)
```powershell
# converti PNG -> ICO (da workspace root)
pwsh -ExecutionPolicy Bypass -File .\tools\convert-png-to-ico.ps1 -InFile .\app.png -Output .\Host\app.ico

# esegui Host (compila e crea shortcut)
cd Host
.\Host.bat

# installa Agent (compila e sposta .class + registra in Startup)
cd ..\Agent
.\InstallAgente.bat
```

Build manuale (javac)
- Per compilare manualmente (richiede JDK):
```powershell
javac -encoding UTF-8 -d . Host\Server.java Host\Sender.java
javac -encoding UTF-8 -d . Agent\Client.java Agent\Listener.java
```

Struttura del progetto

- `Host/` — Sender, Server, Host.bat
- `Agent/` — Listener, Client, script Agent
- `tools/` — script utilità (es. conversione PNG→ICO)
- `README.md`, `USAGE.txt`, `LICENSE`

Note
- Questo progetto è pensato per Windows (i batch e lo script PowerShell). Alcune funzionalità (creazione collegamento, copia in `%APPDATA%`) richiedono permessi utente normali.
- Il nome dell'app è `DisplayCatch` e le cartelle in `%APPDATA%` vengono create come `%APPDATA%\DisplayCatch\...`.

Licenza
- Vedi il file `LICENSE` incluso nel repository.

Contatti
- Se trovi problemi, crea un'issue nel repository con i dettagli e uno screenshot.

---
Piccola guida rapida: `Host.bat` compila e installa il sender, copia l'icona (se presente) e crea il collegamento `DisplayCatch.lnk` sul desktop; `InstallAgente.bat` prepara i listener sui display.

## Esecuzione manuale (da riga di comando)
Per utenti avanzati o per debug:

```powershell
cd "C:\Users\Utente\OneDrive - NIK.GE SRL\Desktop\appBr"
javac Host\*.java
javac Agent\*.java

# Avvia il Sender sulla macchina di controllo
java -cp Host Sender

# Avvia il Listener su un display
java -cp Agent Listener
```

## Costruire un JAR eseguibile (opzionale)
Esempio rapido per creare un jar eseguibile per il `Sender`:

```powershell
cd Host
javac *.java
jar cfe Sender.jar Sender *.class
# copia Sender.jar dove vuoi e avvia con:
java -jar Sender.jar
```

## Rete e porte usate
- UDP broadcast: porta 4445 — il `Sender` manda `PORT:5555` per farsi rilevare.
- TCP: porta 5555 — il `Server` ascolta le connessioni dei `Listener`.

## Protocollo semplice
- I `Listener` rispondono al broadcast e si connettono via TCP al `Server`.
- All'avvio il `Listener` invia `REGISTER:<clientId>` per identificarsi.
- Il `Server` mantiene una mappa clientId → socket e inoltra messaggi.
- Comandi speciali: `CMD:HIDE` (fa sparire la visualizzazione sul `Listener`).

## Esempi di utilizzo
- Inviare un messaggio a tutti i display: scrivi il testo nella GUI del `Sender` e clicca `Invia a Tutti`.
- Inviare a uno schermo specifico: selezionalo nella lista e clicca invia.
- Nascondere tutti i messaggi: `Termina Trasmissione`.

## Risoluzione problemi
- Messaggi: "Impossibile connettersi" sul `Listener` — controlla che il `Sender` sia avviato e che le porte non siano bloccate dal firewall.
- Nessun dispositivo nella lista del `Sender` — assicurati che i `Listener` siano sulla stessa rete (stesso router) e che Java sia avviato.
- Broadcast non attraversa subnet — verifica la topologia di rete o posiziona Sender e Listener nello stesso segmento.

## FAQ
Q: Posso usare questa app su reti diverse?  
A: Di default no, il broadcast è limitato alla stessa subnet. Puoi modificare il codice per usare multicast o un server centrale visibile a tutte le subnet.

Q: È sicuro?  
A: Non c'è autenticazione. È pensata per reti affidabili. Per scenari pubblici aggiungere autenticazione/criptografia.

## Come contribuire
1. Fork del repository.
2. Crea un branch `feature/tuo-feature`.
3. Apri una pull request descrivendo la modifica.

Linee guida per i commit
- Messaggi chiari in inglese o italiano.
- Un commit per una funzionalità o fix.

## Licenza
Questo progetto è rilasciato sotto la licenza MIT. Vedi il file `LICENSE` per il testo completo.

## Contatti
Apri un issue in questo repository per bug, richieste o domande. Se vuoi che pubblichi il repository per te, dimmi il nome del repo GitHub e il tuo username.

## Appendice: file importanti
- `Host/Server.java` — gestione connessioni e inoltro messaggi.
- `Host/Sender.java` — interfaccia grafica e broadcast UDP.
- `Agent/Listener.java` — ricezione broadcast, connessione TCP e visualizzazione.
- `Agent/Client.java` — client TCP che riceve i messaggi.

