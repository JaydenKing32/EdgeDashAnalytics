#!/usr/bin/env python3
import csv
import os
import re
from datetime import datetime, timedelta
from typing import Dict, List

algorithms = {
    "offline": "Offline",
    "round_robin": "Round-robin",
    "fastest": "Fastest",
    "least_busy": "Least-busy",
    "fastest_cpu": "Fastest-CPU",
    "most_cpu_cores": "Most-CPU-cores",
    "most_ram": "Most-RAM",
    "most_storage": "Most-storage",
    "highest_battery": "Highest-battery"
}
serial_numbers = {
    "X9BT": "R52R901X9BT",  # Samsung Galaxy Tab S7 FE/SM-T733
    "01BK": "18121FDF6001BK",  # Pixel 6
    "JRQY": "8A1X0JRQY",  # Pixel 3
    "43e2": "105a43e2",  # OPPO Find X2 Pro/CPH2025
    "dd83": "4885dd83",  # OnePlus 8/IN2013
    "2802": "ce12171c8a14c72802",  # Samsung Galaxy S8/SM-G950F
    "34d8": "00a6a4630f4e34d8",  # Nexus 5X
    "9c8f": "00b7a59265959c8f",  # Nexus 5X
    "1825": "0b3b6fd50c371825"  # Nexus 5
}
device_names = {
    "X9BT": "Tab S7 FE",
    "01BK": "Pixel 6",
    "JRQY": "Pixel 3",
    "43e2": "Find X2 Pro",
    "dd83": "OnePlus 8",
    "2802": "Galaxy S8",
    "34d8": "Nexus 5X",
    "9c8f": "Nexus 5X",
    "1825": "Nexus 5"
}
milliamp_devices = ["2802", "X9BT", "43e2"]
models = {
    "lite-model_ssd_mobilenet_v1_1_metadata_2.tflite": "MNV1",  # MobileNetV1
    "lite-model_efficientdet_lite0_detection_metadata_1.tflite": "EDL0",  # EfficientDet-Lite0
    "lite-model_efficientdet_lite1_detection_metadata_1.tflite": "EDL1",  # EfficientDet-Lite1
    "lite-model_efficientdet_lite2_detection_metadata_1.tflite": "EDL2",  # EfficientDet-Lite2
    "lite-model_efficientdet_lite3_detection_metadata_1.tflite": "EDL3",  # EfficientDet-Lite3
    "lite-model_efficientdet_lite4_detection_metadata_2.tflite": "EDL4",  # EfficientDet-Lite4
    "lite-model_movenet_singlepose_lightning_tflite_float16_4.tflite": "MvNL",  # MoveNet Lightning
    "lite-model_movenet_singlepose_thunder_tflite_float16_4.tflite": "MvNT"  # MoveNet Thunder
}

timestamp = r"^(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\s+\d+\s+\d+ "
trailing_whitespace = r"(?:\s+)?$"
re_timestamp = re.compile(r"^(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})\.(\d{3})(?=\s+\d+\s+\d+).*" + trailing_whitespace)
re_down = re.compile(
    timestamp +
    r"I Important: Successfully downloaded "
    r"(.*)\.\w+ in (\d*\.?\d*)s, (\d+)nW consumed" +
    trailing_whitespace
)
re_transfer = re.compile(
    timestamp +
    r"I Important: Completed downloading "
    r"(.*)\.\w+ from Endpoint{id=\S{4}, name=(.*) \[(\w+)]} in (\d*\.?\d*)s, (\d+)nW consumed" +
    trailing_whitespace
)
re_comp = re.compile(
    timestamp +
    r"D Important: Completed analysis of (.*)\.mp4 in (\d*\.?\d*)s, (\d+)nW consumed" +
    trailing_whitespace
)
re_wait = re.compile(
    timestamp +
    r"I Important: Wait time of (.*)\.mp4: (\d*\.?\d*)s" +
    trailing_whitespace
)
re_turnaround = re.compile(
    timestamp +
    r"I Important: Turnaround time of (.*)\.mp4: (\d*\.?\d*)s" +
    trailing_whitespace
)
re_pref = re.compile(timestamp + r"I Important: Preferences:" + trailing_whitespace)
re_total_power = re.compile(timestamp + r"D PowerMonitor:\s+Total: -?(\d+)nW" + trailing_whitespace)
re_average_power = re.compile(timestamp + r"D PowerMonitor:\s+Average: -?(\d+)\.(\d+)nW" + trailing_whitespace)
re_master = re.compile(timestamp + r"I Important:\s+Master: (\w+)" + trailing_whitespace)
re_network = re.compile(timestamp + r"I Important:\s+Wi-Fi: (\w+)" + trailing_whitespace)

