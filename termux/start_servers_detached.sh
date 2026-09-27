#!/data/data/com.termux/files/usr/bin/bash
set -u

# Start ERAYA's local engines without tying their lifetime to the adb shell used to launch them.
LOG="$HOME/eraya-servers.log"
nohup bash "$HOME/ErayaXiqoo/termux/run_servers.sh" >"$LOG" 2>&1 </dev/null &
echo "ERAYA servers starting as PID $!; log: $LOG"
