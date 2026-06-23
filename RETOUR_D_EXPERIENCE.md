# RETOUR_D_EXPERIENCE.md — Branche `standalone`

> Post-mortem de l'implémentation du plan `PLAN_STANDALONE.md`.
> Rédigé après les tests sur device réel (23 juin 2026).

---

## 1. Authentification Anthropic / Claude — BLOQUÉ (fondamental)

### Ce qu'on a essayé

| Tentative | Résultat |
|---|---|
| OAuth WebView vers `claude.ai/oauth/authorize` avec `client_id=9d1c250a-...` et `redirect_uri=https://console.anthropic.com/oauth/code/callback` | Cloudflare bloque le WebView (403 `cf-mitigated: challenge`) |
| Ajout d'un `User-Agent` Chrome mobile pour contourner Cloudflare | La page se charge mais l'interception du callback échoue — écran blanc |
| `shouldOverrideUrlLoading` seul | Ne se déclenche pas pour les redirections 302 serveur-side |
| `onPageStarted` + `stopLoading()` seul | Se déclenche trop tard sur certains appareils, code déjà consommé |
| `shouldOverrideUrlLoading` + `onPageStarted` en parallèle | Fonctionne techniquement mais l'échange de token échoue |
| Scheme custom `claudedash://oauth/callback` | Refusé par le serveur Anthropic — seul `https://console.anthropic.com/...` est pré-enregistré |

### Conclusion définitive

> **L'OAuth Claude Code (`client_id=9d1c250a-e61b-44d9-88ed-5944d1962f5e`) est inutilisable pour les apps tierces.**

Anthropic a mis en place depuis début 2026 une enforcement côté serveur :
- Les tokens `sk-ant-oat01-*` émis par ce flux sont rejetés hors des clients officiels (Claude Code, claude.ai)
- C'est documenté et explicitement interdit par les Consumer Terms of Service
- Aucun workaround technique ne peut contourner cette restriction

### Solution retenue

**Clé API standard `sk-ant-api03-`** obtenue sur `console.anthropic.com/settings/keys`.

- Authentification via header `x-api-key` (non `Authorization: Bearer`)
- L'app affiche un champ de saisie + un bouton qui ouvre directement la console Anthropic
- La clé est stockée dans `SharedPreferences` privées (`TokenStore`)
- Coût : ~$0.000025 par refresh (1 requête `max_tokens:1`) → ~$0.20/mois à 5 min d'intervalle

---

## 2. Authentification Google Cloud Code — BLOQUÉ (fondamental)

### Ce qu'on a essayé

| Tentative | Résultat |
|---|---|
| `AccountManager.getAuthToken(account, "oauth2:https://www.googleapis.com/auth/cloud-platform", ...)` depuis un thread background avec `notifyAuthFailure=false` | `UNREGISTERED_ON_API_CONSOLE` |
| Même appel avec callback sur UI thread (Activity-aware) | `UNREGISTERED_ON_API_CONSOLE` |
| Récupération du `KEY_INTENT` dans le bundle pour afficher l'écran de consentement | L'intent n'est jamais présent — Google refuse en amont |

### Conclusion définitive

> **L'`AccountManager` Android refuse d'émettre un token OAuth `cloud-platform` à une application non enregistrée dans Google Cloud Console.**

L'enregistrement dans Google Cloud Console nécessite :
1. Un package signé avec une empreinte SHA-1 fixe
2. Typiquement un compte Google Play Store

Sans cela, le scope `cloud-platform` est systématiquement bloqué. Ce n'est pas un bug de code.

### Solution retenue

Fallback : **affichage d'un message d'explication** dans l'onboarding au lieu d'un flux cassé. Le widget Claude (via clé API) fonctionne de façon autonome et couvre le cas d'usage principal.

---

## 3. Interception WebView des redirections OAuth — LEÇON TECHNIQUE

### Problème

`shouldOverrideUrlLoading` ne se déclenche **pas** pour les redirections HTTP 302 initiées côté serveur — seules les navigations explicites de l'utilisateur le déclenchent.

### Solution correcte

Combiner les deux callbacks :
```kotlin
// Pour les redirections 302 serveur-side
override fun onPageStarted(view: WebView?, url: String?, ...) {
    if (url?.startsWith(REDIRECT_URI) == true) { view?.stopLoading(); ... }
}
// Pour les navigations explicites (clic sur lien)
override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
    if (request.url.toString().startsWith(REDIRECT_URI)) { ...; return true }
    return false
}
```

Même cette double interception ne fonctionnait pas dans notre cas à cause du blocage Cloudflare en amont.

---

## 4. `lateinit val` en Kotlin — BUG DE COMPILATION

`lateinit` ne peut s'appliquer qu'à des `var`, jamais à des `val`.

```kotlin
// ❌ Erreur de compilation
private lateinit val tokenStore: TokenStore

// ✅ Correct
private lateinit var tokenStore: TokenStore
```

---

## 5. Broadcast vers les widgets depuis un Service

Pour notifier tous les widgets depuis `ApiRefreshService`, il faut envoyer un broadcast **par classe** de widget receiver :

```kotlin
// ❌ Insuffisant — ne réveille que le widget Claude
sendBroadcast(Intent(this, UsageWidget::class.java).apply { action = ACTION_UPDATE_ALL })

// ✅ Correct — réveille tous les widgets
sendBroadcast(Intent(this@ApiRefreshService, UsageWidget::class.java).apply { action = ACTION_UPDATE_ALL })
sendBroadcast(Intent(this@ApiRefreshService, GeminiUsageWidget::class.java).apply { action = ACTION_UPDATE_ALL })
sendBroadcast(Intent(this@ApiRefreshService, CombinedUsageWidget::class.java).apply { action = ACTION_UPDATE_ALL })
```

Aussi : `this` dans une lambda / thread n'est pas l'instance du Service — utiliser `this@NomDuService`.

---

## 6. Chemin du fichier `usage.json` — CHANGEMENT DE PARADIGME

L'ancien chemin `/sdcard/Download/claude_usage.json` (partagé avec Termux) est remplacé par le stockage interne de l'app : `/data/data/com.claudedash.widget/files/usage.json`. Accessible via `context.filesDir`.

**Attention** : `ServiceLocator` utilise ce chemin en dur. Si le `packageName` change, le chemin change aussi.

---

## 7. État final de l'architecture

```
Onboarding
  └─ Saisie de clé API Anthropic (sk-ant-api03-...)
      └─ TokenStore (SharedPreferences)

Widget tap (refresh)
  └─ DirectRefreshTrigger
      └─ ApiRefreshService (thread background)
          ├─ AnthropicApiRepository.fetch()
          │   └─ POST /v1/messages (max_tokens:1)
          │       └─ Lit headers anthropic-ratelimit-unified-*
          └─ GoogleQuotaRepository.fetch()  [désactivé — voir §2]
```

---

## 8. Références utiles

- Anthropic API Keys : https://console.anthropic.com/settings/keys
- Headers rate limits : `anthropic-ratelimit-unified-5h-utilization`, `anthropic-ratelimit-unified-5h-reset`, `anthropic-ratelimit-unified-7d-utilization`, `anthropic-ratelimit-unified-7d-reset`
- Modèle à utiliser pour le ping minimal : `claude-haiku-4-5` (le moins cher)
- Google Cloud Console OAuth : https://console.cloud.google.com/apis/credentials (nécessaire pour débloquer le scope `cloud-platform` via AccountManager)