offline_header = [
    "Filename",
    "Down time (s)",
    "Proc time (s)",
    "Wait time (s)",
    "Turn time (s)",
    "Net power (mW)",
    "Proc power (mW)"
]
online_header = [
    "Filename",
    "Down time (s)",
    "Tran time (s)",
    "Ret time (s)",
    "Proc time (s)",
    "Wait time (s)",
    "Turn time (s)",
    "Net power (mW)",
    "Proc power (mW)",
]
summary_header = [
    "Master",
    "Workers",
    "Algorithm",
    "Down time (s)",
    "Tran time (s)",
    "Ret time (s)",
    "Proc time (s)",
    "Wait time (s)",
    "Turn time (s)",
    "Net power (mW)",
    "Proc power (mW)",
    "Actual power (mW)",
    "Actual time (s)",
    "Human time",
    "Network",
    "Directory"
]

excel = False
proper_name = False


class Video:
    def __init__(self, name: str, down_time: float = 0, transfer_time: float = 0, analysis_time: float = 0,
                 wait_time: float = 0, return_time: float = 0, down_power: float = 0, transfer_power: float = 0,
                 analysis_power: float = 0, turnaround_time: float = 0):
        self.name = name
        self.down_time = down_time
        self.transfer_time = transfer_time
        self.analysis_time = analysis_time
        self.return_time = return_time
        self.wait_time = wait_time
        self.turnaround_time = turnaround_time
        self.down_power = down_power
        self.transfer_power = transfer_power
        self.analysis_power = analysis_power

    def get_stats(self) -> List[str]:
        return [
            self.name,
            f"{self.down_time:.3f}",
            f"{self.transfer_time:.3f}" if self.transfer_time != 0 else "n/a",
            f"{self.return_time:.3f}" if self.return_time != 0 else "n/a",
            f"{self.analysis_time:.3f}",
            f"{self.wait_time:.3f}",
            f"{self.turnaround_time:.3f}",
            f"{self.down_power + self.transfer_power:.3f}",
            f"{self.analysis_power:.3f}"
        ]

    def get_offline_stats(self) -> List[str]:
        return [
            self.name,
            f"{self.down_time:.3f}",
            f"{self.analysis_time:.3f}",
            f"{self.wait_time:.3f}",
            f"{self.turnaround_time:.3f}",
            f"{self.down_power + self.transfer_power:.3f}",
            f"{self.analysis_power:.3f}"
        ]


class Device:
    def __init__(self, name: str):
        self.name = name
        self.videos = {}  # type: Dict[str, Video]
        self.total_power = 0.0
        self.average_power = 0.0
        self.network = ""

    def set_network(self, log_path: str):
        with open(log_path, 'r', encoding="utf-8") as log:
            for line in log:
                network_match = re_network.match(line)

                if network_match is not None:
                    network = network_match.group(2)

                    if network == "offline":
                        self.network = "Direct"
                    else:
                        self.network = "Dash 5GHz" if "5G" in network else "Dash 2.4GHz"
                    return


