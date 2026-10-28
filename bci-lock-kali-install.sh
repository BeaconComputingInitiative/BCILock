#!/usr/bin/env bash
# BEACON COMPUTING INITIATIVE (BCI)
# Component: BCI Lock | Kali Linux Installer v1.0
# Developer Seal: CJB-DEV-2026
# File integrity: SHA-256 manifest generated at installation
set -Eeuo pipefail

if [[ ${EUID} -eq 0 ]]; then
  echo '[BCI LOCK] Run as your normal Kali user, not root.' >&2; exit 1
fi
if ! grep -qi '^ID=kali' /etc/os-release; then
  echo '[BCI LOCK] This installer targets Kali Linux only.' >&2; exit 1
fi
for cmd in sudo apt-get sha256sum systemctl install; do
  command -v "$cmd" >/dev/null || { echo "Missing $cmd" >&2; exit 1; }
done
sudo -v
if [[ -e /usr/local/bin/bci-lock || -L /usr/local/bin/bci-lock ]] &&
   [[ "$(readlink /usr/local/bin/bci-lock 2>/dev/null || true)" != '/usr/local/sbin/bci-lock' ]]; then
  echo '[BCI LOCK] An unrelated /usr/local/bin/bci-lock exists; refusing to overwrite.' >&2
  exit 1
fi
printf '[BCI LOCK] Installing Kali dependencies...\n'
sudo apt-get update
sudo apt-get install -y --no-remove --no-install-recommends nftables rfkill

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
cat > "$work/lock.nft" <<'NFT'
# BEACON COMPUTING INITIATIVE (BCI)
# Component: BCI Lock nftables rules
# Developer Seal: CJB-DEV-2026
# SHA-256 recorded in /etc/bci-lock/SHA256SUMS
table inet bci_lock {
    chain input {
        type filter hook input priority -150; policy drop;
        iifname "lo" accept
    }
    chain output {
        type filter hook output priority -150; policy drop;
        oifname "lo" accept
    }
    chain forward {
        type filter hook forward priority -150; policy drop;
    }
}
NFT

cat > "$work/bci-lock" <<'ENGINE'
#!/usr/bin/env bash
# BEACON COMPUTING INITIATIVE (BCI)
# Component: BCI Lock Network Isolation v1.0
# Developer Seal: CJB-DEV-2026
# SHA-256 recorded in /etc/bci-lock/SHA256SUMS
set -Eeuo pipefail
# Requested lock state is ARMED if and only if $STATE/armed exists.
STATE=/var/lib/bci-lock
RULES=/etc/bci-lock/lock.nft
SERVICES=(ssh.service sshd.service xrdp.service tailscaled.service zerotier-one.service docker.socket docker.service containerd.service)

