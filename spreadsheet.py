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
    "highest_battery": "Highest-battery",
    "max_capacity": "Max-capacity"
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
re_early = re.compile(
    timestamp +
    r"W Important: Stopped processing early for (.*)\.mp4 at (\d+) frames, (\d+) remaining" +
    trailing_whitespace
)
re_pref = re.compile(timestamp + r"I Important: Preferences:" + trailing_whitespace)
re_total_power = re.compile(timestamp + r"D PowerMonitor:\s+Total: -?(\d+)nW" + trailing_whitespace)
re_average_power = re.compile(timestamp + r"D PowerMonitor:\s+Average: -?(\d+)\.(\d+)nW" + trailing_whitespace)
re_master = re.compile(timestamp + r"I Important:\s+Master: (\w+)" + trailing_whitespace)
re_network = re.compile(timestamp + r"I Important:\s+Wi-Fi: (\w+)" + trailing_whitespace)
re_early_divisor = re.compile(timestamp + r"I Important:\s+Early stop divisor: (\d+\.\d+)" + trailing_whitespace)
re_frames = re.compile(timestamp + r"D Important:\s+Starting analysis of (.*)\.mp4, (\d+) frames" + trailing_whitespace)
re_enqueue = re.compile(timestamp + r"D Important:\s+Enqueued (.*)\.mp4" + trailing_whitespace)
re_start = re.compile(timestamp + r"D Important:\s+Started download: (.*)\.mp4" + trailing_whitespace)

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
    "Proc power (mW)"
]
summary_header = [
    "Master",
    "Workers",
    "Algorithm",
    "Enqu time (s)",
    "Down time (s)",
    "Tran time (s)",
    "Ret time (s)",
    "Proc time (s)",
    "Wait time (s)",
    "Turn time (s)",
    "Net power (mW)",
    "Proc power (mW)",
    "Total power (mW)",
    "Skipped",
    "Skip rate",
    "Total time (s)",
    "Human time",
    "Network",
    "Directory"
]

excel = False
proper_name = False
short = False
max_row_size = 0
seg_sep = '!'


class Video:
    def __init__(self, name: str, down_time: float = 0, transfer_time: float = 0, analysis_time: float = 0,
                 wait_time: float = 0, return_time: float = 0, down_power: float = 0, transfer_power: float = 0,
                 analysis_power: float = 0, turnaround_time: float = 0, frames: int = 0, skipped_frames: int = 0):
        self.name = name
        self.enqueue_time = 0.0
        self.down_time = down_time
        self.transfer_time = transfer_time
        self.analysis_time = analysis_time
        self.return_time = return_time
        self.wait_time = wait_time
        self.turnaround_time = turnaround_time
        self.down_power = down_power
        self.transfer_power = transfer_power
        self.analysis_power = analysis_power
        self.frames = frames
        self.skipped_frames = skipped_frames
        self.skip_rate = 0.0

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

    def has_error(self) -> bool:
        return (
                self.down_time == 0
                or self.analysis_time == 0
                or (self.transfer_time > 0 and self.return_time == 0)
                or (self.transfer_time == 0 and self.return_time > 0)
        )

    def __str__(self) -> str:
        return self.name


