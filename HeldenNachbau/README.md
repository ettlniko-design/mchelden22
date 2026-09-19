# HeldenNachbau

Ein moeglichst originalgetreuer Nachbau des Paper-Plugins **MinecraftHelden**
(Season 2, "at.netheritehuhn.minecrafthelden"), rekonstruiert aus der
hochgeladenen JAR-Datei per Konstantenpool-Analyse (kein voller Decompiler
verfuegbar - daher exakte Feld-/Methodennamen und Strings, aber nicht jede
Zeile Kontrolllogik).

## Was 1:1 belegt ist

- **Herzen = reiner Lebenszaehler**, unabhaengig von der echten HP. Die HP
  bleiben immer vanilla (10 Herzen).
- **Actionbar-HUD statt Item/Tab**: Herzen und Partnerkopf werden per
  Custom-Font (`minecrafthelden2`) in der Actionbar gerendert.
- **Statisches HUD-Pack statt Live-Server**: Moderne Minecraft-Clients laden
  Resourcepacks nur noch ueber HTTPS. Ein selbstgehosteter Server (wie im
  Original, Port 8080) hat auf einem geteilten Hosting-Slot weder Domain
  noch TLS-Zertifikat und schlaegt deshalb fehl (`FAILED_DOWNLOAD`). Deshalb
  baut `/mchelden pack build` stattdessen EIN Pack, das die Koepfe ALLER
  bekannten Spieler als eigene Font-Glyphen enthaelt (Unicode Private Use
  Area). Das Plugin zeigt pro Spieler einfach das passende Glyph fuer den
  aktuellen Linked-Partner. Die ZIP muss danach manuell irgendwo mit HTTPS
  gehostet werden (z.B. mc-packs.net) - Link + SHA1 dann per
  `/mchelden pack seturl <url> <sha1>` eintragen. Bei neuen Verlinkungen
  muss das Pack neu gebaut und neu hochgeladen werden.
- **Spectator-Modus** (kein Bann) bei 0 Herzen.
- Tod durch den **eigenen Linked-Partner kostet kein Herz**.
- Stirbt ein Spieler lebensrelevant, verliert der **Partner ebenfalls ein
  Herz** - unabhaengig vom eigenen Bann-Check.
- **Duell-System**: max. 2 gleichzeitige Duelle pro Spieler, Annahme per
  Klick-Nachricht, automatisches Ende nach 30 Minuten, Dritte koennen nicht
  eingreifen.
- **Kampf-System**: Treffer startet eine Begegnung mit Bossbar (oder Chat,
  umschaltbar per `/combat chat|bossbar`), Ausloggen im Kampf zaehlt als
  Kampftod.
- **Inventar-Regel**: getrennte Behalt-Prozentsaetze fuer Kampf- und
  Nicht-Kampftod (`deathinventory combat|noncombat <prozent>`).
- **Blocker-Schalter**: Mending, Dorfbewohnerhandel, Notch-Apfel,
  Feuerwerk-Armbrust, Nether, Netherit, Punch, Totems.
- **Enderperlen-/Spinnweben-Limits** waehrend eines aktiven Kampfes.
- **Multispawn-System** mit optionalem Zufallsmodus.
- **Periodisches Glühen** (Anti-Camping) mit Countdown-Ankuendigung.
- **Nametag-Ausblendung** server-weit umschaltbar.
- Ziegenhorn an ein gezaehmtes Pferd bindet es dauerhaft (kleines QoL-Feature).
- Alle Chat-Texte, Farben und Befehlsnamen sind aus den String-Konstanten
  des Originals uebernommen.

## Was Annahme/Naeherung ist (nicht aus dem Bytecode belegbar)

- **COMBAT_ADD_TICKS** (Sekunden, die ein Treffer die Kampfzeit erhoeht):
  als Konstante im Bytecode vorhanden, aber der Zahlenwert liess sich ohne
  vollen Decompiler nicht auslesen. Standard hier: 300 Ticks (15s).
- Die exakte Blocker-Logik fuer **Nether/Netherit/Punch/Totems** - dazu
  fanden sich keine zugehoerigen String-Konstanten im Original. Umsetzung
  hier: Nether-Portal-Nutzung blocken, Netherit-Schmieden blocken,
  Punch-Verzauberung blocken, Totem-Resurrection blocken.
- Exakte **Font-Codepoints/Ascent-Werte** des Original-HUD-Fonts. Hier
  werden 桜 (Herz) und 頭 (Partnerkopf) genutzt - beide Zeichen tauchten so
  im Original-Bytecode auf, die exakte Zuordnung ist aber eine Annahme.
- Die **BASE_ZIP_URL** (mc-packs.net-Mirror) ist 1:1 aus dem Original
  uebernommen, kann aber jederzeit offline gehen. Deshalb liegt zusaetzlich
  ein **eigenes Fallback-Pack** (`base_pack.zip`) im Plugin-Jar, das
  automatisch genutzt wird, wenn die externe URL nicht erreichbar ist.

## Bauen

```bash
mvn clean package
```

Voraussetzungen: JDK 21, Maven, Internetzugang zum Paper-Repo. Konnte hier
nicht kompiliert werden (kein Netzwerkzugriff in dieser Umgebung) - beim
ersten Build koennen daher noch kleine Fixes noetig sein.

## Befehle

| Befehl | Berechtigung | Beschreibung |
|---|---|---|
| `/mchelden link <p1> <p2>` | `mchelden.admin` | Zwei Spieler verlinken |
| `/mchelden link random` | `mchelden.admin` | Alle Online-Spieler zufaellig paaren |
| `/mchelden hearts <spieler> <n>` | `mchelden.admin` | Herzen setzen (0 = Bann) |
| `/mchelden glow duration\|interval <zeit>` | `mchelden.admin` | Glueh-Timing, z.B. `10s`, `5min` |
| `/mchelden multispawn add\|delete\|tp\|join <name> [spieler]` | `mchelden.admin` | Multispawn verwalten |
| `/mchelden deathinventory combat\|noncombat <prozent>` | `mchelden.admin` | Inventar-Behalt-Rate |
| `/mchelden blocker <typ> <true\|false>` | `mchelden.admin` | Item-/Mechanik-Blocker |
| `/mchelden fastleafdecay <true\|false>` | `mchelden.admin` | Schneller Laubzerfall |
| `/mchelden nametags <true\|false>` | `mchelden.admin` | Nametags an/aus |
| `/mchelden limits cobwebs\|enderpearls <n>` | `mchelden.admin` | Kampf-Limits |
| `/mchelden mode random <true\|false>` | `mchelden.admin` | Zufalls-Multispawn |
| `/mchelden pack build` | `mchelden.admin` | Baut ZIP lokal, gibt SHA1 aus |
| `/mchelden pack seturl <url> <sha1>` | `mchelden.admin` | Trägt extern gehostetes Pack ein |
| `/duel <spieler>` \| `/duel accept` \| `/duel deny` | jeder | Duell-System |
| `/linkedheart` | jeder | Partner anzeigen (nur auf letztem Herz) |
| `/combat chat\|bossbar` | jeder | Kampf-Benachrichtigungsart |

## Naechste Schritte, falls du mehr Original-Praezision willst

Mit einem echten Java-Decompiler (z.B. CFR oder Vineflower) liessen sich
Methodenkoerper vollstaendig rekonstruieren - dann koennten die oben
genannten Annahmen durch die exakte Originallogik ersetzt werden. Falls
du sowas lokal hast, kannst du mir den Output schicken und ich gleiche ab.
