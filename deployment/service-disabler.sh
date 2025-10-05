sudo systemctl stop kale-kaj || true
sudo systemctl disable kale-kaj || true
sudo pkill -f kale-kaj || true

sudo lsof -i:8080
sudo netstat -tulpn | grep :8080
sudo kill -9 $(sudo lsof -ti:8080) 2>/dev/null || true
sudo rm -f /etc/systemd/system/kale-kaj.service