class Device:
    def __init__(self, name: str):
        self.name = name
        self.videos = {}  # type: Dict[str, Video]
        self.total_power = 0.0
        self.average_power = 0.0
        self.network = ""
        self.early_divisor = 0.0

    def set_preferences(self, log_path: str):
        line_count = 0

        with open(log_path, 'r', encoding="utf-8") as log:
            for line in log:
                if line_count > 100:
                    return
                line_count += 1

                network_match = re_network.match(line)
                early_divisor_match = re_early_divisor.match(line)

                if network_match is not None:
                    network = network_match.group(2)

                    if network == "offline":
                        self.network = "Direct"
                    else:
                        self.network = "Dash 5GHz" if "5G" in network else "Dash 2.4GHz"

                if early_divisor_match is not None:
                    early_divisor = float(early_divisor_match.group(2))
                    self.early_divisor = early_divisor
                    return

    def get_totals(self) -> Dict[str, float]:
        return {
            "enqueue_time": sum(v.enqueue_time for v in self.videos.values()),
            "down_time": sum(v.down_time for v in self.videos.values()),
            "transfer_time": sum(v.transfer_time for v in self.videos.values()),
            "return_time": sum(v.return_time for v in self.videos.values()),
            "analysis_time": sum(v.analysis_time for v in self.videos.values()),
            "wait_time": sum(v.wait_time for v in self.videos.values()),
            "turnaround_time": sum(v.turnaround_time for v in self.videos.values()),
            "network_power": sum(v.down_power + v.transfer_power for v in self.videos.values()),
            "analysis_power": sum(v.analysis_power for v in self.videos.values()),
            "skipped_frames": sum(v.skipped_frames for v in self.videos.values())
        }

    def get_averages(self) -> Dict[str, float]:
        video_count = len(self.videos)

        if video_count == 0:
            return {
                "enqueue_time": 0,
                "down_time": 0,
                "transfer_time": 0,
                "return_time": 0,
                "analysis_time": 0,
                "wait_time": 0,
                "turnaround_time": 0,
                "network_power": 0,
                "analysis_power": 0,
                "skipped_frames": 0,
                "skip_rate": 0,
                "total_power": 0
            }

        totals = self.get_totals()
        return {
            "enqueue_time": totals["enqueue_time"] / video_count,
            "down_time": totals["down_time"] / video_count,
            "transfer_time": totals["transfer_time"] / video_count,
            "return_time": totals["return_time"] / video_count,
            "analysis_time": totals["analysis_time"] / video_count,
            "wait_time": totals["wait_time"] / video_count,
            "turnaround_time": totals["turnaround_time"] / video_count,
            "network_power": totals["network_power"] / video_count,
            "analysis_power": totals["analysis_power"] / video_count,
            "skipped_frames": totals["skipped_frames"] / video_count,
            "skip_rate": sum(v.skip_rate for v in self.videos.values()) / video_count,
            "total_power": self.total_power / video_count
        }

    def __str__(self) -> str:
        return self.name


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

        self.enqueue_time = -1.0
        self.down_time = -1.0
        self.transfer_time = -1.0
        self.return_time = -1.0
        self.analysis_time = -1.0
        self.wait_time = -1.0
        self.turnaround_time = -1.0
        self.network_power = -1.0
        self.analysis_power = -1.0
        self.skipped_frames = 0
        self.total_time = get_total_time(self.master_path)

        self.avg_enqueue_time = 0.0
        self.avg_down_time = 0.0
        self.avg_transfer_time = 0.0
        self.avg_return_time = 0.0
        self.avg_analysis_time = 0.0
        self.avg_wait_time = 0.0
        self.avg_turnaround_time = 0.0
        self.avg_network_power = 0.0
        self.avg_analysis_power = 0.0
        self.avg_total_power = 0.0
        self.avg_skipped_frames = 0.0
        self.avg_skip_rate = 0.0

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

        self.avg_enqueue_time = self.enqueue_time / video_count
        self.avg_down_time = self.down_time / video_count
        self.avg_transfer_time = self.transfer_time / video_count
        self.avg_return_time = self.return_time / video_count
        self.avg_analysis_time = self.analysis_time / video_count
        self.avg_wait_time = self.wait_time / video_count
        self.avg_turnaround_time = self.turnaround_time / video_count
        self.avg_network_power = self.network_power / video_count
        self.avg_analysis_power = self.analysis_power / video_count
        self.avg_total_power = self.get_total_power() / video_count
        self.avg_skipped_frames = self.skipped_frames / video_count
        self.avg_skip_rate = sum(d.get_averages()["skip_rate"] for d in self.devices.values()) / len(self.devices)

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
        master_network = self.devices[self.get_master_short_name()].network

        if master_network != "offline" and all(d.network == master_network for d in self.devices.values()):
            return master_network
        else:
            return "Direct"

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

        self.enqueue_time = sum(v.enqueue_time for v in self.videos.values())
        self.down_time = sum(v.down_time for v in self.videos.values())
        self.transfer_time = sum(v.transfer_time for v in self.videos.values())
        self.return_time = sum(v.return_time for v in self.videos.values())
        self.analysis_time = sum(v.analysis_time for v in self.videos.values())
        self.wait_time = sum(v.wait_time for v in self.videos.values())
        self.turnaround_time = sum(v.turnaround_time for v in self.videos.values())
        self.network_power = sum(v.down_power + v.transfer_power for v in self.videos.values())
        self.analysis_power = sum(v.analysis_power for v in self.videos.values())
        self.skipped_frames = sum(v.skipped_frames for v in self.videos.values())

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


def pad_row(row: List[str]) -> List[str]:
    pad_size = max_row_size - len(row)
    return row + [''] * pad_size


def write_row(writer, row: List[str]):
    writer.writerow(pad_row(row))


def get_device_name(short_serial: str) -> str:
    return device_names[short_serial] if proper_name and short_serial else short_serial


