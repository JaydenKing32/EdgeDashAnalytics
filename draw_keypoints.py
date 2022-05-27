import json
import os
from argparse import ArgumentParser
from typing import Union

import cv2
from tqdm import tqdm

body_parts = [
    "NOSE",
    "LEFT_EYE",
    "RIGHT_EYE",
    "LEFT_EAR",
    "RIGHT_EAR",
    "LEFT_SHOULDER",
    "RIGHT_SHOULDER",
    "LEFT_ELBOW",
    "RIGHT_ELBOW",
    "LEFT_WRIST",
    "RIGHT_WRIST",
    "LEFT_HIP",
    "RIGHT_HIP",
    "LEFT_KNEE",
    "RIGHT_KNEE",
    "LEFT_ANKLE",
    "RIGHT_ANKLE"
]

parser = ArgumentParser(description="Visualise body key-points in a video file")
parser.add_argument("video", help="Filepath of video file")
parser.add_argument("key", help="Filepath of JSON file containing key-point data")
args = parser.parse_args()

key_points: list[dict[str, Union[float, int, list[dict[str, Union[str, float, dict[str, float]]]]]]]

with open(args.key) as json_file:
    key_points = json.load(json_file)

video_name = os.path.splitext(os.path.basename(args.video))[0]
out_filename = f"{video_name}-keyPoints.avi"

if os.path.isfile(out_filename):
    os.remove(out_filename)

cap = cv2.VideoCapture(args.video)
frame_width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
frame_height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
frame_rate = cap.get(cv2.CAP_PROP_FPS)
fourcc = cv2.VideoWriter_fourcc(*"MJPG")
out = cv2.VideoWriter(out_filename, fourcc, frame_rate, (frame_width, frame_height))

frame = 0

length = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))

with tqdm(desc=out_filename, total=length, smoothing=0.01) as pbar:
    while cap.isOpened():
        ret, image_np = cap.read()

        if not ret:
            break

        for key in key_points[frame]["keyPoints"]:
            x, y = key["coordinate"].values()
            x, y = int(x), int(y)
            part = key["bodyPart"]

            # x = int(x + (frame_width / 2) - (x * 0.4))

            dot_size = 2
            dot_colour = (0, 0, 255)
            image_np = cv2.circle(image_np, (x, y), dot_size, dot_colour, -1)

            font_face = cv2.FONT_HERSHEY_SIMPLEX
            font_colour = (0, 255, 0)
            offset = 4
            image_np = cv2.putText(image_np, str(part), (x + offset, y - offset), font_face, 0.5, font_colour, 1)

        circle_size = 20
        circle_colour = (0, 0, 255) if key_points[frame]["distracted"] else (0, 255, 0)
        image_np = cv2.circle(image_np, (circle_size, circle_size), circle_size, circle_colour, -1)

        # cv2.imwrite(f"./out/{frame:04d}.png", image_np)
        out.write(image_np)

        frame += 1
        pbar.update()

cap.release()
out.release()