class Analysis:
    def __init__(self, log_dir: str, master: str, devices: Dict[str, Device], videos: Dict[str, Video]):
        self.log_dir = log_dir
        self.master = master
        self.master_path = f"{os.path.join(self.log_dir, self.master)}.log"

        self.devices = devices
        self.videos = videos
        self.seg_num = -1
        self.delay = -1
        self.nodes = len([log for log in os.listdir(self.log_dir) if is_log(log)])
        self.algorithm = "offline"
        self.object_model = ""
        self.pose_model = ""
        self.local = False
        self.dual_download = False

        self.down_time = -1.0
        self.transfer_time = -1.0
        self.return_time = -1.0
        self.analysis_time = -1.0
        self.wait_time = -1.0
        self.turnaround_time = -1.0
        self.network_power = -1.0
        self.analysis_power = -1.0
        self.total_time = get_total_time(self.master_path)

        self.avg_down_time = 0.0
        self.avg_transfer_time = 0.0
        self.avg_return_time = 0.0
        self.avg_analysis_time = 0.0
        self.avg_wait_time = 0.0
        self.avg_turnaround_time = 0.0
        self.avg_network_power = 0.0
        self.avg_analysis_power = 0.0
        self.avg_total_power = 0.0

        self.parse_preferences()

    def get_master_short_name(self) -> str:
        return self.master[-4:]

    def get_master_full_name(self) -> str:
        return get_device_name(self.get_master_short_name())

    def get_algorithm_name(self) -> str:
        return algorithms[self.algorithm]

    def get_time_seconds_string(self) -> str:
        return f"{self.total_time.total_seconds():.3f}"

    def get_time_human_string(self) -> str:
        return format_timedelta(self.total_time)

    def get_time_string(self) -> str:
        return f"{self.get_time_seconds_string()} ({self.get_time_human_string()})"

    def get_sub_log_dir(self) -> str:
        i = self.log_dir.index(os.path.sep) + 1
        return self.log_dir[i:]

    def get_total_stats(self) -> List[str]:
        return [
            f"{self.down_time:.3f}",
            f"{self.transfer_time:.3f}" if self.transfer_time != 0 else "n/a",
            f"{self.return_time:.3f}" if self.transfer_time != 0 else "n/a",
            f"{self.analysis_time:.3f}",
            f"{self.wait_time:.3f}",
            f"{self.turnaround_time:.3f}",
            f"{self.network_power:.3f}",
            f"{self.analysis_power:.3f}"
        ]

    def set_average_stats(self):
        video_count = len(self.videos)

        self.avg_down_time = self.down_time / video_count
        self.avg_transfer_time = self.transfer_time / video_count
        self.avg_return_time = self.return_time / video_count
        self.avg_analysis_time = self.analysis_time / video_count
        self.avg_wait_time = self.wait_time / video_count
        self.avg_turnaround_time = self.turnaround_time / video_count
        self.avg_network_power = self.network_power / video_count
        self.avg_analysis_power = self.analysis_power / video_count
        self.avg_total_power = self.get_total_power() / video_count

    def get_average_stats(self) -> List[str]:
        return [
            f"{self.avg_down_time:.3f}",
            f"{self.avg_transfer_time:.3f}" if self.avg_transfer_time != 0 else "n/a",
            f"{self.avg_return_time:.3f}" if self.avg_return_time != 0 else "n/a",
            f"{self.avg_analysis_time:.3f}",
            f"{self.avg_wait_time:.3f}",
            f"{self.avg_turnaround_time:.3f}",
            f"{self.avg_network_power:.3f}",
            f"{self.avg_analysis_power:.3f}"
        ]

    def get_total_power(self) -> float:
        return sum(d.total_power for d in self.devices.values())

    def get_total_average_power(self) -> float:
        return sum(d.average_power for d in self.devices.values())

    def get_network(self) -> str:
        return next(iter(self.devices.values())).network if all(d.network for d in self.devices.values()) else "Direct"

    def get_worker_string(self) -> str:
        return "-".join(get_device_name(name) for name in sorted(
            [device.name for device in self.devices.values() if device.name != self.get_master_short_name()],
            key=lambda d: list(serial_numbers.keys()).index(d)
        ))

    def parse_preferences(self):
        with open(self.master_path, 'r', encoding="utf-8") as master_log:
            for line in master_log:
                pref = re_pref.match(line)

                if pref is not None:
                    master_log.readline()  # skip master line
                    object_model_filename = master_log.readline().split()[-1]
                    pose_model_filename = master_log.readline().split()[-1]
                    algo = master_log.readline().split()[-1]
                    local = master_log.readline().split()[-1] == "true"
                    master_log.readline()  # skip auto-download line
                    seg = master_log.readline().split()[-1] == "true"
                    seg_num = int(master_log.readline().split()[-1])
                    if not seg:
                        seg_num = 1
                    delay = int(master_log.readline().split()[-1])
                    dual_download = master_log.readline().split()[-1] == "true"

                    self.algorithm = algo
                    self.seg_num = seg_num
                    self.object_model = models[object_model_filename]
                    self.pose_model = models[pose_model_filename]
                    self.local = local
                    self.delay = delay
                    self.dual_download = dual_download
                    break

        self.down_time = sum(v.down_time for v in self.videos.values())
        self.transfer_time = sum(v.transfer_time for v in self.videos.values())
        self.return_time = sum(v.return_time for v in self.videos.values())
        self.analysis_time = sum(v.analysis_time for v in self.videos.values())
        self.wait_time = sum(v.wait_time for v in self.videos.values())
        self.turnaround_time = sum(v.turnaround_time for v in self.videos.values())
        self.network_power = sum(v.down_power for v in self.videos.values())
        self.analysis_power = sum(v.analysis_power for v in self.videos.values())

    def __str__(self) -> str:
        master = self.get_master_full_name()
        local = self.local
        seg = abs(self.seg_num)
        delay = abs(self.delay)
        nodes = self.nodes
        algo = "Duo" if len(self.devices) == 2 else self.get_algorithm_name()

        return f"{master}-{local}-{seg}-{delay}-{nodes}-{algo}"


def is_master(log_path: str) -> bool:
    if not is_log(log_path):
        return False

    with open(log_path, 'r', encoding="utf-8") as log:
        # Check first 10 lines for preferences message
        for _ in range(10):
            pref = re_pref.match(log.readline())

            if pref is not None:
                master = re_master.match(log.readline())

                if master is not None:
                    return master.group(2) == "true"
    return False


def is_log(filename: str) -> bool:
    # Exclude verbose logs
    return filename.endswith(".log") and "verbose" not in filename


def get_master(log_dir: str) -> str:
    for file in [os.path.join(log_dir, filename) for filename in os.listdir(log_dir)]:
        if os.path.isfile(file) and is_master(file):
            return file
    print("Could not find master log")
    return ""


def get_basename_sans_ext(filename: str) -> str:
    return os.path.splitext(os.path.basename(filename))[0]


def excel_format(string: str) -> str:
    # \t prevents excel cell type conversion
    return f"\t{string}" if excel and string else string


def get_device_name(short_serial: str) -> str:
    return device_names[short_serial] if proper_name and short_serial else short_serial