def check_video_count(videos: List[Video]) -> int:
    # There are a specific number of test videos, if the logs do not have all videos, then something went wrong
    expected_video_count = 800
    video_count = len(videos)

    return expected_video_count - video_count


def check_videos(videos: List[Video]):
    return [video.name for video in videos if video.has_error()]


def check_errors(runs: List[Analysis]):
    for run in runs:
        missed = check_video_count(list(run.videos.values()))
        if missed:
            print(f"Unexpected video count: {missed} missing in {os.path.basename(run.log_dir)}")

        videos_with_errors = check_videos(list(run.videos.values()))
        if videos_with_errors:
            print(f"Error found in {run.log_dir} with: {','.join(videos_with_errors)}")


def get_video_name(name: str) -> str:
    if seg_sep in name:
        return name.split(seg_sep)[0]
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


def get_average_row(averages_list: List[List[float]]) -> List[str]:
    return ["Average"] + [f"{a:.3f}" for a in [sum(col) / len(col) for col in zip(*averages_list)]]


def clean_videos(videos: Dict[str, Video]):
    to_delete = {get_video_name(video_name) for video_name in videos if seg_sep in video_name}
    for video_name in to_delete:
        del videos[video_name]


def parse_master_log(devices: Dict[str, Device], master_filename: str, log_dir: str) -> Dict[str, Video]:
    videos = {}  # type: Dict[str, Video]
    enqueue_times = {}  # type: Dict[str, datetime]
    log_path = os.path.join(log_dir, master_filename)

    with open(log_path, 'r', encoding="utf-8") as master_log:
        master_name = master_filename[-8:-4]
        master = devices[master_name]
        master.set_preferences(log_path)

        for line in master_log:
            enqueue = re_enqueue.match(line)
            start = re_start.match(line)
            down = re_down.match(line)
            transfer = re_transfer.match(line)
            comp = re_comp.match(line)
            wait = re_wait.match(line)
            turn = re_turnaround.match(line)
            frames = re_frames.match(line)
            early = re_early.match(line)
            total_power = re_total_power.match(line)
            average_power = re_average_power.match(line)

            if enqueue is not None:
                video_name = enqueue.group(2)

                videos[video_name] = Video(video_name)
                enqueue_times[video_name] = timestamp_to_datetime(line)
            elif start is not None:
                video_name = start.group(2)
                start = enqueue_times[video_name]
                end = timestamp_to_datetime(line)

                videos[video_name].enqueue_time = (end - start).total_seconds()
            elif down is not None:
                video_name = down.group(2)
                down_time = float(down.group(3))
                down_power = parse_power(down.group(4), master_name)

                if video_name in videos:
                    videos[video_name].down_time = down_time
                    videos[video_name].down_power = down_power
                else:
                    videos[video_name] = Video(name=video_name, down_time=down_time, down_power=down_power)
            elif transfer is not None:
                video_name = transfer.group(2)
                base_name = get_video_name(video_name)
                return_time = float(transfer.group(5))
                return_power = parse_power(transfer.group(6), master_name)

                if seg_sep in video_name and base_name in videos:
                    base_video = videos[base_name]
                    videos[video_name] = Video(
                        name=video_name,
                        down_time=base_video.down_time,
                        down_power=base_video.down_power,
                        return_time=return_time,
                        transfer_power=return_power
                    )
                else:
                    videos[video_name].return_time += return_time
                    videos[video_name].transfer_power += return_power
            elif comp is not None:
                video_name = comp.group(2)
                analysis_time = float(comp.group(3))
                analysis_power = parse_power(comp.group(4), master_name)

                videos[video_name].analysis_time += analysis_time
                videos[video_name].analysis_power += analysis_power
                master.videos[video_name] = videos[video_name]
            elif wait is not None:
                video_name = wait.group(2)
                base_name = get_video_name(video_name)
                wait_time = float(wait.group(3))

                if seg_sep in video_name and base_name in videos:
                    base_video = videos[base_name]
                    videos[video_name] = Video(
                        name=video_name,
                        down_time=base_video.down_time,
                        down_power=base_video.down_power,
                        wait_time=wait_time
                    )
                else:
                    videos[video_name].wait_time += wait_time
            elif turn is not None:
                video_name = turn.group(2)
                turn_time = float(turn.group(3))

                videos[video_name].turnaround_time += turn_time
            elif frames is not None:
                video_name = frames.group(2)
                frame_count = int(frames.group(3))

                videos[video_name].frames = frame_count
            elif early is not None:
                video_name = early.group(2)
                skipped = int(early.group(4))

                videos[video_name].skipped_frames += skipped
                videos[video_name].skip_rate = skipped / frame_count
            elif total_power is not None:
                master.total_power = parse_power(total_power.group(2), master_name)
            elif average_power is not None:
                master.average_power = parse_power(average_power.group(2), master_name)
    clean_videos(videos)
    return videos


