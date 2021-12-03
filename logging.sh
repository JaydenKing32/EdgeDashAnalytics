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
phone_dir="/storage/emulated/0/Movies/out"

# https://stackoverflow.com/a/30212526
if [[ -z "${serials[*]}" ]]; then
    # Get `adb.exe devices` output, remove \r and "device", skip first line
    read -ra serials -d '' <<<"$(tail -n +2 <<<"$(adb.exe devices | sed -r 's/(emulator.*)?(device)?\r$//')")"
fi

if [[ -z "${serials[*]}" ]]; then
    printf "No devices are connected, exiting\n"
    exit 1
fi

serial_string=$(join ", " "${serials[@]}")
printf "Collecting logs from %s\n" "${serial_string}"

out_dir="./out/${timestamp}"

if [[ ! -d "${out_dir}" ]]; then
    # Create necessary directories on computer
    mkdir -p "${out_dir}"
fi

for serial in "${serials[@]}"; do
    # Get filename of latest log
    filename="$(adb.exe -s "${serial}" shell ls "${phone_dir}" | tail -n 1)"
    # Sanitise filename
    filename="${filename//[^a-zA-Z0-9_.]/}"

    verbose_log="${out_dir}/verbose-${serial}.log"
    short_log="$out_dir/${serial}.log"

    # Copy verbose log from devices to computer
    adb.exe -s "${serial}" pull "${phone_dir}/${filename}" "${verbose_log}"
    # Filter out important messages from verbose logs
    pcre2grep '^\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}\s+\d+\s+\d+ \w Important' "${verbose_log}" >"${short_log}"
    # Append the last PowerMonitor message from verbose logs
    pcre2grep -M '^\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}\s+\d+\s+\d+ \w PowerMonitor: Power usage:\n.*\n.*\n.*' "${verbose_log}" | tail -4 >>"${short_log}"
done