notice() {
    printf '[BCI LOCK] %s\n' "$*"
    if [[ ${EUID} -eq 0 ]]; then
        mkdir -p "$STATE"
        printf '%s %s\n' "$(date -u +%FT%TZ)" "$*" >> "$STATE/audit.log"
        logger -t bci-lock -- "$*" 2>/dev/null || true
    fi
}
need_root() {
    if [[ ${EUID} -ne 0 ]]; then echo 'Use sudo for this command.' >&2; exit 1; fi
}
has_table() { nft list table inet bci_lock >/dev/null 2>&1; }
apply_firewall() {
    if ! has_table; then
        nft -f "$RULES" || { notice 'ERROR: nftables lockdown could not be installed'; return 1; }
    fi
}
stop_services() {
    local service
    for service in "${SERVICES[@]}"; do
        if systemctl is-active --quiet "$service"; then
            systemctl stop "$service" || notice "WARNING: could not stop $service"
        fi
    done
}
apply_radios_and_network() {
    rfkill block all || notice 'WARNING: rfkill could not block some radios'
    if command -v nmcli >/dev/null; then
        nmcli networking off || notice 'WARNING: NetworkManager could not disable networking'
    fi
}
snapshot() {
    umask 077
    mkdir -p "$STATE"
    : > "$STATE/active-services"
    local service
    for service in "${SERVICES[@]}"; do
        if systemctl is-active --quiet "$service"; then
            printf '%s\n' "$service" >> "$STATE/active-services"
        fi
    done
    rfkill -rn -o ID,SOFT > "$STATE/rfkill.before" || :
    if command -v nmcli >/dev/null; then
        nmcli networking > "$STATE/nm.before" 2>/dev/null || :
    fi
}
arm() {
    need_root
    if [[ -n "${SSH_CONNECTION:-}${SSH_CLIENT:-}${SSH_TTY:-}" ]]; then
        notice 'Refusing to arm from an SSH session. Use the local console.'; exit 1
    fi
    if [[ -f "$STATE/armed" ]]; then
        notice 'Already armed; re-enforcing existing lockdown.'
        enforce; return
    fi
    snapshot
    printf '%s\n' "$(date -u +%FT%TZ)" > "$STATE/armed"
    if ! apply_firewall; then
        rm -f "$STATE/armed"; exit 1
    fi
    apply_radios_and_network
    stop_services
    notice 'ARMED: IPv4/IPv6 denied except local loopback; radio blocks applied.'
}
enforce() {
    need_root
    if [[ ! -f "$STATE/armed" ]]; then return 0; fi
    apply_firewall
    apply_radios_and_network
    stop_services
    notice 'Boot/repeat enforcement applied.'
}
home() {
    need_root
    if [[ ! -f "$STATE/armed" ]]; then
        if has_table; then
            nft delete table inet bci_lock
            notice 'Removed orphaned BCI firewall table.'
        fi
        notice 'Already disarmed.'; return
    fi
    # Delete ONLY the BCI-owned table, never flush the host ruleset.
    if has_table; then nft delete table inet bci_lock || { notice 'ERROR: firewall removal failed'; exit 1; }; fi
    rm -f "$STATE/armed"
    # Restore precisely the recorded per-radio soft-unblocked devices.
    if [[ -f "$STATE/rfkill.before" ]]; then
        while read -r id blocked; do
            if [[ "$id" =~ ^[0-9]+$ ]] && [[ "$blocked" =~ ^(no|false|unblocked|0)$ ]]; then
                rfkill unblock "$id" || notice "WARNING: could not restore radio $id"
            fi
        done < "$STATE/rfkill.before"
    fi
    if command -v nmcli >/dev/null && [[ -f "$STATE/nm.before" ]]; then
        if grep -q '^enabled$' "$STATE/nm.before"; then
            nmcli networking on || notice 'WARNING: NetworkManager restore failed'
        fi
    fi
    if [[ -f "$STATE/active-services" ]]; then
        while read -r service; do
            [[ -n "$service" ]] || continue
            systemctl start "$service" || notice "WARNING: could not restore $service"
        done < "$STATE/active-services"
    fi
    notice 'HOME: BCI firewall removed, saved network state restoration attempted.'
}
status() {
    if [[ -f "$STATE/armed" ]]; then
        echo 'BCI Lock: ARMED (state flag present)'
    else
        echo 'BCI Lock: DISARMED (state flag absent)'
    fi
    if has_table; then
        echo 'BCI firewall: ACTIVE'
    else
        echo 'BCI firewall: INACTIVE'
        if [[ -f "$STATE/armed" ]]; then echo 'WARNING: armed state without firewall; run sudo bci-lock enforce'; fi
    fi
    if command -v nmcli >/dev/null; then
        printf 'NetworkManager networking: '; nmcli networking 2>/dev/null || true
    fi
    rfkill -n -o ID,TYPE,SOFT 2>/dev/null || true
}
# Serialize all state-changing operations to prevent overlapping arm/home calls.
case "${1:-status}" in
    arm|lock|home|unlock|enforce)
        need_root
        mkdir -p "$STATE"
        exec 9> "$STATE/operation.lock"
        flock -x 9
        ;;