def parse_worker_logs(devices: Dict[str, Device], videos: Dict[str, Video], log_dir: str, master_sn: str):
    worker_logs = [log for log in os.listdir(log_dir) if is_log(log) and master_sn not in log]

    for log in worker_logs:
        log_path = os.path.join(log_dir, log)

        with open(log_path, 'r', encoding="utf-8") as work_log:
            device_name = log[-8:-4]
            worker = devices[device_name]
            worker.set_preferences(log_path)

            for line in work_log:
                transfer = re_transfer.match(line)
                comp = re_comp.match(line)
                wait = re_wait.match(line)
                turn = re_turnaround.match(line)
                frames = re_frames.match(line)
                early = re_early.match(line)
                total_power = re_total_power.match(line)
                average_power = re_average_power.match(line)

                if transfer is not None:
                    video_name = transfer.group(2)
                    transfer_time = float(transfer.group(5))
                    transfer_power = parse_power(transfer.group(6), device_name)

                    video = videos[video_name]
                    video.transfer_time += transfer_time
                    video.transfer_power += transfer_power

                    worker.videos[video_name] = video
                elif comp is not None:
                    video_name = comp.group(2)
                    analysis_time = float(comp.group(3))
                    analysis_power = parse_power(comp.group(4), device_name)

                    videos[video_name].analysis_time += analysis_time
                    videos[video_name].analysis_power += analysis_power
                elif wait is not None:
                    video_name = wait.group(2)
                    wait_time = float(wait.group(3))

                    videos[video_name].wait_time += wait_time
                elif turn is not None:
                    video_name = turn.group(2)
                    turn_time = float(turn.group(3))

                    videos[video_name].turnaround_time += turn_time
                elif frames is not None:
                    video_name = frames.group(2)
                    frame_count = int(frames.group(3))

                    videos[video_name].frames = frame_count
                elif early is not None:
                    video_name = early.group(2)
                    skipped = int(early.group(4))

                    videos[video_name].skipped_frames += skipped
                    videos[video_name].skip_rate = skipped / frame_count
                elif total_power is not None:
                    worker.total_power = parse_power(total_power.group(2), device_name)
                elif average_power is not None:
                    worker.average_power = parse_power(average_power.group(2), device_name)


def parse_offline_log(log_path: str) -> Device:
    videos = {}  # type: Dict[str, Video]
    enqueue_times = {}  # type: Dict[str, datetime]

    with open(log_path, 'r', encoding="utf-8") as offline_log:
        device_sn = get_basename_sans_ext(log_path)
        device_name = device_sn[-4:]
        device = Device(device_name)
        device.set_preferences(log_path)
        device.videos = videos

        for line in offline_log:
            enqueue = re_enqueue.match(line)
            start = re_start.match(line)
            down = re_down.match(line)
            comp = re_comp.match(line)
            wait = re_wait.match(line)
            turn = re_turnaround.match(line)
            frames = re_frames.match(line)
            early = re_early.match(line)
            total_power = re_total_power.match(line)
            average_power = re_average_power.match(line)

            if enqueue is not None:
                video_name = enqueue.group(2)

                videos[video_name] = Video(video_name)
                enqueue_times[video_name] = timestamp_to_datetime(line)
            elif start is not None:
                video_name = start.group(2)
                start = enqueue_times[video_name]
                end = timestamp_to_datetime(line)

                videos[video_name].enqueue_time = (end - start).total_seconds()
            elif down is not None:
                video_name = down.group(2)
                down_time = float(down.group(3))
                down_power = parse_power(down.group(4), device_name)

                if video_name in videos:
                    videos[video_name].down_time = down_time
                    videos[video_name].down_power = down_power
                else:
                    videos[video_name] = Video(name=video_name, down_time=down_time, down_power=down_power)
            elif comp is not None:
                video_name = comp.group(2)
                analysis_time = float(comp.group(3))
                analysis_power = parse_power(comp.group(4), device_name)

                videos[video_name].analysis_time = analysis_time
                videos[video_name].analysis_power = analysis_power
            elif wait is not None:
                video_name = wait.group(2)
                wait_time = float(wait.group(3))

                videos[video_name].wait_time = wait_time
            elif turn is not None:
                video_name = turn.group(2)
                turn_time = float(turn.group(3))

                videos[video_name].turnaround_time = turn_time
            elif frames is not None:
                video_name = frames.group(2)
                frame_count = int(frames.group(3))

                videos[video_name].frames = frame_count
            elif early is not None:
                video_name = early.group(2)
                skipped = int(early.group(4))

                videos[video_name].skipped_frames = skipped
                videos[video_name].skip_rate = skipped / frame_count
            elif total_power is not None:
                device.total_power = parse_power(total_power.group(2), device_name)
            elif average_power is not None:
                device.average_power = parse_power(average_power.group(2), device_name)
    return device


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
            device = parse_offline_log(log_path)

            parent_path = os.path.dirname(log_path)
            device_sn = get_basename_sans_ext(log_path)
            device_name = device_sn[-4:]

            run = Analysis(parent_path, device_sn, {device_name: device}, device.videos)
            run.local = False
            run.algorithm = "offline"

            run.set_average_stats()
            runs.append(run)


