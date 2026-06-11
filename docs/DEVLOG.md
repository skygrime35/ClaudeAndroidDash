# Journal de développement & débogage — ClaudeAndroidDash

Ce document retrace les **réflexions, découvertes et impasses (dead ends)** de la grosse
refonte de juin 2026 (widgets Anthropic/Google, refresh au clic sur Android 16, fichier
unique, persistance). But : que la prochaine personne (ou IA) ne refasse pas les mêmes
erreurs et comprenne *pourquoi* l'architecture est ce qu'elle est.

---

## 0. Contexte matériel (indispensable à comprendre)

- L'appareil fait tourner **Termux** (depuis le **Play Store** : `googleplay.2026.02.11`,
  installeur `com.android.vending`), dans lequel tourne un **distro PRoot « ubuntu »**
  (`proot-distro`). Claude Code, `agy` (CLI Antigravity), les credentials et les scripts
  vivent **dans le PRoot** (`HOME=/root`).
- Android **16** (API 36).
- Le home Termux `/data/data/com.termux/files/home` est **bind-monté** dans le PRoot au
  même chemin → les chemins sont identiques dedans/dehors.
- Conséquence clé : ce qui s'exécute « côté app Android » (widgets, RunCommandService) est
  en **Termux natif**, PAS dans le PRoot. Faire le pont entre les deux est LE sujet central.

---

## 1. Récupération des quotas

### Anthropic (Claude) — déjà OK
`parser/claude_usage_api.sh` : 1 requête à l'API Anthropic (`max_tokens:1`), lit les quotas
dans les en-têtes `anthropic-ratelimit-unified-5h/7d-*`. Refresh OAuth proactif du token.
Écrit aussi par le **statusline de Claude Code** (`~/.claude/statusline-command.sh`).

### Google — long chemin, beaucoup d'impasses

**Impasse 1 — fausse hypothèse.** Au début, on a cru que « le quota Claude chez Google »
était le même que Claude@Anthropic → **FAUX**. L'abo Google AI Pro donne accès à Claude via
Antigravity, c'est un quota **distinct**.

**Impasse 2 — parser le terminal `agy`.** On a piloté `agy` en pseudo-TTY pour lire
`/credits` puis `/usage`. **Fragile** : selon le timing, `/usage` affiche l'écran crédits au
lieu des quotas → « No data parsed » aléatoire. À ABANDONNER.

**Impasse 3 — format imaginé.** On pensait obtenir « Gemini 6h + semaine ». En réalité
`/usage` liste un **% par modèle** + un « Refreshes in Xh ». Pas de fenêtres propres.

**Découverte — l'API directe (la bonne voie).** `agy` parle à
`https://daily-cloudcode-pa.googleapis.com/v1internal:...`. Endpoints testés :
- `:fetchAvailableModels` → **403 SERVICE_DISABLED** (route vers le projet conso, API non activée). Impasse.
- `:retrieveUserQuotaSummary` → **403** … jusqu'à LA découverte : il faut le header
  **`User-Agent: antigravity-cli`**. Avec ça → **200** et une réponse parfaite :
  buckets `gemini-5h`, `gemini-weekly`, `3p-5h`, `3p-weekly` (3p = Claude/GPT), chacun avec
  `remainingFraction` (omis si =100 %) et `resetTime`. **Exactement** ce qu'on voulait.
  Body : `{"project": "<cloudaicompanionProject>"}` obtenu via `:loadCodeAssist`.

**Impasse 4 — le nombre de crédits IA (« 1000 »).** Introuvable par l'API
(`loadCodeAssist`, `retrieveUserQuota`, `fetchUserInfo` testés). Et `agy /credits` ne
l'affiche **plus** en texte (renvoie vers une page web). Pire : le « 1000 » qu'on voyait
était une **valeur par défaut hardcodée** par le statusline, jamais une vraie lecture.
→ Décision : **valeur fixe 1000** dans le widget.

**Token Google.** Expire en ~1h. On le rafraîchit (`grant_type=refresh_token`) avec les
`client_id/secret` d'app installée Antigravity (extraits du binaire `agy`). Refresh
**proactif** (vérif `expiry`) sinon chaque appel fait 401→refresh→retry = lent.

---

## 2. Le gros morceau : refresh au clic sur Android 16

**Impasse majeure — `RunCommandService` de Termux est inutilisable ici.** L'idée standard
(le widget envoie un intent `RUN_COMMAND` à Termux) **ne marche pas** : le service ne démarre
jamais (journal vide même avec `startForegroundService`). Permission `RUN_COMMAND` non
accordable de façon fiable au Termux Play Store. **Mort.**

**Solution retenue — un démon « écouteur ».** `parser/refresh_daemon.sh` tourne dans le
PRoot, surveille un **journal** que l'app alimente, et lance le bon script.