def check_video_count(videos: List[Video], log_dir: str, print_message: bool) -> int:
    # There are 40 test videos, if the logs do not specify 40 videos, then something went wrong during a test run
    expected_video_count = 1200
    video_count = len(videos)

    if print_message and video_count != expected_video_count:
        print(f"Unexpected video count: {video_count} in {os.path.basename(log_dir)}")
    return expected_video_count - video_count


def get_video_name(name: str) -> str:
    sep = '!'
    if sep in name:
        return name.split(sep)[0]
    else:
        return name


def parse_power(power: str, device_name: str = None) -> float:
    # Convert from nW to mW
    if device_name in milliamp_devices:
        return int(power) / 1000
    else:
        return int(power) / 1000000


def compare_offline_files(files: List[str]) -> int:
    try:
        filename = next(filter(is_log, files))
    except StopIteration:
        return -1

    serial = get_basename_sans_ext(filename)
    if serial not in serial_numbers.values():
        return -1

    return list(serial_numbers.values()).index(serial)


def timestamp_to_datetime(line: str) -> datetime:
    match = re_timestamp.match(line)

    year = 2022
    month = int(match.group(1))
    day = int(match.group(2))
    hour = int(match.group(3))
    minute = int(match.group(4))
    second = int(match.group(5))
    microsecond = int(int(match.group(6)) * 1000)

    return datetime(year, month, day, hour, minute, second, microsecond)


def format_timedelta(time: timedelta) -> str:
    hours, rem = divmod(time.total_seconds(), 3600)
    minutes, seconds = divmod(rem, 60)

    return f"{hours:.0f}:{minutes:02.0f}:{seconds:06.3f}"


def get_total_time(master_log_file: str) -> timedelta:
    start = None
    end = None

    with open(master_log_file, 'r', encoding="utf-8") as master_log:
        for line in master_log:
            if start is None:
                pref = re_pref.match(line)

                if pref is not None:
                    start = line
            down_match = re_transfer.match(line)
            comp_match = re_comp.match(line)

            if down_match is not None:
                end = line
            elif comp_match is not None:
                end = line
    return timestamp_to_datetime(end) - timestamp_to_datetime(start)


def parse_master_log(devices: Dict[str, Device], master_filename: str, log_dir: str) -> Dict[str, Video]:
    videos = {}  # type: Dict[str, Video]
    log_path = os.path.join(log_dir, master_filename)

    with open(log_path, 'r', encoding="utf-8") as master_log:
        master_name = master_filename[-8:-4]
        master = devices[master_name]
        master.set_network(log_path)

        for line in master_log:
            down = re_down.match(line)
            transfer = re_transfer.match(line)
            comp = re_comp.match(line)
            wait = re_wait.match(line)
            turn = re_turnaround.match(line)
            total_power = re_total_power.match(line)
            average_power = re_average_power.match(line)

            if down is not None:
                video_name = get_video_name(down.group(2))
                down_time = float(down.group(3))
                down_power = parse_power(down.group(4), master_name)

                video = Video(name=video_name, down_time=down_time, down_power=down_power)
                videos[video_name] = video
                master.videos[video_name] = video
            elif transfer is not None:
                video_name = get_video_name(transfer.group(2))
                return_time = float(transfer.group(5))
                return_power = parse_power(transfer.group(6), master_name)

                videos[video_name].return_time += return_time
                videos[video_name].transfer_power += return_power
            elif comp is not None:
                video_name = get_video_name(comp.group(2))
                analysis_time = float(comp.group(3))
                analysis_power = parse_power(comp.group(4), master_name)

                videos[video_name].analysis_time += analysis_time
                videos[video_name].analysis_power += analysis_power
            elif wait is not None:
                video_name = get_video_name(wait.group(2))
                wait_time = float(wait.group(3))

                videos[video_name].wait_time = wait_time
            elif turn is not None:
                video_name = get_video_name(turn.group(2))
                turn_time = float(turn.group(3))

                videos[video_name].turnaround_time = turn_time
            elif total_power is not None:
                master.total_power = parse_power(total_power.group(2), master_name)
            elif average_power is not None:
                master.average_power = parse_power(average_power.group(2), master_name)

    return videos


