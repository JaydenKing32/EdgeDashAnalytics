#!/usr/bin/env bash

# https://stackoverflow.com/a/2173421/8031185
trap 'trap - SIGTERM && kill -- -$$' SIGINT SIGTERM EXIT

usage() {
    printf "Usage: ./logging.sh [-s SERIAL_NUMBER]\n"
    exit 1
}

serials=""

while :; do
    case $1 in
    -s)
        if [[ -z "${2}" ]]; then
            printf "ERROR: No serial number specified.\n"
            usage
        else
            serials="${2}"
            shift 2
        fi
        ;;
    "")
        break
        ;;
    -?*)
        printf "ERROR: Unknown argument: %s\n" "${1}"
        usage
        ;;
    *)
        printf "ERROR: Unknown argument: %s\n" "${1}"
        usage
        ;;
    esac
done

timestamp="$(date +%Y%m%d_%H%M%S)"
phone_dir="/storage/emulated/0/Movies/out/${timestamp}"

if [[ -z ${serials} ]]; then
    # Get `adb.exe devices` output, remove \r and "device", skip first line
    serials=$(tail -n +2 <<<"$(adb.exe devices | sed -r 's/(emulator.*)?(device)?\r$//')")
fi

for serial in ${serials}; do
    # Create necessary directories on device
    adb.exe -s "${serial}" shell mkdir -p "${phone_dir}"
    # Get PID of app in order to filter out logs from other processes
    pid="$(adb.exe -s "${serial}" shell ps | awk '/com\.example\.edgedashanalytics/ {print $2}')"
    # Clear logcat buffer, should also manually increase buffer size with `adb logcat -G`
    adb.exe -s "${serial}" logcat -c
    # Write log messages to file on device
    adb.exe -s "${serial}" logcat --pid "${pid}" -f "${phone_dir}/${serial}.log" &
done

# Wait until all phones complete processing to continue
read -n1 -s -r -p $'Press any key to continue...\n'

out_dir="./out/${timestamp}"
verbose_dir="${out_dir}/verbose/"

if [[ ! -d "${verbose_dir}" ]]; then
    # Create necessary directories on computer
    mkdir -p "${verbose_dir}"
fi

for serial in ${serials}; do
    # Copy verbose log from devices to computer
    adb.exe -s "${serial}" pull "${phone_dir}/${serial}.log" "${verbose_dir}"
    # Filter out important messages from verbose logs
    grep -P '^\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}\s+\d+\s+\d+ \w Important' "${verbose_dir}/${serial}.log" >"$out_dir/${serial}.log"
done
