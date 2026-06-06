# Claude Dash

> Widget Android qui affiche en direct ta conso Claude Code (bloc 5h + fenêtre 7 jours), calé sur les vrais compteurs Anthropic — les mêmes que ceux de claude.ai.

![tier](https://img.shields.io/badge/min%20Android-8.0-blue) ![build](https://img.shields.io/badge/build-Termux%20on--device-orange) ![langue](https://img.shields.io/badge/lang-Kotlin%202.x-purple)

---

## À quoi ça sert

Tu utilises Claude Code et tu veux savoir, **sans ouvrir l'app**, combien il te reste de quota avant le prochain reset (5h glissantes + semaine). Claude Dash met deux barres de progression sur ton écran d'accueil et les rafraîchit dès qu'une session Claude Code tape de nouveaux tokens.

- **Bloc 5h** : % consommé + temps avant reset
- **Semaine** : % consommé + temps avant reset
- **Tap sur le widget** : force un refresh
- **Resizable** jusqu'à 1 cellule de haut (textes en surimpression sur les barres)

Les chiffres viennent du *même* JSON qu'Anthropic envoie à la statusline de Claude Code. Aucune estimation, aucun scraping du site.

---

## Installation

### 1. Pré-requis Termux (one-shot)

Sur le téléphone, dans Termux :

```bash
pkg install aapt aapt2 apksigner d8 kotlin openjdk-21 zipalign termux-api jq python git
termux-setup-storage          # autorise Termux à écrire dans /sdcard/
```

Installe aussi l'app compagnon **Termux:API** depuis F-Droid (sans elle, le tap-refresh ne peut pas réveiller le parser Python).

Active enfin l'IPC entrant côté Termux :

```bash
mkdir -p ~/.termux
echo 'allow-external-apps=true' >> ~/.termux/termux.properties
termux-reload-settings
```

### 2. Cloner et builder

```bash
cd ~/Projects
git clone <ce-repo> ClaudeAndroidDash
cd ClaudeAndroidDash
bash setup_sdk.sh             # télécharge le vrai android.jar depuis dl.google.com
bash build.sh                 # ~30s, sort l'APK dans /sdcard/Download/
```

Le build est **no-gradle** : `aapt2 → kotlinc → d8 → zipalign → apksigner`, tout en natif ARM64.

### 3. Installer l'APK

Ouvre l'explorateur de fichiers Android → `Téléchargements` → `ClaudeDash-2.2.apk` → *Installer*.

> Si Android refuse : *Paramètres → Applis → ton explorateur de fichiers → Installer des applis inconnues → Autoriser*.

Au premier lancement, l'appli te demande **« Accès à tous les fichiers »** — accepte (c'est nécessaire pour lire le JSON dans `/sdcard/Download/`).

### 4. Poser le widget

Long-press écran d'accueil → *Widgets* → *Claude Dash* → glisse-le où tu veux. Tu peux le redimensionner jusqu'à 1 cellule de haut.

### 5. Activer le hook statusline

C'est le hook qui pousse les vraies valeurs vers le widget. Une fois pour toutes :

```bash
# Sauvegarde l'original
cp ~/.claude/statusline-command.sh ~/.claude/statusline-command.sh.bak
```

Puis insère ce bloc tout en haut du script, juste après la ligne `input=$(cat)` :

```bash
{
  echo "$input" | jq -c '{
    updated_at: (now | todateiso8601),
    source: "statusline",
    model: .model.display_name,
    context_pct: (.context_window.used_percentage // 0 | floor),
    session_cost_usd: (.cost.total_cost_usd // 0),
    five_hour: {
      used_pct: (.rate_limits.five_hour.used_percentage // 0 | floor),
      resets_at: (.rate_limits.five_hour.resets_at_unix // 0)
    },
    seven_day: {
      used_pct: (.rate_limits.seven_day.used_percentage // 0 | floor),
      resets_at: (.rate_limits.seven_day.resets_at_unix // 0)
    }
  }' > /sdcard/Download/claude_usage.json.tmp \
    && mv -f /sdcard/Download/claude_usage.json.tmp /sdcard/Download/claude_usage.json
} &
```

Lance n'importe quelle session Claude Code → la statusline tourne → le JSON s'écrit → le widget l'affiche.

---

## Utilisation au quotidien