def parse_worker_logs(devices: Dict[str, Device], videos: Dict[str, Video], log_dir: str, master_sn: str):
    worker_logs = [log for log in os.listdir(log_dir) if is_log(log) and master_sn not in log]

    for log in worker_logs:
        log_path = os.path.join(log_dir, log)

        with open(log_path, 'r', encoding="utf-8") as work_log:
            device_name = log[-8:-4]
            worker = devices[device_name]
            worker.set_network(log_path)

            for line in work_log:
                transfer = re_transfer.match(line)
                comp = re_comp.match(line)
                wait = re_wait.match(line)
                turn = re_turnaround.match(line)
                total_power = re_total_power.match(line)
                average_power = re_average_power.match(line)

                if transfer is not None:
                    video_name = get_video_name(transfer.group(2))
                    transfer_time = float(transfer.group(5))
                    transfer_power = parse_power(transfer.group(6), device_name)

                    video = videos[video_name]
                    video.transfer_time += transfer_time
                    video.transfer_power += transfer_power

                    worker.videos[video_name] = video
                elif comp is not None:
                    video_name = get_video_name(comp.group(2))
                    analysis_time = float(comp.group(3))
                    analysis_power = parse_power(comp.group(4), device_name)

                    videos[video_name].analysis_time += analysis_time
                    videos[video_name].analysis_power += analysis_power
                elif wait is not None:
                    video_name = get_video_name(wait.group(2))
                    wait_time = float(wait.group(3))

                    videos[video_name].wait_time = wait_time
                elif turn is not None:
                    video_name = get_video_name(turn.group(2))
                    turn_time = float(turn.group(3))

                    videos[video_name].turnaround_time = turn_time
                elif total_power is not None:
                    worker.total_power = parse_power(total_power.group(2), device_name)
                elif average_power is not None:
                    worker.average_power = parse_power(average_power.group(2), device_name)


def parse_offline_log(log_path: str) -> Analysis:
    videos = {}  # type: Dict[str, Video]

    with open(log_path, 'r', encoding="utf-8") as offline_log:
        device_sn = get_basename_sans_ext(log_path)
        device_name = device_sn[-4:]
        device = Device(device_name)
        device.set_network(log_path)

        parent_path = os.path.dirname(log_path)
        run = Analysis(parent_path, device_sn, {device_name: device}, videos)
        run.local = False
        run.algorithm = "offline"

        for line in offline_log:
            down = re_down.match(line)
            comp = re_comp.match(line)
            wait = re_wait.match(line)
            turn = re_turnaround.match(line)
            total_power = re_total_power.match(line)
            average_power = re_average_power.match(line)

            if down is not None:
                video_name = get_video_name(down.group(2))
                down_time = float(down.group(3))
                down_power = parse_power(down.group(4), device_name)

                videos[video_name] = Video(name=video_name, down_time=down_time, down_power=down_power)
            elif comp is not None:
                video_name = get_video_name(comp.group(2))
                analysis_time = float(comp.group(3))
                analysis_power = parse_power(comp.group(4), device_name)

                videos[video_name].analysis_time = analysis_time
                videos[video_name].analysis_power = analysis_power
            elif wait is not None:
                video_name = get_video_name(wait.group(2))
                wait_time = float(wait.group(3))

                videos[video_name].wait_time = wait_time
            elif turn is not None:
                video_name = get_video_name(turn.group(2))
                turn_time = float(turn.group(3))

                videos[video_name].turnaround_time = turn_time
            elif total_power is not None:
                device.total_power = parse_power(total_power.group(2), device_name)
            elif average_power is not None:
                device.average_power = parse_power(average_power.group(2), device_name)
    return run


def parse_offline_logs(log_dir: str, runs: List[Analysis]):
    # Offline directories should only contain two files, normal log and verbose log
    for (path, _, files) in sorted(
            [(path, _, files) for (path, _, files) in os.walk(log_dir) if len(files) == 2],
            key=lambda pdf: compare_offline_files(pdf[2])
    ):
        for log in files:
            # Skip verbose logs and non-log files
            if not is_log(log):
                continue

            log_path = os.path.join(path, log)
            run = parse_offline_log(log_path)
            check_video_count(list(run.videos.values()), path, True)

            total_down_time = sum(v.down_time for v in run.videos.values())
            total_analysis_time = sum(v.analysis_time for v in run.videos.values())
            total_wait_time = sum(v.wait_time for v in run.videos.values())
            total_turnaround_time = sum(v.turnaround_time for v in run.videos.values())
            total_network_power = sum(v.down_power for v in run.videos.values())
            total_analysis_power = sum(v.analysis_power for v in run.videos.values())

            run.down_time = total_down_time
            run.analysis_time = total_analysis_time
            run.wait_time = total_wait_time
            run.turnaround_time = total_turnaround_time
            run.network_power = total_network_power
            run.analysis_power = total_analysis_power
            run.set_average_stats()

            runs.append(run)


def write_offline_runs(runs: List[Analysis], writer):
    writer.writerow(["Offline"])

    for run in [r for r in runs if r.algorithm == "offline"]:
        videos = run.videos

        missed = check_video_count(list(videos.values()), run.log_dir, False)
        writer.writerow([f"Device: {run.get_master_full_name()}"])
        writer.writerow([
            f"Download Delay: {run.delay}",
            f"Object Model: {run.object_model}",
            f"Pose Model: {run.pose_model}",
            f"Dual: {run.dual_download}",
            f"MISSING {missed}" if missed else "",
            "",
            "",
            f"Dir: {run.get_sub_log_dir()}",
        ])
        writer.writerow(offline_header)

        for video in videos.values():
            writer.writerow(video.get_offline_stats())

        writer.writerow([
            "Total",
            f"{run.down_time:.3f}",
            f"{run.analysis_time:.3f}",
            f"{run.wait_time:.3f}",
            f"{run.turnaround_time:.3f}",
            f"{run.network_power:.3f}",
            f"{run.analysis_power:.3f}"
        ])
        writer.writerow([
            "Average",
            f"{run.avg_down_time:.3f}",
            f"{run.avg_analysis_time:.3f}",
            f"{run.avg_wait_time:.3f}",
            f"{run.avg_turnaround_time:.3f}",
            f"{run.avg_network_power:.3f}",
            f"{run.avg_analysis_power:.3f}"
        ])
        writer.writerow([
            "Actual total time", run.get_time_string(),
            "Actual total power", f"{run.get_total_power():.3f}"
        ])
    writer.writerow('')


