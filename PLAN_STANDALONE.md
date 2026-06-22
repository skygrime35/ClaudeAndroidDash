# Plan — branche `standalone`

## Objectif

Supprimer toute dépendance à Termux. L'app fonctionne en autonome sur n'importe quel téléphone Android : l'utilisateur se connecte à son compte Claude et/ou Google directement depuis l'app via une WebView, l'app capture les tokens OAuth et gère leur renouvellement de façon transparente.

Modèle de référence : [AI Usage: Claude, Codex & more](https://play.google.com/store/apps/details?id=u.sage)

---

## Ce qui disparaît

| Supprimé | Raison |
|---|---|
| `TermuxRefreshTrigger` | Remplacé par un Service Android natif |
| `JsonFileUsageRepository` | Remplacé par des appels HTTP directs depuis l'app |
| Permission `MANAGE_EXTERNAL_STORAGE` | Plus de fichier partagé avec Termux |
| Permission `com.termux.permission.RUN_COMMAND` | Plus d'intent vers Termux |
| Scripts `parser/` (non supprimés, restent sur `main`) | Non utilisés dans cette branche |

---

## Ce qui s'ajoute

### Nouveaux fichiers Kotlin

| Fichier | Rôle |
|---|---|
| `adapter/auth/ClaudeLoginActivity.kt` | WebView vers claude.ai, PKCE S256, intercept du callback OAuth, échange du code contre access+refresh token |
| `adapter/credentials/TokenStore.kt` | Wrapper SharedPreferences privées : lit/écrit `claude_access_token`, `claude_refresh_token`, `claude_expires_at`, `google_access_token`, etc. |
| `adapter/repository/AnthropicApiRepository.kt` | POST `https://api.anthropic.com/v1/messages` (1 token), lit les headers `anthropic-ratelimit-unified-*`, retourne `UsageSnapshot` |
| `adapter/repository/GoogleQuotaRepository.kt` | `AccountManager` Android → token OAuth Google → POST `https://daily-cloudcode-pa.googleapis.com/v1internal:retrieveUserQuotaSummary`, retourne la partie `google` du snapshot |
| `adapter/refresh/ApiRefreshService.kt` | `Service` Android, tourne en thread background, appelle Anthropic + Google API, met à jour `TokenStore`, notifie les widgets |
| `adapter/refresh/DirectRefreshTrigger.kt` | Implémente `RefreshTrigger` : lance `ApiRefreshService` via `startService()` |

### Fichiers modifiés

| Fichier | Modification |
|---|---|
| `di/ServiceLocator.kt` | Câble `AnthropicApiRepository` + `GoogleQuotaRepository` à la place de `JsonFileUsageRepository`, et `DirectRefreshTrigger` à la place de `TermuxRefreshTrigger` |
| `ui/OnboardingActivity.kt` | Remplace les instructions Termux par deux boutons : "Se connecter à Claude" (lance `ClaudeLoginActivity`) et "Se connecter à Google" (lance le flow `AccountManager`) |
| `AndroidManifest.xml` | Ajoute `INTERNET` + `GET_ACCOUNTS` ; supprime `MANAGE_EXTERNAL_STORAGE`, `READ_EXTERNAL_STORAGE`, `RUN_COMMAND` ; déclare `ClaudeLoginActivity` et `ApiRefreshService` |

### Fichiers supprimés

- `adapter/refresh/TermuxRefreshTrigger.kt`
- `adapter/repository/JsonFileUsageRepository.kt`

---

## Flux OAuth Claude (détail)

1. Générer `code_verifier` (32 bytes random, base64url) et `code_challenge` (SHA-256 du verifier, base64url)
2. Ouvrir WebView vers :
   ```
   https://claude.ai/oauth/authorize
     ?response_type=code
     &client_id=9d1c250a-e61b-44d9-88ed-5944d1962f5e
     &redirect_uri=https://console.anthropic.com/oauth/code/callback
     &scope=user:inference%20user:profile
     &code_challenge=<challenge>
     &code_challenge_method=S256
     &state=<verifier>
   ```
3. Dans `WebViewClient.shouldOverrideUrlLoading`, détecter l'URL commençant par `https://console.anthropic.com/oauth/code/callback`
4. Extraire `?code=XXX` de l'URL
5. POST à `https://api.anthropic.com/v1/oauth/token` :
   ```json
   {
     "grant_type": "authorization_code",
     "code": "<code>",
     "redirect_uri": "https://console.anthropic.com/oauth/code/callback",
     "client_id": "9d1c250a-e61b-44d9-88ed-5944d1962f5e",
     "code_verifier": "<verifier>"
   }
   ```
6. Stocker `access_token`, `refresh_token`, `expires_at` dans `TokenStore`

### Renouvellement automatique du token Claude

Avant chaque appel API, si `expires_at - now < 60s` :
```
POST https://api.anthropic.com/v1/oauth/token
  grant_type=refresh_token
  refresh_token=<stored>
  client_id=9d1c250a-e61b-44d9-88ed-5944d1962f5e
```

---

## Flux Google (détail)

1. `AccountManager.get(context).getAccountsByType("com.google")` → liste des comptes Google du téléphone
2. Si plusieurs comptes, l'utilisateur en choisit un (dialog simple)
3. `accountManager.getAuthToken(account, "oauth2:https://www.googleapis.com/auth/cloud-platform", ...)` → token d'accès
4. POST `https://daily-cloudcode-pa.googleapis.com/v1internal:retrieveUserQuotaSummary` avec `Authorization: Bearer <token>` et `User-Agent: antigravity-cli`
5. Parser la réponse (même format que le script Python actuel)

> **Note** : si Google refuse le scope `cloud-platform` sans projet Cloud enregistré, fallback : afficher les quotas Claude uniquement et proposer un message d'aide pour le setup Google.

---

## Ce qui NE change pas

- Tout `domain/` — ports, `UsageSnapshot`, `UsageFormatter`, `Clock` : **zéro modification**
- L'architecture hexagonale reste identique (on swap des adapters, pas la logique)
- `adapter/clock/RealClock.kt` — inchangé
- Le build system (`build.sh`, `setup_sdk.sh`) — inchangé
- Les widgets (`UsageWidget`, `GeminiUsageWidget`, `CombinedUsageWidget`) — inchangés
- `RefreshActivity.kt`, `WidgetRenderer.kt` — inchangés

---

## Permissions finales dans `AndroidManifest.xml`

```xml
<!-- Ajoutées -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.GET_ACCOUNTS" />

<!-- Supprimées -->
<!-- android.permission.READ_EXTERNAL_STORAGE -->
<!-- android.permission.MANAGE_EXTERNAL_STORAGE -->
<!-- com.termux.permission.RUN_COMMAND -->
```

---

## Ordre d'implémentation suggéré

1. `TokenStore.kt` (pas de dépendances, testable isolément)
2. `AnthropicApiRepository.kt` (logique HTTP Claude, le plus critique)
3. `ClaudeLoginActivity.kt` (WebView OAuth)
4. `ApiRefreshService.kt` + `DirectRefreshTrigger.kt`
5. `GoogleQuotaRepository.kt`
6. `OnboardingActivity.kt` (modif)
7. `ServiceLocator.kt` (câblage final)
8. `AndroidManifest.xml` (permissions + déclarations)
9. Supprimer `TermuxRefreshTrigger.kt` + `JsonFileUsageRepository.kt`
10. Build + test sur device
