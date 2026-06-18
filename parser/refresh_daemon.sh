#!/data/data/com.termux/files/usr/bin/bash
# Démon de refresh des widgets ClaudeAndroidDash.
#
# Sur Android 16, une app ne peut pas lancer un script Termux au clic (RunCommandService
# bloqué). Contournement : RefreshActivity APPEND "RefreshActivity onCreate target=<t>"
# dans le journal ci-dessous (seules les écritures de l'app sur un fichier déjà créé côté
# PRoot se propagent de façon fiable via FUSE) ; ce démon le surveille et lance le script.
#
# Singleton (pidfile). PAS de wake-lock : dort écran éteint (zéro batterie), se réveille
# avec l'écran = quand l'utilisateur clique. Lancé au boot par ~/.termux/boot/.
export PATH="/data/data/com.termux/files/usr/bin:$PATH"

DIR="/data/data/com.termux/files/home/Projects/AndroidApp/ClaudeAndroidDash/parser"
LOG="/sdcard/Download/widget_refresh.log"     # triggers écrits par l'app (lus ici)
DLOG="/sdcard/Download/widget_daemon.log"     # journal interne du démon (sorties scripts)
PIDFILE="/data/data/com.termux/files/home/.cache/cd_refresh_daemon.pid"

# Singleton : ne pas lancer une 2e instance.
if [ -f "$PIDFILE" ] && kill -0 "$(cat "$PIDFILE" 2>/dev/null)" 2>/dev/null; then
  exit 0
fi
echo $$ > "$PIDFILE"

# Le journal de triggers doit exister (créé côté PRoot) pour que les append de l'app soient visibles.
[ -f "$LOG" ] || : > "$LOG"
# Rotation : repartir propre si le journal est devenu gros.
[ "$(wc -l < "$LOG" 2>/dev/null || echo 0)" -gt 2000 ] && : > "$LOG"

echo "$(date '+%F %T') refresh_daemon v4 started (pid $$)" >> "$DLOG"

processed=$(wc -l < "$LOG" 2>/dev/null | tr -d ' ')
[ -z "$processed" ] && processed=0

while true; do
  total=$(wc -l < "$LOG" 2>/dev/null | tr -d ' ')
  [ -z "$total" ] && total=0
  if [ "$total" -gt "$processed" ]; then
    new=$(sed -n "$((processed + 1)),${total}p" "$LOG" 2>/dev/null)
    processed="$total"   # ne JAMAIS sauter au-delà : les clics arrivés pendant un refresh
                         # sont > total et seront traités au cycle suivant.
    # DÉDUPLICATION : peu importe le nombre de clics dans ce lot, on ne lance qu'UN refresh
    # par type (et "combined" couvre déjà claude+gemini). Évite l'embouteillage.
    dc=0; dg=0; dco=0
    while IFS= read -r line; do
      case "$line" in
        *"target=combined"*) dco=1 ;;
        *"target=gemini"*)   dg=1 ;;
        *"target=claude"*)   dc=1 ;;
      esac
    done <<< "$new"
    if [ "$dco" = 1 ]; then
      echo "$(date '+%F %T') -> combined" >> "$DLOG"; bash "$DIR/refresh_all.sh" >> "$DLOG" 2>&1
    else
      [ "$dc" = 1 ] && { echo "$(date '+%F %T') -> claude" >> "$DLOG"; bash "$DIR/claude_usage_api.sh" >> "$DLOG" 2>&1; }
      [ "$dg" = 1 ] && { echo "$(date '+%F %T') -> gemini" >> "$DLOG"; bash "$DIR/gemini_usage_api.sh" >> "$DLOG" 2>&1; }
    fi
  fi
  sleep 1
done
