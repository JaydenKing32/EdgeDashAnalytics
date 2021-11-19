#!/usr/bin/env bash

# https://stackoverflow.com/a/2173421
trap 'trap - SIGTERM && kill -- -$$' SIGINT SIGTERM EXIT

usage() {
    printf "Usage: ./logging.sh [-s SERIAL_NUMBER]\n"
    exit 1
}

# https://stackoverflow.com/a/17841619
function join() {
    local d=${1-} f=${2-}

    if shift 2; then
        printf %s "$f" "${@/#/$d}"
    fi
}

serials=""

while :; do
    case $1 in
    -s)
        if [[ -z "${2}" ]]; then
            printf "ERROR: No serial number specified.\n"
            usage
        else
            read -ra serials <<<"${2}"
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

# https://stackoverflow.com/a/30212526
if [[ -z "${serials[*]}" ]]; then
    # Get `adb.exe devices` output, remove \r and "device", skip first line
    read -ra serials -d '' <<<"$(tail -n +2 <<<"$(adb.exe devices | sed -r 's/(emulator.*)?(device)?\r$//')")"
fi

for serial in "${serials[@]}"; do
    # Create necessary directories on device
    adb.exe -s "${serial}" shell mkdir -p "${phone_dir}"
    # Get PID of app in order to filter out logs from other processes
    pid="$(adb.exe -s "${serial}" shell ps | awk '/com\.example\.edgedashanalytics/ {print $2}')"
    # Clear logcat buffer, should also manually increase buffer size with `adb logcat -G`
    adb.exe -s "${serial}" logcat -c
    # Write log messages to file on device
    adb.exe -s "${serial}" logcat --pid "${pid}" -f "${phone_dir}/${serial}.log" &
done

serial_string=$(join ", " "${serials[@]}")
printf "Collecting logs from %s\n" "${serial_string}"

# Wait until all phones complete processing to continue
read -rsp $'Press ENTER to continue...\n'

out_dir="./out/${timestamp}"
verbose_dir="${out_dir}/verbose/"

if [[ ! -d "${verbose_dir}" ]]; then
    # Create necessary directories on computer
    mkdir -p "${verbose_dir}"
fi

for serial in "${serials[@]}"; do
    # Copy verbose log from devices to computer
    adb.exe -s "${serial}" pull "${phone_dir}/${serial}.log" "${verbose_dir}"
    # Filter out important messages from verbose logs
    pcre2grep '^\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}\s+\d+\s+\d+ \w Important' "${verbose_dir}/${serial}.log" >"$out_dir/${serial}.log"
    # Append the last PowerMonitor message from verbose logs
    pcre2grep -M '^\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}\s+\d+\s+\d+ \w PowerMonitor: Power usage:\n.*\n.*\n.*' "${verbose_dir}/${serial}.log" | tail -4 >>"$out_dir/${serial}.log"
done
