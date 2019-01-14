#!/usr/bin/env bash

sleep 10

updateDir="update/new/"
backupDir="update/old"

files_to_copy=(
    conf/
    html/
    lib/
    burst.cmd
    burst.jar
    burst.sh
    genscoop.cl
    init-mysql.sql
    LICENSE.txt
    README.md
)

# Check update exists
if [[ ! -d "${updateDir}" ]]; then
    echo "Could not find update"
    exit
fi

# Clear previous backup
if [[ -d "${backupDir}" ]]; then
    rm -rf "${backupDir}"
fi

# Backup current install
for file in ${files_to_copy[@]}; do
    cp -Rf "${file}" "${backupDir}"
done

# Install new update
for file in ${files_to_copy[@]}; do
    rm -rf "${file}"
    cp -Rf "${updateDir}/${file}" "."
done

# Restore config
copy -f "$updateDir/conf/brs.properties" "conf/"
copy -f "$updateDir/conf/logging.properties" "conf/"

# Start BRS
nohup java -jar burst.jar &>/dev/null &
