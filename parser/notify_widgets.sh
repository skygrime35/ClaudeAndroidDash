#!/data/data/com.termux/files/usr/bin/bash
# Filet de sécurité : force le redraw des widgets (le re-render principal est fait par
# RefreshActivity qui relit le JSON). Broadcast EXPLICITE (-n) car l'implicite n'est pas
# délivré aux receivers manifest sur Android 8+. Les 3 broadcasts sont lancés EN PARALLÈLE
# (chaque `am` démarre une JVM ~2s : en série ça ajoutait ~6s au refresh).
AM="/data/data/com.termux/files/usr/bin/am"
for comp in UsageWidget GeminiUsageWidget CombinedUsageWidget; do
  "$AM" broadcast -n "com.claudedash.widget/.$comp" \
    -a com.claudedash.widget.ACTION_UPDATE_ALL --user 0 >/dev/null 2>&1 &
done
wait