def write_offline_runs(runs: List[Analysis], writer):
    write_row(writer, ["Offline"])

    for run in [r for r in runs if r.algorithm == "offline"]:
        videos = run.videos

        missed = check_video_count(list(videos.values()))
        write_row(writer, [f"Device: {run.get_master_full_name()}"])
        write_row(writer, [
            f"Download Delay: {run.delay}",
            f"Object Model: {run.object_model}",
            f"Pose Model: {run.pose_model}",
            f"Dual: {run.dual_download}",
            f"Skipped: {run.skipped_frames}",
            f"Videos: {len(videos)}",
            f"ESD: {run.devices[run.get_master_short_name()].early_divisor:.2f}",
            f"MISSING {missed}" if missed else "",
            f"Dir: {run.get_sub_log_dir()}"
        ])
        write_row(writer, offline_header)

        for video in videos.values():
            write_row(writer, video.get_offline_stats())

        write_row(writer, [
            "Total",
            f"{run.down_time:.3f}",
            f"{run.analysis_time:.3f}",
            f"{run.wait_time:.3f}",
            f"{run.turnaround_time:.3f}",
            f"{run.network_power:.3f}",
            f"{run.analysis_power:.3f}"
        ])
        write_row(writer, [
            "Average",
            f"{run.avg_down_time:.3f}",
            f"{run.avg_analysis_time:.3f}",
            f"{run.avg_wait_time:.3f}",
            f"{run.avg_turnaround_time:.3f}",
            f"{run.avg_network_power:.3f}",
            f"{run.avg_analysis_power:.3f}"
        ])
        write_row(writer, [
            "Total total time", run.get_time_string(),
            "Total total power", f"{run.get_total_power():.3f}"
        ])
    write_row(writer, [])


def write_online_run(run: Analysis, writer):
    missed = check_video_count(list(run.videos.values()))

    write_row(writer, [
        f"Master: {run.get_master_full_name()}",
        f"Segments: {run.seg_num}",
        f"Nodes: {run.nodes}",
        f"Algorithm: {run.get_algorithm_name()}"
    ])
    write_row(writer, [
        f"Local Processing: {run.local}",
        f"Download Delay: {run.delay}",
        f"Object Model: {run.object_model}",
        f"Pose Model: {run.pose_model}",
        f"Dual: {run.dual_download}",
        f"Videos: {len(run.videos)}",
        f"MISSING {missed}" if missed else "",
        f"Dir: {run.get_sub_log_dir()}"
    ])

    for device_name, device in run.devices.items():
        totals = device.get_totals()

        write_row(writer, [
            f"Device: {get_device_name(device_name)}",
            f"Network: {device.network}",
            f"Processed: {len(device.videos)}",
            f"ESD: {device.early_divisor:.2f}",
            f"Skipped: {totals['skipped_frames']}"
        ])

        videos = list(device.videos.values())

        if not videos:
            write_row(writer, ["Did not analyse any videos"])
            continue

        write_row(writer, online_header + ["Total total power", f"{device.total_power:.3f}"])

        for video in videos:
            write_row(writer, video.get_stats())

        video_count = len(videos)
        if video_count > 1:
            write_row(writer, [
                "Total",
                f"{totals['down_time']:.3f}",
                f"{totals['transfer_time']:.3f}" if totals["transfer_time"] != 0 else "n/a",
                f"{totals['return_time']:.3f}" if totals["return_time"] != 0 else "n/a",
                f"{totals['analysis_time']:.3f}",
                f"{totals['wait_time']:.3f}",
                f"{totals['turnaround_time']:.3f}",
                f"{totals['network_power']:.3f}",
                f"{totals['analysis_power']:.3f}"
            ])

            averages = device.get_averages()
            write_row(writer, [
                "Average",
                f"{averages['down_time']:.3f}",
                f"{averages['transfer_time']:.3f}" if totals["transfer_time"] != 0 else "n/a",
                f"{averages['return_time']:.3f}" if totals["return_time"] != 0 else "n/a",
                f"{averages['analysis_time']:.3f}",
                f"{averages['wait_time']:.3f}",
                f"{averages['turnaround_time']:.3f}",
                f"{averages['network_power']:.3f}",
                f"{averages['analysis_power']:.3f}"
            ])

    if sum(1 for device in run.devices.values() if device) > 1:
        write_row(writer, ["Combined total"] + run.get_total_stats())
        write_row(writer, ["Combined average"] + run.get_average_stats())

    write_row(writer, [
        "Total total time", run.get_time_string(),
        "Total total power", f"{run.get_total_power():.3f}",
        "Total skipped", str(run.skipped_frames),
        "Avg skipped", f"{run.avg_skipped_frames:.3f}"
    ])
    write_row(writer, [])