**Impasse FUSE — propagation app→PRoot.** L'app écrit dans `/sdcard`. Surprise : un fichier
**créé par l'app** n'est **pas visible** par le PRoot (couche FUSE). Par contre, écrire
(append) dans un fichier **déjà créé par le PRoot** EST visible. → l'app append ses triggers
dans `widget_refresh.log` (pré-créé par le démon). C'est pour ça que ce journal précis est le
canal, et qu'on a abandonné l'idée d'un fichier `.cd_refresh_<target>` créé par l'app.

**Re-render du widget — impasses successives :**
- Broadcast **implicite** (`am broadcast -a ACTION`) → non délivré aux receivers manifest
  (Android 8+). Il faut un broadcast **explicite** (`am broadcast -n <composant>`).
  → `parser/notify_widgets.sh`.
- Re-render par **RefreshActivity** qui *poll* le JSON : ça marchait MAIS provoquait un
  **freeze** (une Activity translucide qui reste au premier plan met le launcher en pause, et
  elle lit le fichier sur le thread principal). → on a **supprimé** ce comportement :
  RefreshActivity affiche « … » et **finit immédiatement** ; le re-render final est fait par
  le **broadcast** (process frais, pas de gel).

**Impasse embouteillage.** Le démon traitait **chaque** ligne de clic en série. Si on clique
plusieurs fois (ex. combiné ×4), chaque refresh prend ~8 s → le dernier clic attend ~37 s.
Symptôme : « toujours très long ». → **Déduplication** : un seul refresh par type par lot,
et « combiné » couvre déjà claude+gemini. Bug à ne jamais refaire : ne PAS réajuster le
compteur de lignes après un refresh (ça sauterait les clics arrivés pendant).

---

## 3. Autres pièges résolus

- **`<View>` non supporté par RemoteViews.** Le widget combiné affichait « Impossible de
  charger le widget ». Cause : le séparateur vertical `<View>` — les widgets n'acceptent que
  certaines classes (FrameLayout, LinearLayout, TextView, ProgressBar…). → remplacé par un
  `<FrameLayout>` de 1dp. (Seul `<View>` du projet ⇒ seul le combiné plantait.)
- **Statuslines qui écrasent les données.** `~/.claude/statusline-agy.sh` (agy) écrivait
  `gemini_usage.json` et écrasait les quotas API → section retirée. `statusline-command.sh`
  (Claude Code) écrit la partie `.claude` → adapté pour fusionner dans le fichier unique.
- **Fichier unique.** Avant : `claude_usage.json` + `gemini_usage.json`. Maintenant :
  `usage.json` avec sections `.claude` et `.google` ; chaque producteur **fusionne** sa
  section (jq `.claude = $c` / python `root["google"]=…`) sans écraser l'autre.

---

## 4. Persistance du démon

- **Termux:Boot impossible.** Il n'existe que sur F-Droid ; signature ≠ Termux Play Store →
  il ne peut pas piloter ce Termux. (Réinstaller tout Termux en F-Droid = perdre
  l'environnement PRoot → écarté.)
- **Solution : `~/.bashrc`.** Le démon (singleton via pidfile, **sans wake-lock** pour ne pas
  drainer la batterie) démarre à l'ouverture de Termux. Donc après un reboot : ouvrir Termux
  une fois suffit. Réglage recommandé : Termux en batterie « Sans restriction ».

---

## 5. Optimisations de latence (refresh ~16 s → ~4 s pour Google)

1. Refresh **proactif** du token (évite les cascades 401→refresh→retry).
2. **Cache** du `cloudaicompanionProject` (`~/.cache/cd_google_project`) → plus de
   `loadCodeAssist` à chaque clic.
3. Les 3 broadcasts `am` lancés **en parallèle** (chaque `am` démarre une JVM ~2 s).
4. Polls réduits (démon 2 s→1 s).

---

## 6. Secrets & dépôt

GitHub **Secret Scanning** a bloqué le push (les `client_secret` OAuth dans le code). →
sortis du dépôt vers **`~/.cache/cd_oauth_clients.json`** (lu par `gemini_usage_api.py`).
⚠️ Ce fichier n'est **pas** versionné : sur un nouveau clone, le recréer (méthode en
commentaire à la fin de `gemini_usage_api.py`).

---

## 7. Flux complet au clic (résumé)

```
Clic widget
  └─ RefreshActivity : append "…onCreate target=X" dans widget_refresh.log ; rend "…" ; finish()
       └─ refresh_daemon.sh (PRoot) détecte la ligne (poll 1s), déduplique, lance le script
            ├─ claude_usage_api.sh  → fusionne .claude dans usage.json
            └─ gemini_usage_api.py  → refresh token si besoin, API, fusionne .google
                 └─ notify_widgets.sh → broadcasts EXPLICITES en parallèle
                      └─ <Widget>.onReceive(ACTION_UPDATE_ALL) → relit usage.json → updateAppWidget
```

Fichiers d'état (dans `/sdcard/Download/`, hors dépôt) : `usage.json` (données),
`widget_refresh.log` (triggers, canal app→démon), `widget_daemon.log` (sorties du démon).
