"""Capture a small, guided identity set from the laptop webcam.

The preview is mirrored for natural posing, while saved frames retain the camera's
original orientation. Each pose chooses the sharpest frame from a short burst.
"""

from __future__ import annotations

import argparse
import time
from pathlib import Path

import cv2
import numpy as np


POSES = (
    ("front", "LOOK STRAIGHT AT THE CAMERA"),
    ("left", "TURN YOUR FACE SLIGHTLY LEFT"),
    ("right", "TURN YOUR FACE SLIGHTLY RIGHT"),
    ("chin_up", "CHIN SLIGHTLY UP - RELAXED FACE"),
)


def open_camera() -> cv2.VideoCapture:
    for index in range(4):
        camera = cv2.VideoCapture(index, cv2.CAP_DSHOW)
        if camera.isOpened():
            camera.set(cv2.CAP_PROP_FRAME_WIDTH, 1280)
            camera.set(cv2.CAP_PROP_FRAME_HEIGHT, 720)
            camera.set(cv2.CAP_PROP_AUTOFOCUS, 1)
            ok, _ = camera.read()
            if ok:
                return camera
        camera.release()
    raise RuntimeError("No working laptop camera was found")


def sharpness(frame: np.ndarray) -> float:
    grey = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
    return float(cv2.Laplacian(grey, cv2.CV_64F).var())


def overlay(frame: np.ndarray, prompt: str, remaining: float, captured: int) -> np.ndarray:
    shown = cv2.flip(frame, 1)
    shade = shown.copy()
    cv2.rectangle(shade, (0, 0), (shown.shape[1], 130), (18, 7, 28), -1)
    shown = cv2.addWeighted(shade, 0.78, shown, 0.22, 0)
    cv2.putText(shown, "ERAYA IDENTITY CAPTURE", (32, 42), cv2.FONT_HERSHEY_SIMPLEX, 0.9, (255, 135, 210), 2)
    cv2.putText(shown, prompt, (32, 86), cv2.FONT_HERSHEY_SIMPLEX, 0.78, (255, 255, 255), 2)
    cv2.putText(shown, f"Hold still: {max(0, int(remaining) + 1)}   Shot {captured + 1}/4", (32, 120), cv2.FONT_HERSHEY_SIMPLEX, 0.68, (220, 210, 255), 2)
    return shown


def capture(output: Path, seconds_per_pose: float) -> list[Path]:
    output.mkdir(parents=True, exist_ok=True)
    camera = open_camera()
    window = "ERAYA - four private identity photos"
    cv2.namedWindow(window, cv2.WINDOW_NORMAL)
    cv2.resizeWindow(window, 960, 600)
    cv2.setWindowProperty(window, cv2.WND_PROP_TOPMOST, 1)
    saved: list[Path] = []
    try:
        for pose_index, (slug, prompt) in enumerate(POSES):
            start = time.monotonic()
            candidates: list[np.ndarray] = []
            while True:
                ok, frame = camera.read()
                if not ok:
                    raise RuntimeError("Laptop camera stopped returning frames")
                elapsed = time.monotonic() - start
                remaining = seconds_per_pose - elapsed
                if remaining <= 0.8:
                    candidates.append(frame.copy())
                cv2.imshow(window, overlay(frame, prompt, remaining, pose_index))
                key = cv2.waitKey(1) & 0xFF
                if key in (27, ord("q")):
                    raise KeyboardInterrupt("Capture cancelled")
                if elapsed >= seconds_per_pose:
                    break
            best = max(candidates, key=sharpness)
            destination = output / f"{pose_index + 1:02d}-{slug}.jpg"
            if not cv2.imwrite(str(destination), best, [cv2.IMWRITE_JPEG_QUALITY, 96]):
                raise RuntimeError(f"Could not save {destination}")
            saved.append(destination)
        return saved
    finally:
        camera.release()
        cv2.destroyAllWindows()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--seconds-per-pose", type=float, default=4.0)
    args = parser.parse_args()
    files = capture(args.output, args.seconds_per_pose)
    for file in files:
        print(file.resolve())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