def write_online_run(run: Analysis, writer):
    missed = check_video_count(list(run.videos.values()), run.log_dir, True)

    writer.writerow([
        f"Master: {run.get_master_full_name()}",
        f"Segments: {run.seg_num}",
        f"Nodes: {run.nodes}",
        f"Algorithm: {run.get_algorithm_name()}"
    ])
    writer.writerow([
        f"Local Processing: {run.local}",
        f"Download Delay: {run.delay}",
        f"Object Model: {run.object_model}",
        f"Pose Model: {run.pose_model}",
        f"Dual: {run.dual_download}",
        f"MISSING {missed}" if missed else "",
        "",
        f"Dir: {run.get_sub_log_dir()}"
    ])

    # Cannot cleanly separate videos between devices when segmentation is used
    if run.seg_num > 1:
        writer.writerow(["Device", "Actual Power (mW)", "Network"])
        for device_name, device in run.devices.items():
            writer.writerow([get_device_name(device_name), f"{device.total_power:.3f}", device.network])

        writer.writerow(online_header)

        for video in run.videos.values():
            writer.writerow(video.get_stats())

        writer.writerow(["Total"] + run.get_total_stats())
        writer.writerow(["Average"] + run.get_average_stats())

    else:
        for device_name, device in run.devices.items():
            writer.writerow([f"Device: {get_device_name(device_name)}", f"Network: {device.network}"])

            # Master videos list should only contain videos that haven't been transferred
            videos = [v for v in device.videos.values() if v.transfer_time == 0] \
                if device_name == run.get_master_short_name() \
                else list(device.videos.values())

            if not videos:
                writer.writerow(["Did not analyse any videos"])
                continue

            writer.writerow(online_header + ["Actual total power", f"{device.total_power:.3f}"])

            for video in videos:
                writer.writerow(video.get_stats())

            video_count = len(videos)
            if video_count > 1:
                total_down_time = sum(v.down_time for v in videos)
                total_transfer_time = sum(v.transfer_time for v in videos)
                total_return_time = sum(v.return_time for v in videos)
                total_analysis_time = sum(v.analysis_time for v in videos)
                total_wait_time = sum(v.wait_time for v in videos)
                total_turnaround_time = sum(v.turnaround_time for v in videos)
                total_network_power = sum(v.down_power + v.transfer_power for v in videos)
                total_analysis_power = sum(v.analysis_power for v in videos)

                writer.writerow([
                    "Total",
                    f"{total_down_time:.3f}",
                    f"{total_transfer_time:.3f}" if total_transfer_time != 0 else "n/a",
                    f"{total_return_time:.3f}" if total_return_time != 0 else "n/a",
                    f"{total_analysis_time:.3f}",
                    f"{total_wait_time:.3f}",
                    f"{total_turnaround_time:.3f}",
                    f"{total_network_power:.3f}",
                    f"{total_analysis_power:.3f}"
                ])

                writer.writerow([
                    "Average",
                    f"{total_down_time / video_count:.3f}",
                    f"{total_transfer_time / video_count:.3f}" if total_transfer_time != 0 else "n/a",
                    f"{total_return_time / video_count:.3f}" if total_return_time != 0 else "n/a",
                    f"{total_analysis_time / video_count:.3f}",
                    f"{total_wait_time / video_count:.3f}",
                    f"{total_turnaround_time / video_count:.3f}",
                    f"{total_network_power / video_count:.3f}",
                    f"{total_analysis_power / video_count:.3f}"
                ])

        if sum(1 for device in run.devices.values() if device) > 1:
            writer.writerow(["Combined total"] + run.get_total_stats())
            writer.writerow(["Combined average"] + run.get_average_stats())

    writer.writerow([
        "Actual total time", run.get_time_string(),
        "Actual total power", f"{run.get_total_power():.3f}"
    ])
    writer.writerow('')


