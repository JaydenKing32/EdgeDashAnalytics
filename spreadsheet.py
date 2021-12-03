#!/usr/bin/env python3
import csv
import os
import re
from argparse import ArgumentParser
from datetime import datetime, timedelta
from typing import Dict, List

algorithms = [
    "offline",
    "round_robin",
    "fastest",
    "least_busy",
    "fastest_cpu",
    "most_cpu_cores",
    "most_ram",
    "most_storage",
    "highest_battery"
]


class Video:
    def __init__(self, name: str, dash_down_time: float = 0, down_time: float = 0,
                 analysis_time: float = 0, return_time: float = 0):
        self.name = name
        self.dash_down_time = dash_down_time
        self.down_time = down_time
        self.analysis_time = analysis_time
        self.return_time = return_time

    def get_stats(self) -> List[str]:
        return [
            self.name,
            "{:.3f}".format(self.dash_down_time),
            "{:.3f}".format(self.down_time) if self.down_time != 0 else "n/a",
            "{:.3f}".format(self.return_time) if self.return_time != 0 else "n/a",
            "{:.3f}".format(self.analysis_time)
        ]


class Device:
    def __init__(self, name: str):
        self.name = name
        self.videos = {}  # type: Dict[str, Video]
        self.total_power = 0
        self.average_power = 0  # type: float


class Analysis:
    def __init__(self, log_dir: str, master: str, devices: Dict[str, Device], videos: Dict[str, Video]):
        self.log_dir = log_dir
        self.master = master
        self.master_path = "{}.log".format(os.path.join(self.log_dir, self.master))
        self.devices = devices
        self.videos = videos
        self.seg_num = -1
        self.nodes = len([log for log in os.listdir(self.log_dir) if is_log(log)])
        self.algorithm = "offline"
        self.model = ""
        self.local = False
        self.dash_down_time = -1.0
        self.down_time = -1.0
        self.analysis_time = -1.0
        self.return_time = -1.0
        self.delay = -1
        self.total_time = get_total_time(self.master_path)
        self.avg_dash_down_time = 0
        self.avg_down_time = 0
        self.avg_return_time = 0
        self.avg_analysis_time = 0
        self.parse_preferences()

    def get_master_short_name(self) -> str:
        return self.master[-4:]

    def get_time_string(self) -> str:
        return "{} ({:.11})".format(
            self.total_time.total_seconds(), str(self.total_time))

    def get_sub_log_dir(self) -> str:
        i = self.log_dir.index(os.path.sep) + 1
        return self.log_dir[i:]

    def get_total_stats(self) -> List[str]:
        return [
            "{:.3f}".format(self.dash_down_time),
            "{:.3f}".format(self.down_time) if self.down_time != 0 else "n/a",
            "{:.3f}".format(self.return_time) if self.down_time != 0 else "n/a",
            "{:.3f}".format(self.analysis_time)
        ]

    def set_average_stats(self):
        video_count = len(self.videos)

        self.avg_dash_down_time = self.dash_down_time / video_count
        self.avg_down_time = self.down_time / video_count
        self.avg_return_time = self.return_time / video_count
        self.avg_analysis_time = self.analysis_time / video_count

    def get_average_stats(self) -> List[str]:
        return [
            "{:.3f}".format(self.avg_dash_down_time),
            "{:.3f}".format(self.avg_down_time) if self.avg_down_time != 0 else "n/a",
            "{:.3f}".format(self.avg_return_time) if self.avg_return_time != 0 else "n/a",
            "{:.3f}".format(self.avg_analysis_time)
        ]

    def get_total_power(self) -> int:
        return sum(d.total_power for d in self.devices.values())

    def get_total_average_power(self) -> float:
        return sum(d.average_power for d in self.devices.values())

    def parse_preferences(self):
        with open(self.master_path, 'r') as master_log:
            for line in master_log:
                pref = re_pref.match(line)
                delay = re_down_delay.match(line)

                if pref is not None:
                    model = master_log.readline().split()[-1]
                    algo = master_log.readline().split()[-1]
                    local = master_log.readline().split()[-1] == "true"
                    master_log.readline()  # skip auto-download line
                    seg = master_log.readline().split()[-1] == "true"
                    seg_num = int(master_log.readline().split()[-1]) if seg else 1

                    self.algorithm = algo
                    self.seg_num = seg_num
                    self.model = model
                    self.local = local

                if delay is not None:
                    self.delay = int(delay.group(2))
                    break

        self.dash_down_time = sum(v.dash_down_time for v in self.videos.values())
        self.down_time = sum(v.down_time for v in self.videos.values())
        self.return_time = sum(v.return_time for v in self.videos.values())
        self.analysis_time = sum(v.analysis_time for v in self.videos.values())

    def __str__(self) -> str:
        # master-local-seg_num-delay-node_num-algo
        return "{}-{}-{}-{}-{}-{}".format(
            self.get_master_short_name(),
            self.local,
            abs(self.seg_num),
            abs(self.delay),
            self.nodes,
            self.algorithm
        )


