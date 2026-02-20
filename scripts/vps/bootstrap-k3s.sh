#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run as root: sudo bash scripts/vps/bootstrap-k3s.sh"
  exit 1
fi

echo "[1/6] System update"
apt-get update -y
apt-get upgrade -y

echo "[2/6] Basic security packages"
apt-get install -y ufw fail2ban curl ca-certificates gnupg

echo "[3/6] SSH hardening (keys only)"
mkdir -p /etc/ssh/sshd_config.d
cat >/etc/ssh/sshd_config.d/99-kobo-hardening.conf <<'CONF'
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin prohibit-password
PubkeyAuthentication yes
X11Forwarding no
AllowTcpForwarding yes
CONF
sshd -t
systemctl reload ssh || systemctl reload sshd

echo "[4/6] UFW policy"
ufw --force reset
ufw default deny incoming
ufw default allow outgoing
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable

echo "[5/6] fail2ban"
cat >/etc/fail2ban/jail.d/sshd.local <<'CONF'
[sshd]
enabled = true
port = ssh
maxretry = 5
findtime = 10m
bantime = 1h
CONF
systemctl enable --now fail2ban

echo "[6/6] Install k3s"
curl -sfL https://get.k3s.io | sh -
systemctl enable --now k3s

echo "Done. Check:"
echo "  sudo kubectl get nodes"
echo "  sudo ufw status verbose"
echo "  sudo fail2ban-client status sshd"