def write_spread_totals(runs: List[Analysis], writer):
    writer.writerow(summary_header + ["Summary of totals"])

    for run in runs:
        writer.writerow([
            run.get_master_full_name(),
            excel_format(run.get_worker_string()),
            "Duo" if len(run.devices) == 2 else run.get_algorithm_name(),
            f"{run.down_time:.3f}",
            f"{run.transfer_time:.3f}" if run.transfer_time > 0 else "n/a",
            f"{run.return_time:.3f}" if run.transfer_time > 0 else "n/a",
            f"{run.analysis_time:.3f}",
            f"{run.wait_time:.3f}",
            f"{run.turnaround_time:.3f}",
            f"{run.network_power:.3f}",
            f"{run.analysis_power:.3f}",
            f"{run.get_total_power():.3f}",
            run.get_time_seconds_string(),
            excel_format(run.get_time_human_string()),
            run.get_network(),
            run.log_dir
        ])

    time = timedelta(seconds=sum(run.total_time.total_seconds() for run in runs))
    writer.writerow(["Total"] + ['', ''] + [
        f"{sum(run.down_time for run in runs):.3f}",
        f"{sum(run.transfer_time for run in runs):.3f}",
        f"{sum(run.return_time for run in runs):.3f}",
        f"{sum(run.analysis_time for run in runs):.3f}",
        f"{sum(run.wait_time for run in runs):.3f}",
        f"{sum(run.turnaround_time for run in runs):.3f}",
        f"{sum(run.network_power for run in runs):.3f}",
        f"{sum(run.analysis_power for run in runs):.3f}",
        f"{sum(run.get_total_power() for run in runs):.3f}",
        f"{sum(run.total_time.total_seconds() for run in runs):.3f}",
        excel_format(format_timedelta(time))
    ])
    writer.writerow('')


def write_spread_averages(runs: List[Analysis], writer):
    writer.writerow(summary_header + ["Summary of averages"])

    for run in runs:
        writer.writerow([
            run.get_master_full_name(),
            excel_format(run.get_worker_string()),
            "Duo" if len(run.devices) == 2 else run.get_algorithm_name(),
            f"{run.avg_down_time:.3f}",
            f"{run.avg_transfer_time:.3f}" if run.avg_transfer_time > 0 else "n/a",
            f"{run.avg_return_time:.3f}" if run.avg_transfer_time > 0 else "n/a",
            f"{run.avg_analysis_time:.3f}",
            f"{run.avg_wait_time:.3f}",
            f"{run.avg_turnaround_time:.3f}",
            f"{run.avg_network_power:.3f}",
            f"{run.avg_analysis_power:.3f}",
            f"{run.avg_total_power:.3f}",
            run.get_time_seconds_string(),
            excel_format(run.get_time_human_string()),
            run.get_network(),
            run.log_dir
        ])

    time = timedelta(seconds=sum(run.total_time.total_seconds() for run in runs) / len(runs))
    writer.writerow(["Average"] + ['', ''] + [
        f"{sum(run.avg_down_time for run in runs) / len(runs):.3f}",
        f"{sum(run.avg_transfer_time for run in runs) / len(runs):.3f}",
        f"{sum(run.avg_return_time for run in runs) / len(runs):.3f}",
        f"{sum(run.avg_analysis_time for run in runs) / len(runs):.3f}",
        f"{sum(run.avg_wait_time for run in runs) / len(runs):.3f}",
        f"{sum(run.avg_turnaround_time for run in runs) / len(runs):.3f}",
        f"{sum(run.avg_network_power for run in runs) / len(runs):.3f}",
        f"{sum(run.avg_analysis_power for run in runs) / len(runs):.3f}",
        f"{sum(run.get_total_power() for run in runs) / len(runs):.3f}",
        f"{sum(run.total_time.total_seconds() for run in runs) / len(runs):.3f}",
        excel_format(format_timedelta(time))
    ])
    writer.writerow('')