parser = ArgumentParser(description="Generates spreadsheets from logs")
parser.add_argument("-d", "--dir", default="out", help="directory of logs")
parser.add_argument("-o", "--output", default="results.csv", help="name of output file")
args = parser.parse_args()

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

timestamp = r"^(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\s+\d+\s+\d+ "
re_timestamp = re.compile(r"^(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})\.(\d{3})(?=\s+\d+\s+\d+).*(?:\s+)?$")
re_dash_down = re.compile(
    timestamp +
    r"I Important: Successfully downloaded "
    r"(.*)\.\w+ in (\d*\.?\d*)s(?:\s+)?$")
re_down = re.compile(
    timestamp +
    r"I Important: Completed downloading "
    r"(.*)\.\w+ from Endpoint{id=\S{4}, name=(.*) \[(\w+)]} in (\d*\.?\d*)s(?:\s+)?$")
re_comp = re.compile(timestamp + r"D Important: Completed analysis of (.*)\.mp4 in (\d*\.?\d*)s(?:.*)?$")
re_pref = re.compile(timestamp + r"I Important: Preferences:(?:\s+)?$")
re_down_delay = re.compile(timestamp + r"I Important: Download delay: (\d*\.?\d*)s(?:\s+)?$")
re_total_power = re.compile(timestamp + r"D PowerMonitor:\s+Total: -?(\d+)nW(?:\s+)?$")
re_average_power = re.compile(timestamp + r"D PowerMonitor:\s+Average: -?(\d+)\.(\d+)nW(?:\s+)?$")


def is_master(log_path: str) -> bool:
    with open(log_path, 'r') as log:
        # Check first 10 lines for preferences message
        for i in range(10):
            pref = re_pref.match(log.readline())

            if pref is not None:
                return True
    return False


def is_log(filename: str) -> bool:
    # Exclude verbose logs
    return filename.endswith(".log") and "verbose" not in filename


def get_master(log_dir: str) -> str:
    for file in [os.path.join(log_dir, filename) for filename in os.listdir(log_dir)]:
        if os.path.isfile(file) and is_master(file):
            return file


def get_video_name(name: str) -> str:
    sep = '!'
    if sep in name:
        return name.split(sep)[0]
    else:
        return name


def timestamp_to_datetime(line: str) -> datetime:
    match = re_timestamp.match(line)

    year = 2020
    month = int(match.group(1))
    day = int(match.group(2))
    hour = int(match.group(3))
    minute = int(match.group(4))
    second = int(match.group(5))
    microsecond = int(int(match.group(6)) * 1000)

    return datetime(year, month, day, hour, minute, second, microsecond)


def get_total_time(master_log_file: str) -> timedelta:
    start = None
    end = None

    with open(master_log_file, 'r') as master_log:
        for line in master_log:
            if start is None:
                pref = re_pref.match(line)

                if pref is not None:
                    start = line
            down_match = re_down.match(line)
            comp_match = re_comp.match(line)

            if down_match is not None:
                end = line
            elif comp_match is not None:
                end = line
    return timestamp_to_datetime(end) - timestamp_to_datetime(start)