def write_spread_totals(runs: List[Analysis], writer):
    write_row(writer, summary_header + ["Totals"])

    for run in runs:
        write_row(writer, [
            run.get_master_full_name(),
            excel_format(run.get_worker_string()),
            "Duo" if len(run.devices) == 2 else run.get_algorithm_name(),
            f"{run.enqueue_time:.3f}",
            f"{run.down_time:.3f}",
            f"{run.transfer_time:.3f}" if run.transfer_time > 0 else "n/a",
            f"{run.return_time:.3f}" if run.transfer_time > 0 else "n/a",
            f"{run.analysis_time:.3f}",
            f"{run.wait_time:.3f}",
            f"{run.turnaround_time:.3f}",
            f"{run.network_power:.3f}",
            f"{run.analysis_power:.3f}",
            f"{run.get_total_power():.3f}",
            str(run.skipped_frames),
            f"{run.avg_skip_rate:.3f}",  # Using average as it doesn't make sense to have a "total" skip rate
            run.get_time_seconds_string(),
            excel_format(run.get_time_human_string()),
            run.get_network(),
            run.log_dir
        ])

    time = timedelta(seconds=sum(run.total_time.total_seconds() for run in runs))
    write_row(writer, ["Total"] + ['', ''] + [
        f"{sum(run.enqueue_time for run in runs):.3f}",
        f"{sum(run.down_time for run in runs):.3f}",
        f"{sum(run.transfer_time for run in runs):.3f}",
        f"{sum(run.return_time for run in runs):.3f}",
        f"{sum(run.analysis_time for run in runs):.3f}",
        f"{sum(run.wait_time for run in runs):.3f}",
        f"{sum(run.turnaround_time for run in runs):.3f}",
        f"{sum(run.network_power for run in runs):.3f}",
        f"{sum(run.analysis_power for run in runs):.3f}",
        f"{sum(run.get_total_power() for run in runs):.3f}",
        f"{sum(run.avg_skip_rate for run in runs) / len(runs):.3f}",
        f"{sum(run.skipped_frames for run in runs):.3f}",
        f"{sum(run.total_time.total_seconds() for run in runs):.3f}",
        excel_format(format_timedelta(time))
    ])
    write_row(writer, [])


