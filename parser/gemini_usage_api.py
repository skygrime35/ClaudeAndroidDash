#!/usr/bin/env python3
# Récupère le quota Google (Gemini + Claude/3p, fenêtres 5h et hebdo) via l'API Code Assist
# que la CLI Antigravity utilise — APPEL HTTP DIRECT, pas de pilotage du terminal.
#   POST https://daily-cloudcode-pa.googleapis.com/v1internal:retrieveUserQuotaSummary
#   Authorization: Bearer <token OAuth de ~/.gemini/antigravity-cli>, User-Agent: antigravity-cli
#
# Le token OAuth Google expire (~1h) : on le rafraîchit automatiquement via le refresh_token
# présent dans le fichier (comme claude_usage_api.sh le fait pour Anthropic). Les client_id/
# secret sont ceux, publics, de l'app installée Antigravity (extraits du binaire agy).
import json
import os
import subprocess
import urllib.request
import urllib.error
import urllib.parse
from datetime import datetime, timezone, timedelta

TOKEN_FILE = "/root/.gemini/antigravity-cli/antigravity-oauth-token"
OUT = "/sdcard/Download/usage.json"
BASE = "https://daily-cloudcode-pa.googleapis.com/v1internal"
QUOTA_URL = BASE + ":retrieveUserQuotaSummary"
LOAD_URL = BASE + ":loadCodeAssist"
UA = "antigravity-cli"
NOTIFY = "/data/data/com.termux/files/home/Projects/ClaudeAndroidDash/parser/notify_widgets.sh"
PROJECT_CACHE = "/data/data/com.termux/files/home/.cache/cd_google_project"

TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
# Paires (client_id, client_secret) d'app installée Antigravity. Stockées HORS du dépôt
# (secrets) ; régénérables depuis le binaire agy : voir le commentaire en bas de fichier.
OAUTH_CLIENTS_FILE = "/data/data/com.termux/files/home/.cache/cd_oauth_clients.json"


def load_oauth_clients():
    try:
        with open(OAUTH_CLIENTS_FILE) as f:
            return [tuple(c) for c in json.load(f)]
    except Exception:
        return []


def load_token_file():
    with open(TOKEN_FILE) as f:
        return json.load(f)


def refresh_access_token():
    """Échange le refresh_token contre un nouvel access_token et le persiste. Retourne le token ou None."""
    try:
        data = load_token_file()
        rt = data["token"]["refresh_token"]
    except Exception:
        return None
    for cid, sec in load_oauth_clients():
        body = urllib.parse.urlencode({
            "grant_type": "refresh_token", "refresh_token": rt,
            "client_id": cid, "client_secret": sec}).encode()
        try:
            req = urllib.request.Request(
                TOKEN_ENDPOINT, data=body,
                headers={"Content-Type": "application/x-www-form-urlencoded"})
            with urllib.request.urlopen(req, timeout=20) as r:
                resp = json.load(r)
        except Exception:
            continue
        if "access_token" in resp:
            data["token"]["access_token"] = resp["access_token"]
            exp = datetime.now(timezone.utc) + timedelta(seconds=resp.get("expires_in", 3600))
            data["token"]["expiry"] = exp.strftime("%Y-%m-%dT%H:%M:%S.%fZ")
            try:
                tmp = TOKEN_FILE + ".tmp"
                with open(tmp, "w") as f:
                    json.dump(data, f)
                os.replace(tmp, TOKEN_FILE)
            except Exception:
                pass
            print("token refreshed", flush=True)
            return resp["access_token"]
    return None