def parse_master_log(devices: Dict[str, Device], master_filename: str, log_dir: str) -> Dict[str, Video]:
    videos = {}  # type: Dict[str, Video]

    with open(os.path.join(log_dir, master_filename), 'r') as master_log:
        master_name = master_filename[-8:-4]

        for line in master_log:
            dash_down = re_dash_down.match(line)
            down = re_down.match(line)
            comp = re_comp.match(line)
            total_power = re_total_power.match(line)
            average_power = re_average_power.match(line)

            if dash_down is not None:
                video_name = get_video_name(dash_down.group(2))
                dash_down_time = float(dash_down.group(3))

                video = Video(name=video_name, dash_down_time=dash_down_time)
                videos[video_name] = video
                devices[master_name].videos[video_name] = video
            elif down is not None:
                video_name = get_video_name(down.group(2))
                return_time = float(down.group(5))

                videos[video_name].return_time += return_time
            elif comp is not None:
                video_name = get_video_name(comp.group(2))
                analysis_time = float(comp.group(3))

                videos[video_name].analysis_time += analysis_time
            elif total_power is not None:
                devices[master_name].total_power = int(total_power.group(2))
            elif average_power is not None:
                devices[master_name].average_power = int(average_power.group(2))
    return videos


def parse_worker_logs(devices: Dict[str, Device], videos: Dict[str, Video], log_dir: str, master_sn: str):
    worker_logs = [log for log in os.listdir(log_dir) if is_log(log) and master_sn not in log]

    for log in worker_logs:
        with open(os.path.join(log_dir, log), 'r') as work_log:
            device_name = log[-8:-4]

            for line in work_log:
                down = re_down.match(line)
                comp = re_comp.match(line)
                total_power = re_total_power.match(line)
                average_power = re_average_power.match(line)

                if down is not None:
                    video_name = get_video_name(down.group(2))
                    down_time = float(down.group(5))

                    video = videos[video_name]
                    video.down_time += down_time
                    devices[device_name].videos[video_name] = video
                elif comp is not None:
                    video_name = get_video_name(comp.group(2))
                    analysis_time = float(comp.group(3))

                    videos[video_name].analysis_time += analysis_time
                elif total_power is not None:
                    devices[device_name].total_power = int(total_power.group(2))
                elif average_power is not None:
                    devices[device_name].average_power = int(average_power.group(2))


def make_offline_spreadsheet(log_dir: str, runs: List[Analysis], out_name: str):
    with open(out_name, 'a', newline='') as csv_f:
        writer = csv.writer(csv_f)
        writer.writerow(["Offline"])

        # Offline directories should only contain two files, normal log and verbose log
        for (path, dirs, files) in sorted(
                [(path, dirs, files) for (path, dirs, files) in os.walk(log_dir) if len(files) == 2]):
            for log in files:
                # Skip verbose logs
                if "verbose" in log:
                    continue

                log_path = os.path.join(path, log)

                with open(log_path, 'r') as offline_log:
                    device_sn = log[:-4]
                    videos = {}  # type: Dict[str, Video]
                    device = Device(device_sn[-4:])

                    run = Analysis(path, device_sn, {device_sn[-4:]: device}, videos)
                    run.local = False
                    run.algorithm = "offline"
                    runs.append(run)

                    for line in offline_log:
                        dash_down = re_dash_down.match(line)
                        comp = re_comp.match(line)
                        total_power = re_total_power.match(line)
                        average_power = re_average_power.match(line)

                        if dash_down is not None:
                            video_name = get_video_name(dash_down.group(2))
                            dash_down_time = float(dash_down.group(3))

                            videos[video_name] = Video(name=video_name, dash_down_time=dash_down_time)
                        elif comp is not None:
                            video_name = get_video_name(comp.group(2))
                            analysis_time = float(comp.group(3))

                            videos[video_name].analysis_time = analysis_time
                        elif total_power is not None:
                            device.total_power = int(total_power.group(2))
                        elif average_power is not None:
                            device.average_power = int(average_power.group(2))

                writer.writerow(["Device: {}".format(run.get_master_short_name())])
                writer.writerow([
                    "Download Delay: {}".format(run.delay),
                    "Model: {}".format(run.model),
                    "Total Power: {}".format(device.total_power),
                    "Average Power: {}".format(device.average_power)
                ])
                writer.writerow(["Filename", "Download time (s)", "Analysis time (s)"])

                for video in videos.values():
                    writer.writerow([video.name, video.dash_down_time, video.analysis_time])

                total_dash_down_time = sum(v.dash_down_time for v in videos.values())
                total_analysis_time = sum(v.analysis_time for v in videos.values())

                run.dash_down_time = total_dash_down_time
                run.analysis_time = total_analysis_time
                run.set_average_stats()

                writer.writerow([
                    "Total",
                    "{:.3f}".format(run.dash_down_time),
                    "{:.3f}".format(run.analysis_time)
                ])
                writer.writerow(["Actual total time", run.get_time_string()])

        writer.writerow('')