esac
case "${1:-status}" in
    arm|lock) arm ;;
    home|unlock) home ;;
    status) status ;;
    enforce) enforce ;;
    *) echo 'Usage: sudo bci-lock {arm|home|status|enforce}' >&2; exit 2 ;;
esac
ENGINE

cat > "$work/bci-lock.service" <<'UNIT'
# BEACON COMPUTING INITIATIVE (BCI)
# Developer Seal: CJB-DEV-2026
# SHA-256 recorded in /etc/bci-lock/SHA256SUMS
[Unit]
Description=BCI Lock boot network-isolation enforcement
DefaultDependencies=no
After=local-fs.target nftables.service
Wants=network-pre.target
Before=network-pre.target NetworkManager.service systemd-networkd.service
ConditionPathExists=/var/lib/bci-lock/armed

[Service]
Type=oneshot
ExecStart=/usr/local/sbin/bci-lock enforce
RemainAfterExit=yes
TimeoutStartSec=30

[Install]
WantedBy=multi-user.target
UNIT

bash -n "$work/bci-lock"
if ! sudo nft list table inet bci_lock >/dev/null 2>&1; then
  sudo nft -c -f "$work/lock.nft"
fi
backup_time="$(date +%Y%m%d%H%M%S)"
for existing in /etc/bci-lock/lock.nft /usr/local/sbin/bci-lock /etc/systemd/system/bci-lock.service; do
    if sudo test -e "$existing"; then sudo cp -a "$existing" "${existing}.bak.${backup_time}"; fi
done
sudo install -d -m 0755 /etc/bci-lock
sudo install -d -m 0700 /var/lib/bci-lock
sudo install -m 0644 "$work/lock.nft" /etc/bci-lock/lock.nft
sudo install -m 0755 "$work/bci-lock" /usr/local/sbin/bci-lock
sudo install -m 0644 "$work/bci-lock.service" /etc/systemd/system/bci-lock.service
sudo ln -sfn /usr/local/sbin/bci-lock /usr/local/bin/bci-lock
sudo systemctl daemon-reload
sudo systemd-analyze verify /etc/systemd/system/bci-lock.service
sudo systemctl enable bci-lock.service
sudo sh -c 'sha256sum /etc/bci-lock/lock.nft /usr/local/sbin/bci-lock /etc/systemd/system/bci-lock.service > /etc/bci-lock/SHA256SUMS'

if [[ "${SHELL:-}" == */zsh || -f "$HOME/.zshrc" ]]; then
    if ! grep -Fq '# BEGIN BCI LOCK ALIASES' "$HOME/.zshrc" 2>/dev/null; then
        if [[ -f "$HOME/.zshrc" ]]; then cp -p "$HOME/.zshrc" "$HOME/.zshrc.bci-lock.$(date +%Y%m%d%H%M%S).bak"; fi
        cat >> "$HOME/.zshrc" <<'ALIASES'

# BEGIN BCI LOCK ALIASES
# BEACON COMPUTING INITIATIVE | Developer Seal: CJB-DEV-2026
alias arm='sudo bci-lock arm'
alias home='sudo bci-lock home'
alias bci-status='sudo bci-lock status'
# END BCI LOCK ALIASES
ALIASES
    fi
fi
printf '\n[BCI LOCK] Installed but NOT armed.\n'
printf '[BCI LOCK] Manifest hashes:\n'
sudo cat /etc/bci-lock/SHA256SUMS
printf '[BCI LOCK] Installer SHA-256:\n'
sha256sum "$0"
printf '[BCI LOCK] Manifest SHA-256:\n'
sudo sha256sum /etc/bci-lock/SHA256SUMS
if [[ -f "$HOME/.zshrc" ]]; then sha256sum "$HOME/.zshrc"; fi
printf '\nCommands: sudo bci-lock arm | sudo bci-lock home | sudo bci-lock status\n'
printf 'For zsh aliases, open a new terminal (arm, home, bci-status).\n'