def api(url, token, body):
    req = urllib.request.Request(
        url, data=json.dumps(body).encode(),
        headers={"Authorization": f"Bearer {token}",
                 "Content-Type": "application/json", "User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


# Token courant (rafraîchi à la volée sur 401).
_TOKEN = [None]


def call(url, body):
    for attempt in range(2):
        try:
            return api(url, _TOKEN[0], body)
        except urllib.error.HTTPError as e:
            if e.code in (401, 403) and attempt == 0:
                t = refresh_access_token()
                if not t:
                    raise
                _TOKEN[0] = t
            else:
                raise


def iso_to_epoch(iso):
    try:
        return int(datetime.fromisoformat(iso.replace("Z", "+00:00")).timestamp())
    except Exception:
        return None


def token_near_expiry():
    """True si le token expire dans <2 min (ou si l'expiry est illisible)."""
    try:
        exp = load_token_file()["token"].get("expiry", "")
        e = datetime.fromisoformat(exp.replace("Z", "+00:00"))
        return datetime.now(timezone.utc) >= e - timedelta(seconds=120)
    except Exception:
        return True


def main():
    try:
        _TOKEN[0] = load_token_file()["token"]["access_token"]
    except Exception as e:
        print(f"no token: {e}", flush=True)
        return

    # Refresh PROACTIF : sans ça, un token expiré fait échouer chaque appel en 401 puis
    # refresh+retry → très lent. On rafraîchit une seule fois, en amont.
    if token_near_expiry():
        t = refresh_access_token()
        if t:
            _TOKEN[0] = t

    # Le project (cloudaicompanionProject) est fixe par compte : on le met en cache pour
    # éviter l'appel loadCodeAssist à CHAQUE clic.
    project = ""
    try:
        with open(PROJECT_CACHE) as f:
            project = f.read().strip()
    except Exception:
        pass
    if not project:
        try:
            la = call(LOAD_URL, {"metadata": {
                "ideType": "IDE_UNSPECIFIED", "platform": "PLATFORM_UNSPECIFIED",
                "pluginType": "GEMINI"}})
            project = la.get("cloudaicompanionProject", "")
            if project:
                try:
                    os.makedirs(os.path.dirname(PROJECT_CACHE), exist_ok=True)
                    with open(PROJECT_CACHE, "w") as f:
                        f.write(project)
                except Exception:
                    pass
        except Exception as e:
            print(f"loadCodeAssist failed: {e}", flush=True)

    qs = call(QUOTA_URL, {"project": project})
    buckets = {}
    for group in qs.get("groups", []):
        for b in group.get("buckets", []):
            buckets[b.get("bucketId")] = b

    def pct(bid):
        b = buckets.get(bid)
        return round(b.get("remainingFraction", 1.0) * 100) if b else None

    def reset(bid):
        b = buckets.get(bid)
        return iso_to_epoch(b.get("resetTime", "")) if b else None

    # Fichier unique : on charge l'existant pour préserver la section .claude.
    root = {}
    try:
        with open(OUT) as f:
            root = json.load(f)
    except Exception:
        pass
    # NB : pas de champ "credits" ici. Les crédits IA Google One (1000/mois sur AI Pro) ne
    # sont exposés par AUCUNE API Code Assist accessible (loadCodeAssist ne renvoie pas
    # availableCredits, les verbes :*Credits sont en 404, generateContent n'inclut rien).
    # agy les lit via gRPC pur et ne tourne pas sans TTY. Seule source fiable : la page web
    # one.google.com/u/1/ai/activity. Tant qu'on n'a pas cette donnée, la ligne crédits a été
    # retirée des widgets plutôt que d'afficher une valeur fictive. Voir docs/DEVLOG.md.
    root["google"] = {
        "updated_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "source": "antigravity-api",
        "gemini_5h_pct": pct("gemini-5h"), "gemini_5h_reset": reset("gemini-5h"),
        "gemini_week_pct": pct("gemini-weekly"), "gemini_week_reset": reset("gemini-weekly"),
        "claude_5h_pct": pct("3p-5h"), "claude_5h_reset": reset("3p-5h"),
        "claude_week_pct": pct("3p-weekly"), "claude_week_reset": reset("3p-weekly"),
    }

    tmp = OUT + f".{os.getpid()}.tmp"
    with open(tmp, "w") as f:
        json.dump(root, f, indent=2)
    os.replace(tmp, OUT)
    print(f"wrote .google in {OUT}: {root['google']}", flush=True)
    try:
        subprocess.run(["bash", NOTIFY], capture_output=True)
    except Exception:
        pass


if __name__ == "__main__":
    main()

# Régénérer cd_oauth_clients.json (client_id/secret d'app installée Antigravity, hors dépôt) :
#   - les client_id ont la forme  <chiffres>-<alnum>.apps.googleusercontent.com
#   - les client_secret ont le préfixe Google "installed app" (cf. doc OAuth)
#   les extraire via `strings` sur le binaire, apparier, et écrire la liste
#   [[client_id, secret], ...] dans ~/.cache/cd_oauth_clients.json