| Geste | Effet |
|---|---|
| **Tap sur le widget** | Force un refresh (anim d'attente ~2.5s puis nouvelles valeurs) |
| **Sessions Claude Code actives** | Le JSON est mis à jour toutes les ~2s par la statusline |
| **Pas de session ouverte** | Le widget affiche « Open Claude Code » (vide) |
| **Redimensionnement** | Drag les bords après long-press du widget |

Le système Android réveille aussi le widget tout seul toutes les 30 minutes (paramètre `updatePeriodMillis` dans `widget_info.xml`).

---

## Comment ça marche

```
┌────────────────────────────────────────────────┐
│ Claude Code (session Termux active)            │
│   └─ statusline-command.sh tourne toutes ~2s   │
│      reçoit le JSON Anthropic sur stdin        │
│      (rate_limits.five_hour / seven_day, …)    │
│                                                │
│   hook injecté : jq + écriture atomique →      │
│     /sdcard/Download/claude_usage.json         │
└────────────────────────────────────────────────┘
                    │
                    ▼
┌────────────────────────────────────────────────┐
│ Widget Android (APK Kotlin)                    │
│  UsageWidget (AppWidgetProvider)               │
│   • onUpdate : lit le JSON et rend les barres  │
│   • onReceive(ACTION_REFRESH) :                │
│       1. déclenche Termux via RUN_COMMAND      │
│       2. affiche l'état "loading" (indéterm.)  │
│       3. après 2.5s, relit le JSON et re-rend  │
└────────────────────────────────────────────────┘
```

**Source de vérité unique** : le JSON. Aucune autre métrique n'est calculée côté APK.

### Architecture du code (hexagonale)

```
com.claudedash.widget/
├── domain/      # Pure Kotlin, zéro import android.*
├── adapter/     # Adaptateurs sortants (lecture JSON, IPC Termux, horloge)
├── ui/          # Adaptateurs entrants (renderer, onboarding)
├── UsageWidget  # AppWidgetProvider (à la racine pour stabilité du cache launcher)
└── di/          # ServiceLocator (pas de framework DI)
```

Voir [`AGENTS.md`](AGENTS.md) et le dossier [`AGENTS/`](AGENTS/) pour le détail module par module.

### Mode dégradé : parser Python

Si tu n'as pas de session Claude Code ouverte, un parser Python (`parser/claude_usage.py`) peut estimer la conso depuis `~/.claude/projects/**/*.jsonl`. Active-le :

```bash
bash parser/install_cron.sh   # termux-job-scheduler toutes les 5 min
```

Les valeurs seront marquées `source: "legacy"` et restent des estimations (les vraies limites Anthropic ne sont plus exposées aux outils tiers). Préfère toujours le chemin statusline.

---

## Mettre à jour

```bash
cd ~/Projects/ClaudeAndroidDash
git pull
bash build.sh
```

Si le `versionCode` a changé, Android propose la mise à jour. Sinon désinstalle d'abord l'ancienne version.

> **Important** : si tu vois deux entrées « Claude Dash » dans le sélecteur de widgets et que la première ne marche pas, c'est que le launcher cache une ancienne référence. Désinstalle totalement l'appli depuis *Paramètres → Applis* avant de réinstaller.

---

## Dépannage

| Symptôme | Cause | Solution |
|---|---|---|
| Widget affiche « Open Claude Code » | Hook absent ou jamais déclenché | Vérifier le hook + lancer une session Claude Code |
| Tap ne refresh rien | Termux:API absent ou `allow-external-apps` non activé | Voir étape 1 |
| Icône d'appli invisible | (Corrigé en 2.x — adaptive-icon) | Rebuild depuis main |
| Deux widgets « Claude Dash » dans le picker | Cache launcher avec ancien chemin de classe | Désinstaller complètement, réinstaller |
| Build échoue sur `android.jar` | Le jar Termux est cassé | `bash setup_sdk.sh` (télécharge le vrai depuis Google) |

Pour les pièges connus côté build, voir la section *Known pitfalls* dans [`AGENTS.md`](AGENTS.md).

---

## Limites

- **Android ≥ 8.0** uniquement (`minSdk=26`).
- Pas de Play Store, pas de signature de release — l'APK est signé avec un keystore de debug, c'est volontaire (usage perso, sideload).
- Pas de split Sonnet/Opus/Haiku — Anthropic ne l'expose plus dans le flux `rate_limits`.
- Mono-compte Claude (un seul `~/.claude/`).

---

## Licence

Usage personnel. Pas de distribution publique prévue.
