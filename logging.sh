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
prune=false

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
    -p)
        prune=true
        shift 1
        break
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

# https://stackoverflow.com/a/30212526
if [[ -z "${serials[*]}" ]]; then
    # Get `adb.exe devices` output, remove \r and "device", skip first line
    read -ra serials -d '' <<<"$(tail -n +2 <<<"$(adb.exe devices | sed -r 's/(emulator.*)?(device)?\r$//')")"
fi

if [[ -z "${serials[*]}" ]]; then
    printf "No devices are connected, exiting\n"
    exit 1
fi

common_tags="system_server JobScheduler.Temperature OplusNetworkStackService alarmtest dbsw
DeviceInfoHidlClient ANDR-PERF-LM OplusNecManagerHelper OppoBaseWatchdog PlayCommon BatteryLed
DeviceStatisticsService OppoBaseBatteryService System.err ORMS_CORE MmTelFeatureConnection
OppoThermalStats WeatherS_WeatherServiceVersionUtils AudioFlinger Parcel t_app_installe
OplusExSystemServiceHelper AuthPII SingleClockView OppoRpmSubsystemManager OppoDevicePowerStats
OplusDexOptimizeManager ColorHansManager sensors-hal Finsky Mdaci ORMS_HAL ORMS_Platform
MediaMetadataRetriever MetadataRetrieverClient FrameDecoder io_stats"

if [[ $prune == true ]]; then
    for serial in "${serials[@]}"; do
        # Get UID of app, strip '\r' if it exists
        uid="$(adb.exe -s "${serial}" shell dumpsys package com.example.edgedashanalytics | awk -F= '/userId=/ {print $2}' | sed -r 's/\r$//')"
        # Whitelist the app's UID in logcat's prune list
        # https://developer.android.com/studio/command-line/logcat#options
        adb.exe -s "${serial}" logcat -P "\"${uid}\""

        for tag in $common_tags; do
            adb.exe -s "${serial}" shell setprop "log.tag.${tag}" ASSERT
        done
    done
    exit 0
fi

serial_string=$(join ", " "${serials[@]}")
printf "Collecting logs from %s\n" "${serial_string}"

phone_dir="/storage/emulated/0/Documents/out"
read -ra log_times -d '' <<<"$(adb.exe -s "${serials[0]}" shell ls "${phone_dir}" | sed 's/\.log\r$//')"
count=0

# Assumes that all connected devices have the same number of log files
for ((i = 0; i < ${#log_times[@]}; i++)); do
    log_time=${log_times[i]}
    out_dir="./out/${log_time}"

    if [[ ! -d "${out_dir}" ]]; then
        # Create necessary directories on computer
        mkdir -p "${out_dir}"
    fi

    for serial in "${serials[@]}"; do
        ((count++))
        filename="$(adb.exe -s "${serial}" shell ls "${phone_dir}" | sed 's/\r$//' | sed "$((i + 1))q;d")"
        verbose_log="${out_dir}/verbose-${serial}.log"
        short_log="$out_dir/${serial}.log"

        # Copy verbose log from devices to computer
        adb.exe -s "${serial}" pull "${phone_dir}/${filename}" "${verbose_log}"
        # Filter out important messages from verbose logs
        pcre2grep '^\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}\s+\d+\s+\d+ \w Important' "${verbose_log}" >"${short_log}"
        # Append the last PowerMonitor message from verbose logs
        pcre2grep -M '^\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}\s+\d+\s+\d+ \w PowerMonitor: Power usage:\n.*\n.*\n.*' "${verbose_log}" | tail -4 >>"${short_log}"
    done
done

echo "Collected ${#log_times[@]} sets of logs from ${#serials[@]} device(s), ${count} logs in total"