def make_spreadsheet(run: Analysis, out: str):
    with open(out, 'a', newline='') as csv_f:
        writer = csv.writer(csv_f)

        writer.writerow([
            "Master: {}".format(run.get_master_short_name()),
            "Segments: {}".format(run.seg_num),
            "Nodes: {}".format(run.nodes),
            "Algorithm: {}".format(run.algorithm)
        ])
        writer.writerow([
            "Local Processing: {}".format(run.local),
            "Download Delay: {}".format(run.delay),
            "Model: {}".format(run.model),
            "Dir: {}".format(run.get_sub_log_dir())
        ])

        # Cannot cleanly separate videos between devices when segmentation is used
        if run.seg_num > 1:
            writer.writerow([
                "Filename",
                "Download time (s)",
                "Transfer time (s)",
                "Return time (s)",
                "Analysis time (s)",
            ])

            for video in run.videos.values():
                writer.writerow(video.get_stats())

            run.set_average_stats()
            writer.writerow(["Total"] + run.get_total_stats())
            writer.writerow(["Average"] + run.get_average_stats())

        else:
            for device_name, device in run.devices.items():
                writer.writerow([
                    "Device: {}".format(device_name),
                    "Total Power: {}".format(device.total_power),
                    "Average Power: {}".format(device.average_power)
                ])

                # Master videos list should only contain videos that haven't been transferred
                videos = [v for v in device.videos.values() if v.down_time == 0] \
                    if device_name == run.get_master_short_name() \
                    else list(device.videos.values())

                if not videos:
                    writer.writerow(["Did not analyse any videos"])
                    continue

                writer.writerow([
                    "Filename",
                    "Download time (s)",
                    "Transfer time (s)",
                    "Return time (s)",
                    "Analysis time (s)",
                ])

                for video in videos:
                    writer.writerow(video.get_stats())

                video_count = len(videos)
                if video_count > 1:
                    total_dash_down_time = sum(v.dash_down_time for v in videos)
                    total_down_time = sum(v.down_time for v in videos)
                    total_return_time = sum(v.return_time for v in videos)
                    total_analysis_time = sum(v.analysis_time for v in videos)

                    writer.writerow([
                        "Total",
                        "{:.3f}".format(total_dash_down_time),
                        "{:.3f}".format(total_down_time) if total_down_time != 0 else "n/a",
                        "{:.3f}".format(total_return_time) if total_return_time != 0 else "n/a",
                        "{:.3f}".format(total_analysis_time)
                    ])

                    writer.writerow([
                        "Average",
                        "{:.3f}".format(total_dash_down_time / video_count),
                        "{:.3f}".format(total_down_time / video_count) if total_down_time != 0 else "n/a",
                        "{:.3f}".format(total_return_time / video_count) if total_return_time != 0 else "n/a",
                        "{:.3f}".format(total_analysis_time / video_count)
                    ])

            if sum(1 for device in run.devices.values() if device) > 1:
                run.set_average_stats()
                writer.writerow(["Combined total"] + run.get_total_stats() +
                                ["Total power consumption (nW): {}".format(run.get_total_power())])
                writer.writerow(["Combined average"] + run.get_average_stats() +
                                ["Average power consumption (nW): {:.3f}".format(run.get_total_average_power())])

        writer.writerow(["Actual total time", run.get_time_string()])
        writer.writerow('')


