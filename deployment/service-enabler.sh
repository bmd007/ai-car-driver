cp kale-kaj.service /etc/systemd/system/kale-kaj.service
sudo systemctl daemon-reload
sudo systemctl enable kale-kaj
sudo systemctl start kale-kaj

journalctl -u kale-kaj -f
