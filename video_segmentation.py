#!/usr/bin/env python3
import os
import subprocess
from pathlib import Path

frame_dir = Path("./frames")
out_dir = Path("./out")
map_file = Path("./filename_mapping.txt")

frame_dir.mkdir(parents=True, exist_ok=True)
out_dir.mkdir(parents=True, exist_ok=True)

segment_template = "out_{:04}.mp4"
# segment_template = "inn_{:04}.mp4"
frame_glob = f"{frame_dir}/%04d.png"
ffmpeg = ["ffmpeg", "-v", "warning", "-threads", "1"]

fps = 30
video_count = 1

for video in Path("./").glob("./*.mp4"):
    segment_name = segment_template.format(video_count)
    first_segment = segment_name

    print("Clearing frame directory")
    for frame in frame_dir.glob('*'):
        frame.unlink()

    print(f"Extracting frames from {video}")
    subprocess.run(ffmpeg + ["-r", str(fps), "-i", video, frame_glob])
    frame_num = len(os.listdir(frame_dir))

    print("Creating video segments")
    for i in range(1, frame_num - fps, fps):
        segment_name = segment_template.format(video_count)

        subprocess.run(ffmpeg + [
            "-framerate", str(fps),
            "-start_number", str(i),
            "-i", frame_glob,
            "-vframes", str(fps),
            "-an",
            "-c:v", "libx264",
            "-vf", f"fps={fps}",
            '-crf', "20",
            "-pix_fmt", "yuv420p",
            "-g", "10",
            out_dir.joinpath(segment_name)
        ])
        video_count += 1

    map_string = f"{video}  ->  {first_segment}...{segment_name}"
    print(f"Segmenting complete: {map_string}")

    with open(map_file, 'a', encoding="utf-8") as f:
        f.write(map_string + '\n')