def spread(root: str, out: str):
    root = os.path.normpath(root)
    runs = []  # type: List[Analysis]

    make_offline_spreadsheet(root, runs, out)

    for (path, dirs, files) in sorted([(path, dirs, files) for (path, dirs, files) in os.walk(root) if len(files) > 2]):
        master_sn = os.path.splitext(os.path.basename(get_master(path)))[0]
        logs = [log for log in os.listdir(path) if is_log(log)]
        devices = {device[-8:-4]: Device(device[-8:-4]) for device in logs}  # Initialise device dictionary

        videos = parse_master_log(devices, "{}.log".format(master_sn), path)
        parse_worker_logs(devices, videos, path, master_sn)

        run = Analysis(path, master_sn, devices, videos)
        make_spreadsheet(run, out)
        runs.append(run)

    runs.sort(key=lambda r: (
        r.nodes,
        r.seg_num,
        r.delay,
        r.local,
        r.get_master_short_name(),
        algorithms.index(r.algorithm),
        r.get_sub_log_dir()
    ))

    with open(out, 'a', newline='') as csv_f:
        writer = csv.writer(csv_f)

        writer.writerow(["Summary of total times"])
        writer.writerow([
            "Run",
            "Download time (s)",
            "Transfer time (s)",
            "Return time (s)",
            "Analysis time (s)",
            "Total power consumption (nW)",
            "Average power consumption (nW)",
            "Actual time (s)",
            "Human-readable time",
        ])

        for run in runs:
            writer.writerow([
                str(run),
                "{:.3f}".format(run.dash_down_time),
                "{:.3f}".format(run.down_time) if run.down_time > 0 else "n/a",
                "{:.3f}".format(run.return_time) if run.down_time > 0 else "n/a",
                "{:.3f}".format(run.analysis_time),
                run.get_total_power(),
                run.get_total_average_power(),
                run.total_time.total_seconds(),
                "{:.11}".format(str(run.total_time))
            ])

        writer.writerow(["Total"] + [
            "{:.3f}".format(sum(run.dash_down_time for run in runs)),
            "{:.3f}".format(sum(run.down_time for run in runs)),
            "{:.3f}".format(sum(run.return_time for run in runs)),
            "{:.3f}".format(sum(run.analysis_time for run in runs)),
            sum(run.get_total_power() for run in runs),
            sum(run.get_total_average_power() for run in runs),
            "{:.3f}".format(sum(run.total_time.total_seconds() for run in runs)),
            "{:.11}".format(str(timedelta(seconds=sum(run.total_time.total_seconds() for run in runs))))
        ])
        writer.writerow(["Total Average"] + [
            "{:.3f}".format(sum(run.avg_dash_down_time for run in runs)),
            "{:.3f}".format(sum(run.avg_down_time for run in runs)),
            "{:.3f}".format(sum(run.avg_return_time for run in runs)),
            "{:.3f}".format(sum(run.avg_analysis_time for run in runs)),
            "{:.3f}".format(sum(run.get_total_power() / len(runs) for run in runs)),
            "{:.3f}".format(sum(run.get_total_average_power() / len(runs) for run in runs)),
            "{:.3f}".format(sum(run.total_time.total_seconds() / len(runs) for run in runs)),
            "{:.11}".format(str(timedelta(seconds=sum(run.total_time.total_seconds() / len(runs) for run in runs))))
        ])
        writer.writerow(["True Average"] + [
            "{:.3f}".format(sum(run.avg_dash_down_time for run in runs) / len(runs)),
            "{:.3f}".format(sum(run.avg_down_time for run in runs) / len(runs)),
            "{:.3f}".format(sum(run.avg_return_time for run in runs) / len(runs)),
            "{:.3f}".format(sum(run.avg_analysis_time for run in runs) / len(runs)),
            "{:.3f}".format(sum(run.get_total_power() for run in runs) / len(runs)),
            "{:.3f}".format(sum(run.get_total_average_power() for run in runs) / len(runs)),
            "{:.3f}".format(sum(run.total_time.total_seconds() for run in runs) / len(runs)),
            "{:.11}".format(str(timedelta(seconds=sum(run.total_time.total_seconds() for run in runs) / len(runs))))
        ])


spread(args.dir, args.output)
