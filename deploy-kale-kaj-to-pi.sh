#!/bin/bash

PASSWORD="$1"
JAR_PATH="deployment/kale-kaj.jar"
DEST="pi@192.168.1.165:/home/pi/freenov-kale-kaj/deployment"
REMOTE_JAR_PATH="/home/pi/freenov-kale-kaj/deployment/kale-kaj.jar"

if [ -z "$PASSWORD" ]; then
  echo "Usage: $0 <ssh_password>"
  exit 1
fi

if ! command -v sshpass &> /dev/null; then
  echo "Error: sshpass not installed. Install with: brew install sshpass"
  exit 2
fi

./gradlew bootJar

if [ ! -f "$JAR_PATH" ]; then
  echo "Error: $JAR_PATH not found."
  exit 3
fi

sshpass -p "$PASSWORD" ssh -o StrictHostKeyChecking=no pi@192.168.1.165 "mkdir -p /home/pi/freenov-kale-kaj/deployment" || {
  echo "Error: Unable to connect to Raspberry Pi or create directory."
  exit 4
}

sshpass -p "$PASSWORD" scp "$JAR_PATH" "$DEST" || {
  echo "Error: SCP file transfer failed."
  exit 5
}

echo "JAR successfully copied to Raspberry Pi."
