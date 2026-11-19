# Monolith

App Android nativa (Kotlin + Jetpack Compose, Material 3) in bianco, nero e grigi.
Agenda, chat private e di gruppo, posizione in tempo reale e chiamate vocali con gli amici, **senza un server da gestire**.

## Funzioni

- **Agenda**: eventi con titolo, descrizione, data, ora, note e promemoria. L'emoji scelta diventa l'icona della notifica. Puoi invitare gli amici: ricevono l'invito e rispondono "Partecipo" / "Non posso".
- **Clubhouse**: chat private e gruppi, foto (galleria o fotocamera), trilli, eventi e posizione condivisi in chat, spunte di consegna/lettura, "sta scrivendo", risposta rapida dalla notifica.
- **Mappa**: posizione live con gli amici scelti, per 15 min / 1 h / 8 h / 24 h, mappa minimal in scala di grigi.
- **Amici**: ricerca per username, richiesta di amicizia da accettare.
- **Chiamate vocali** peer-to-peer.
- Tema chiaro/scuro, animazioni Material (transizioni condivise, molle, micro-interazioni).

## Come funziona senza server

| Livello | Tecnologia |
|---|---|
| Identità | chiave secp256k1 generata sul telefono (backup `nsec` dalle impostazioni) |
| Cifratura | NIP-44 v2 (ECDH + HKDF + ChaCha20 + HMAC), buste tipo NIP-59 che nascondono il mittente |
| Recapito offline e segnalazione | relay pubblici Nostr usati solo come buca delle lettere cifrata |
| Tempo reale | **WebRTC** data channel diretto tra i telefoni (posizione, foto, digitazione) e audio (chiamate) |
| Sempre online | servizio in primo piano `remoteMessaging` che tiene aperta la connessione |

Nessun dato in chiaro lascia il telefono. I relay sono intercambiabili dalle impostazioni; si può aggiungere un server TURN opzionale per reti molto restrittive.

## Build

Ogni push su `main` compila l'APK firmato con GitHub Actions e lo pubblica nelle *Releases*.

```
./gradlew assembleRelease
```