def write_tables(runs: List[Analysis], writer):
    writer.writerow(["Offline tests"])
    writer.writerow([
        "Device",
        "Download time (s)",
        "Processing time (s)",
        "Wait time (s)",
        "Turnaround time (s)",
        "Network power (mW)",
        "Processing power (mW)",
        "Actual power (mW)",
        "Actual time (s)"
    ])

    for run in [r for r in runs if r.algorithm == "offline"]:
        writer.writerow([
            run.get_master_full_name(),
            f"{run.avg_down_time:.3f}",
            f"{run.avg_analysis_time:.3f}",
            f"{run.avg_wait_time:.3f}",
            f"{run.avg_turnaround_time:.3f}",
            f"{run.avg_network_power:.3f}",
            f"{run.avg_analysis_power:.3f}",
            f"{run.get_total_power():.3f}",
            run.get_time_seconds_string()
        ])
    writer.writerow('')

    writer.writerow(["Two-node tests"])
    writer.writerow([
        "Worker",
        "Transfer time (s)",
        "Return time (s)",
        "Processing time (s)",
        "Wait time (s)",
        "Turnaround time (s)",
        "Network power (mW)",
        "Processing power (mW)",
        "Actual power (mW)",
        "Actual time (s)"
    ])
    prev_master = ""

    for run in [r for r in runs if len(r.devices) == 2]:
        if run.get_master_full_name() != prev_master:
            prev_master = run.get_master_full_name()
            down_times = [
                r.avg_down_time for r in runs if r.get_master_full_name() == prev_master and len(r.devices) == 2]
            avg_down_time = sum(t for t in down_times) / len(down_times)
            writer.writerow([
                "Master:", prev_master,
                "Download time (s):", f"{avg_down_time:.3f}"
            ])

        writer.writerow([
            run.get_worker_string(),
            f"{run.avg_transfer_time:.3f}",
            f"{run.avg_return_time:.3f}",
            f"{run.avg_analysis_time:.3f}",
            f"{run.avg_wait_time:.3f}",
            f"{run.avg_turnaround_time:.3f}",
            f"{run.avg_network_power:.3f}",
            f"{run.avg_analysis_power:.3f}",
            f"{run.get_total_power():.3f}",
            run.get_time_seconds_string()
        ])
    writer.writerow('')

    writer.writerow(["Three-node tests"])
    writer.writerow([
        "Algorithm",
        "Transfer time (s)",
        "Return time (s)",
        "Processing time (s)",
        "Wait time (s)",
        "Turnaround time (s)",
        "Network power (mW)",
        "Processing power (mW)",
        "Actual power (mW)",
        "Actual time (s)"
    ])
    prev_master = ""
    prev_workers = ""

    for run in [r for r in runs if len(r.devices) == 3]:
        if run.get_master_full_name() != prev_master or run.get_worker_string() != prev_workers:
            prev_master = run.get_master_full_name()
            prev_workers = run.get_worker_string()

            down_times = [
                r.avg_down_time for r in runs if
                r.get_master_full_name() == prev_master and r.get_worker_string() == prev_workers]
            avg_down_time = sum(t for t in down_times) / len(down_times)
            writer.writerow([
                "Master:", prev_master,
                "Workers:", prev_workers,
                "Download time (s):", f"{avg_down_time:.3f}"
            ])

        writer.writerow([
            run.get_algorithm_name(),
            f"{run.avg_transfer_time:.3f}",
            f"{run.avg_return_time:.3f}",
            f"{run.avg_analysis_time:.3f}",
            f"{run.avg_wait_time:.3f}",
            f"{run.avg_turnaround_time:.3f}",
            f"{run.avg_network_power:.3f}",
            f"{run.avg_analysis_power:.3f}",
            f"{run.get_total_power():.3f}",
            run.get_time_seconds_string()
        ])


def make_spreadsheet(root: str, out: str, append: bool, sort: bool, full_results: bool, table: bool):
    root = os.path.normpath(root)
    runs = []  # type: List[Analysis]
    write_mode = 'a' if append else 'w'

    with open(out, write_mode, newline='', encoding="utf-8") as csv_f:
        writer = csv.writer(csv_f)

        parse_offline_logs(root, runs)
        if not table and full_results:
            write_offline_runs(runs, writer)

        for (path, _, files) in sorted(
                [(path, _, files) for (path, _, files) in os.walk(root) if len(files) > 2]):
            master = get_master(path)
            if not master:
                continue

            master_sn = get_basename_sans_ext(master)
            logs = [log for log in os.listdir(path) if is_log(log)]
            devices = {device[-8:-4]: Device(device[-8:-4]) for device in logs}  # Initialise device dictionary

            videos = parse_master_log(devices, f"{master_sn}.log", path)
            parse_worker_logs(devices, videos, path, master_sn)

            run = Analysis(path, master_sn, devices, videos)
            run.set_average_stats()
            runs.append(run)

            if not table and full_results:
                write_online_run(run, writer)

        if sort:
            runs.sort(key=lambda r: (
                r.nodes,
                r.seg_num,
                r.delay,
                r.local,
                r.get_master_short_name(),
                list(algorithms.keys()).index(r.algorithm),
                r.get_sub_log_dir()
            ))
        else:
            runs.sort(key=lambda r: r.log_dir)

        if table:
            write_tables(runs, writer)
        else:
            write_spread_totals(runs, writer)
            write_spread_averages(runs, writer)


if __name__ == "__main__":
    from argparse import ArgumentParser

    parser = ArgumentParser(description="Generates spreadsheets from logs")
    parser.add_argument("-d", "--dir", default="out", help="directory of logs")
    parser.add_argument("-o", "--output", default="results.csv", help="name of output file")
    parser.add_argument("-a", "--append", action="store_true", help="append to results file instead of overwriting it")
    parser.add_argument("-s", "--sort", action="store_true", help="sort summaries based on config instead of log paths")
    parser.add_argument("-e", "--excel", action="store_true", help="use measures to prevent excel cell type conversion")
    parser.add_argument("-p", "--proper-name", action="store_true", help="use device model names instead of serial ID")
    parser.add_argument("-f", "--full-results", action="store_true", help="full results instead of just summaries")
    parser.add_argument("-t", "--table", action="store_true", help="structure results in summary tables")
    args = parser.parse_args()

    excel = args.excel
    proper_name = args.proper_name or args.table
    make_spreadsheet(args.dir, args.output, args.append, args.sort, args.full_results, args.table)