def write_spread_averages(runs: List[Analysis], writer):
    write_row(writer, summary_header + ["Averages"])

    for run in runs:
        write_row(writer, [
            run.get_master_full_name(),
            excel_format(run.get_worker_string()),
            "Duo" if len(run.devices) == 2 else run.get_algorithm_name(),
            f"{run.avg_enqueue_time:.3f}",
            f"{run.avg_down_time:.3f}",
            f"{run.avg_transfer_time:.3f}" if run.avg_transfer_time > 0 else "n/a",
            f"{run.avg_return_time:.3f}" if run.avg_transfer_time > 0 else "n/a",
            f"{run.avg_analysis_time:.3f}",
            f"{run.avg_wait_time:.3f}",
            f"{run.avg_turnaround_time:.3f}",
            f"{run.avg_network_power:.3f}",
            f"{run.avg_analysis_power:.3f}",
            f"{run.avg_total_power:.3f}",
            f"{run.avg_skipped_frames:.3f}",
            f"{run.avg_skip_rate:.3f}",
            run.get_time_seconds_string(),
            excel_format(run.get_time_human_string()),
            run.get_network(),
            run.log_dir
        ])

    time = timedelta(seconds=sum(run.total_time.total_seconds() for run in runs) / len(runs))
    write_row(writer, ["Average"] + ['', ''] + [
        f"{sum(run.avg_enqueue_time for run in runs) / len(runs):.3f}",
        f"{sum(run.avg_down_time for run in runs) / len(runs):.3f}",
        f"{sum(run.avg_transfer_time for run in runs) / len(runs):.3f}",
        f"{sum(run.avg_return_time for run in runs) / len(runs):.3f}",
        f"{sum(run.avg_analysis_time for run in runs) / len(runs):.3f}",
        f"{sum(run.avg_wait_time for run in runs) / len(runs):.3f}",
        f"{sum(run.avg_turnaround_time for run in runs) / len(runs):.3f}",
        f"{sum(run.avg_network_power for run in runs) / len(runs):.3f}",
        f"{sum(run.avg_analysis_power for run in runs) / len(runs):.3f}",
        f"{sum(run.get_total_power() for run in runs) / len(runs):.3f}",
        f"{sum(run.avg_skipped_frames for run in runs) / len(runs):.3f}",
        f"{sum(run.avg_skip_rate for run in runs) / len(runs):.3f}",
        f"{sum(run.total_time.total_seconds() for run in runs) / len(runs):.3f}",
        excel_format(format_timedelta(time))
    ])
    write_row(writer, [])


def write_device_averages(device: Device, writer):
    if len(device.videos) > 0:
        sub_averages = device.get_averages()
        write_row(writer, [
            get_device_name(device.name),
            f"{sub_averages['transfer_time']:.3f}" if sub_averages["transfer_time"] != 0 else "n/a",
            f"{sub_averages['return_time']:.3f}" if sub_averages["return_time"] != 0 else "n/a",
            f"{sub_averages['analysis_time']:.3f}",
            f"{sub_averages['wait_time']:.3f}",
            f"{sub_averages['turnaround_time']:.3f}",
            f"{device.total_power:.3f}",
            f"{sub_averages['total_power']:.3f}",
            f"{device.early_divisor:.3f}",
            f"{sub_averages['skipped_frames']:.3f}",
            f"{sub_averages['skip_rate']:.3f}",
            len(device.videos)
        ])
    else:
        write_row(writer, [get_device_name(device.name)] + ["0"] * 7 + [f"{device.early_divisor:.3f}"] + ["0"] * 4)


