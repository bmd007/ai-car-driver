sh ./service-disabler.sh

sudo cp kale-kaj.service /etc/systemd/system/kale-kaj.service
sudo systemctl daemon-reload
sudo systemctl enable kale-kaj
sudo systemctl start kale-kaj

systemctl status kale-kaj --no-pager | head -5
