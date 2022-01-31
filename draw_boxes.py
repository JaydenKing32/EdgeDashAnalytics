import json
import os
from argparse import ArgumentParser
from datetime import datetime

import cv2.cv2 as cv2

parser = ArgumentParser(description="Draw boundary boxes of detected objects in a video file")
parser.add_argument("video", help="Filepath of video file")
parser.add_argument("det", help="Filepath of JSON file containing object detection data")
args = parser.parse_args()

with open(args.det) as json_file:
    detections = json.load(json_file)

video_name = os.path.splitext(os.path.basename(args.video))[0]
out_filename = f"{video_name}-boxes.avi"

if os.path.isfile(out_filename):
    os.remove(out_filename)

cap = cv2.VideoCapture(args.video)
frame_width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
frame_height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
frame_rate = cap.get(cv2.CAP_PROP_FPS)
fourcc = cv2.VideoWriter_fourcc(*"MJPG")
out = cv2.VideoWriter(out_filename, fourcc, frame_rate, (frame_width, frame_height))

frame = 0

while cap.isOpened():
    ret, image_np = cap.read()

    if not ret:
        print(f"{datetime.now()}: Completed video analysis")
        break

    for det in detections[frame]["hazards"]:
        bbox = det["bBox"]
        colour = (0, 0, 255) if det["danger"] else (0, 255, 0)

        image_np = cv2.rectangle(image_np, (bbox["left"], bbox["top"]), (bbox["right"], bbox["bottom"]), colour, 1)

        f_face = cv2.FONT_HERSHEY_SIMPLEX
        f_colour = (255, 255, 255)
        image_np = cv2.putText(image_np, det["category"], (bbox["left"], bbox["bottom"] + 10), f_face, 0.5, f_colour, 1)
    # cv2.imwrite(f"./out/{frame:04d}.png", image_np)
    out.write(image_np)

    frame += 1

cap.release()
out.release()