def write_tables(runs: List[Analysis], writer):
    if short:
        offline_table_header = [
            "Device",
            "Enqueue",
            "Download",
            "Processing",
            "Wait",
            "Turnaround",
            "Total power",
            "Average power",
            "ESD",
            "Skipped",
            "Skip rate",
            "Total time"
        ]
        online_table_header = [
            "Device",
            "Transfer",
            "Return",
            "Processing",
            "Wait",
            "Turnaround",
            "Total power",
            "Average power",
            "ESD",
            "Skipped",
            "Skip rate",
            "Videos"
        ]
        download_time_label = "Download:"
        total_time_label = "Total time:"
        enqueue_time_label = "Enqueue:"
    else:
        offline_table_header = [
            "Device",
            "Enqueue time (s)",
            "Download time (s)",
            "Processing time (s)",
            "Wait time (s)",
            "Turnaround time (s)",
            "Total power (mW)",
            "Average power (mW)",
            "ESD",
            "Skipped",
            "Skip rate",
            "Total time (s)"
        ]
        online_table_header = [
            "Device",
            "Transfer time (s)",
            "Return time (s)",
            "Processing time (s)",
            "Wait time (s)",
            "Turnaround time (s)",
            "Total power (mW)",
            "Average power (mW)",
            "ESD",
            "Skipped",
            "Skip rate",
            "Videos"
        ]
        download_time_label = "Download time (s):"
        total_time_label = "Total time (s):"
        enqueue_time_label = "Enqueue time (s):"

    write_row(writer, ["Offline tests"])
    write_row(writer, offline_table_header)
    averages_list = []  # type: List[List[float]]

    for run in [r for r in runs if r.algorithm == "offline"]:
        device = run.devices[run.get_master_short_name()]
        average_dict = device.get_averages()

        averages = [
            average_dict["enqueue_time"],
            average_dict["down_time"],
            average_dict["analysis_time"],
            average_dict["wait_time"],
            average_dict["turnaround_time"],
            device.total_power,
            average_dict["total_power"],
            device.early_divisor,
            average_dict["skipped_frames"],
            average_dict["skip_rate"],
            run.total_time.total_seconds()
        ]
        averages_list.append(averages)

        write_row(
            writer,
            [run.get_master_full_name()] +
            [f"{a:.3f}" for a in averages]
        )
    write_row(writer, get_average_row(averages_list))
    write_row(writer, [])

    write_row(writer, ["Two-node tests"])
    write_row(writer, online_table_header)
    cur_master = ""
    averages_list = []

    for run in [r for r in runs if len(r.devices) == 2]:
        if run.get_master_full_name() != cur_master:
            if averages_list:
                write_row(writer, get_average_row(averages_list))
                averages_list = []
            cur_master = run.get_master_full_name()

            down_times = [
                r.avg_down_time for r in runs if r.get_master_full_name() == cur_master and len(r.devices) == 2]
            avg_down_time = sum(t for t in down_times) / len(down_times)
            write_row(writer, [
                "Master:", cur_master,
                download_time_label, f"{avg_down_time:.3f}"
            ])

        write_row(writer, [
            total_time_label, run.get_time_seconds_string(),
            enqueue_time_label, f"{run.avg_enqueue_time:.3f}"
        ])
        for device in run.devices.values():
            write_device_averages(device, writer)

        averages = [
            run.avg_transfer_time,
            run.avg_return_time,
            run.avg_analysis_time,
            run.avg_wait_time,
            run.avg_turnaround_time,
            run.get_total_power(),
            run.avg_total_power,
            sum(d.early_divisor for d in run.devices.values()) / len(run.devices),
            run.avg_skipped_frames,
            run.avg_skip_rate,
            sum(len(d.videos) for d in run.devices.values()) / len(run.devices)
        ]
        averages_list.append(averages)

        write_row(writer, ["Average"] + [f"{a:.3f}" for a in averages])
    if len(averages_list) > 1:
        write_row(writer, get_average_row(averages_list))
    write_row(writer, [])

    write_row(writer, ["Three-node tests"])
    write_row(writer, online_table_header)
    cur_master = ""
    cur_workers = ""
    averages_list = []

    for run in [r for r in runs if len(r.devices) == 3]:
        if run.get_master_full_name() != cur_master or run.get_worker_string() != cur_workers:
            if len(averages_list) > 1:
                write_row(writer, get_average_row(averages_list))
                averages_list = []
            cur_master = run.get_master_full_name()
            cur_workers = run.get_worker_string()

            down_times = [
                r.avg_down_time for r in runs if
                r.get_master_full_name() == cur_master and r.get_worker_string() == cur_workers]
            avg_down_time = sum(t for t in down_times) / len(down_times)
            write_row(writer, [
                "Master:", cur_master,
                download_time_label, f"{avg_down_time:.3f}"
            ])

        write_row(writer, [
            run.get_algorithm_name(),
            total_time_label, run.get_time_seconds_string(),
            enqueue_time_label, f"{run.avg_enqueue_time:.3f}"
        ])
        for device in run.devices.values():
            write_device_averages(device, writer)

        averages = [
            run.avg_transfer_time,
            run.avg_return_time,
            run.avg_analysis_time,
            run.avg_wait_time,
            run.avg_turnaround_time,
            run.get_total_power(),
            run.avg_total_power,
            sum(d.early_divisor for d in run.devices.values()) / len(run.devices),
            run.avg_skipped_frames,
            run.avg_skip_rate,
            sum(len(d.videos) for d in run.devices.values()) / len(run.devices)
        ]
        averages_list.append(averages)

        write_row(writer, ["Average"] + [f"{a:.3f}" for a in averages])
    if len(averages_list) > 1:
        write_row(writer, get_average_row(averages_list))
    write_row(writer, [])


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

        check_errors(runs)

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
    parser.add_argument("-n", "--names", action="store_true", help="use device model names instead of serial ID")
    parser.add_argument("-f", "--full-results", action="store_true", help="full results instead of just summaries")
    parser.add_argument("-t", "--table", action="store_true", help="structure results in summary tables")
    parser.add_argument("-p", "--pad", action="store_true", help="ensure all rows have the same number of commas")
    parser.add_argument("--short", action="store_true", help="use short headers for tables")
    args = parser.parse_args()

    excel = args.excel
    proper_name = args.names or args.table
    short = args.short

    if args.pad:
        max_row_size = 13 if args.table else 20

    make_spreadsheet(args.dir, args.output, args.append, args.sort, args.full_results, args.table)
