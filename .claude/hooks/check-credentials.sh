#!/usr/bin/env bash
# PreToolUse hook (Read|Write|Edit|MultiEdit): credentials must never be written in committed files.
# - Write/Edit: denies the call when the new content holds a literal credential and the file is not git-ignored.
# - Read: never blocks, but warns Claude when a committable file already holds a literal credential
#   (typical case: .mcp.json), so it is neither copied nor repeated.
# Placeholders such as ${VAR}, $VAR or <value> are accepted: use environment variables.

input=$(cat)
tool=$(jq -r '.tool_name // empty' <<<"$input")
file=$(jq -r '.tool_input.file_path // empty' <<<"$input")
[ -z "$file" ] && exit 0

# Git-ignored files (.env, application-local.yaml, ...) are the right place for credentials.
git check-ignore -q -- "$file" 2>/dev/null && exit 0

KEY='(password|passwd|pwd|secret|token|api[_-]?key|apikey|access[_-]?key|private[_-]?key|client[_-]?secret|authorization|bearer)'
# key (optionally quoted) then ':' or '=' then a literal value of 4+ chars
ASSIGN="[\"']?[A-Za-z0-9_.-]*${KEY}[A-Za-z0-9_.-]*[\"']?[[:space:]]*[:=][[:space:]]*[\"']?[^[:space:]\"',]{4,}"
KNOWN='(AKIA[0-9A-Z]{16}|gh[pousr]_[A-Za-z0-9]{36,}|sk-[A-Za-z0-9_-]{20,}|xox[baprs]-[A-Za-z0-9-]{10,}|-----BEGIN [A-Z ]*PRIVATE KEY-----|://[^/[:space:]:@]+:[^/[:space:]@${]{3,}@)'
PLACEHOLDER='[:=][[:space:]]*["'"'"']?(\$|<|\*|%|#\{|\{\{|changeme|your[_-]|xxx|null|true|false)'

find_credentials() {
  # stdin: text to inspect. Prints offending lines (value masked), exit 0 when something was found.
  local hits
  hits=$({ grep -Eiv "$PLACEHOLDER" | grep -Ei -e "$ASSIGN" -e "$KNOWN"; } 2>/dev/null) || true
  # Known token formats are suspicious even next to a placeholder-looking word.
  [ -z "$hits" ] && return 1
  # Mask known token formats and URL passwords first, then literal assignment values.
  sed -E \
    -e 's#(://[^/[:space:]:@]+:)[^/[:space:]@]+@#\1***@#g' \
    -e 's/(AKIA[0-9A-Z]{16}|gh[pousr]_[A-Za-z0-9]{36,}|sk-[A-Za-z0-9_-]{20,}|xox[baprs]-[A-Za-z0-9-]{10,})/***/g' \
    -e 's/(-----BEGIN [A-Z ]*PRIVATE KEY-----).*/\1 ***/' \
    -e 's/(([Pp]assword|[Pp]asswd|[Ss]ecret|[Tt]oken|[Kk]ey|[Aa]uthorization)[A-Za-z0-9_.-]*["'"'"']?[[:space:]]*[:=][[:space:]]*["'"'"']?)[^[:space:]"'"'"',*]{4,}/\1***/g' \
    <<<"$hits" | head -5
  return 0
}

case "$tool" in
  Write)
    text=$(jq -r '.tool_input.content // empty' <<<"$input") ;;
  Edit)
    text=$(jq -r '.tool_input.new_string // empty' <<<"$input") ;;
  MultiEdit)
    text=$(jq -r '[.tool_input.edits[]?.new_string] | join("\n")' <<<"$input") ;;
  Read)
    [ -f "$file" ] || exit 0
    text=$(head -c 200000 "$file") ;;
  *) exit 0 ;;
esac
[ -z "$text" ] && exit 0

if lines=$(find_credentials <<<"$text"); then
  if [ "$tool" = "Read" ]; then
    jq -n --arg f "$file" --arg l "$lines" '{hookSpecificOutput:{hookEventName:"PreToolUse",additionalContext:("WARNING: "+$f+" is not git-ignored and seems to contain literal credentials (values masked):\n"+$l+"\nNever copy or repeat these values. Replace them by environment variable references (e.g. ${MY_TOKEN}) and tell the user to rotate them.")}}'
    exit 0
  fi
  jq -n --arg f "$file" --arg l "$lines" '{hookSpecificOutput:{hookEventName:"PreToolUse",permissionDecision:"deny",permissionDecisionReason:("Credentials must never be written in committed files ("+$f+"). Offending lines (values masked):\n"+$l+"\nUse an environment variable reference (e.g. ${MY_TOKEN}) or a git-ignored file instead.")}}'
fi
exit 0
