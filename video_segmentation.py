#!/usr/bin/env python3
import os
import platform
import subprocess
from argparse import ArgumentParser
from pathlib import Path

parser = ArgumentParser(description="Splits videos into segments of equal length")
parser.add_argument("-f", "--fps", type=int, default=30, help="frames per second of videos")
parser.add_argument("-s", "--seconds", type=float, default=2, help="length of segments in seconds")
parser.add_argument("-p", "--prefix", default="out", help="prefix for segment filenames")
parser.add_argument("-d", "--dir", default="./", help="directory of videos")
parser.add_argument("-e", "--encoding", default="-an -crf 22 -g 30", help="encoding settings")
parser.add_argument("-v", "--verbose", action="store_true", help="enable verbose output")
args = parser.parse_args()

fps = args.fps
frames_per_vid = int(fps * args.seconds)

frame_dir = Path("./frames")
out_dir = Path("./out")
map_file = Path("./filename_mapping.txt")

frame_dir.mkdir(parents=True, exist_ok=True)
out_dir.mkdir(parents=True, exist_ok=True)

segment_template = f"{args.prefix}_{{:04}}.mp4"
frame_glob = f"{frame_dir}/%04d.png"
ffmpeg = ["ffmpeg", "-threads", "1"]
encoding = args.encoding.split()

if not args.verbose:
    ffmpeg += ["-v", "warning"]

video_count = 1


def run_ffmpeg(command: list[str]):
    if args.verbose:
        print("Running:", " ".join(map(str, command)))

    if platform.system() == "Windows":
        # Run with low priority to prevent locking up computer
        subprocess.run(command, creationflags=subprocess.IDLE_PRIORITY_CLASS)
    else:
        subprocess.run(command)


for video in Path(args.dir).glob("./*.mp4"):
    segment_name = segment_template.format(video_count)
    first_segment = segment_name

    print("Clearing frame directory")
    for frame in frame_dir.glob('*'):
        frame.unlink()

    print(f"Extracting frames from {video}")
    run_ffmpeg(ffmpeg + ["-r", str(fps), "-i", video, frame_glob])
    frame_num = len(os.listdir(frame_dir))

    print("Creating video segments")
    for i in range(1, frame_num - frames_per_vid, frames_per_vid):
        segment_name = segment_template.format(video_count)

        ffmpeg_command = ffmpeg + [
            "-framerate", str(fps),
            "-start_number", str(i),
            "-i", frame_glob,
            "-vframes", str(frames_per_vid),
            "-c:v", "libx264",
            "-vf", f"fps={fps}",
            "-pix_fmt", "yuv420p"
        ] + encoding + [str(out_dir.joinpath(segment_name))]

        run_ffmpeg(ffmpeg_command)
        video_count += 1

    map_string = f"{video}  ->  {first_segment}...{segment_name}"
    print(f"Segmenting complete: {map_string}")

    with open(map_file, 'a', encoding="utf-8") as f:
        f.write(map_string + '\n')
