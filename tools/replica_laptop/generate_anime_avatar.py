"""Build a stylized 3D anime bust around ERAYA's 468-point identity face.

The first 468 vertices remain MediaPipe facial landmarks so the Android renderer
can animate the eyes, lips, and jaw. Procedural geometry adds a rear head,
turban volume, beard, ears, neck, and shoulders without pretending to be a
photogrammetry scan.
"""

from __future__ import annotations

import argparse
import json
import math
import time
import zipfile
from pathlib import Path
from typing import Callable

import numpy as np

from generate_eraya_replica import (
    FILES,
    build_geometry,
    calculate_normals,
    detect_landmarks,
    load_photo,
    parse_topology,
    write_binary,
    write_obj,
)

PORTRAIT_FILE = "replica_portrait.jpg"
TALKING_PORTRAIT_FILE = "replica_talking.jpg"
BLINK_PORTRAIT_FILE = "replica_blink.jpg"
LOOK_LEFT_PORTRAIT_FILE = "replica_look_left.jpg"
LOOK_RIGHT_PORTRAIT_FILE = "replica_look_right.jpg"


class MeshBuilder:
    def __init__(self, positions: np.ndarray, uvs: np.ndarray, indices: np.ndarray) -> None:
        self.positions = [tuple(map(float, row)) for row in positions]
        self.uvs = [tuple(map(float, row)) for row in uvs]
        self.indices = list(map(int, indices))

    def add_ellipsoid(
        self,
        center: tuple[float, float, float],
        radii: tuple[float, float, float],
        color_uv: tuple[float, float],
        lat_segments: int = 14,
        lon_segments: int = 24,
        lat_min: float = -math.pi / 2,
        lat_max: float = math.pi / 2,
        lon_min: float = 0.0,
        lon_max: float = 2 * math.pi,
        uv_mapper: Callable[[float, float, float], tuple[float, float]] | None = None,
    ) -> None:
        base = len(self.positions)
        cx, cy, cz = center
        rx, ry, rz = radii
        for lat_index in range(lat_segments + 1):
            lat = lat_min + (lat_max - lat_min) * lat_index / lat_segments
            ring = math.cos(lat)
            for lon_index in range(lon_segments + 1):
                lon = lon_min + (lon_max - lon_min) * lon_index / lon_segments
                point = (
                    cx + rx * ring * math.cos(lon),
                    cy + ry * math.sin(lat),
                    cz + rz * ring * math.sin(lon),
                )
                self.positions.append(point)
                self.uvs.append(uv_mapper(*point) if uv_mapper is not None else color_uv)
        stride = lon_segments + 1
        for lat_index in range(lat_segments):
            for lon_index in range(lon_segments):
                a = base + lat_index * stride + lon_index
                b = a + stride
                self.indices.extend((a, b, a + 1, a + 1, b, b + 1))

    def add_torus(
        self,
        center: tuple[float, float, float],
        major_radius: float,
        tube_radius: tuple[float, float],
        color_uv: tuple[float, float],
        tilt_degrees: float = 0.0,
        major_segments: int = 28,
        tube_segments: int = 8,
    ) -> None:
        base = len(self.positions)
        cx, cy, cz = center
        tube_y, tube_depth = tube_radius
        tilt = math.radians(tilt_degrees)
        cos_tilt, sin_tilt = math.cos(tilt), math.sin(tilt)
        for major_index in range(major_segments + 1):
            major = 2 * math.pi * major_index / major_segments
            cos_major, sin_major = math.cos(major), math.sin(major)
            for tube_index in range(tube_segments + 1):
                tube = 2 * math.pi * tube_index / tube_segments
                radial = major_radius + tube_depth * math.cos(tube)
                x = radial * cos_major
                y = tube_y * math.sin(tube)
                z = radial * sin_major
                rotated_x = x * cos_tilt - y * sin_tilt
                rotated_y = x * sin_tilt + y * cos_tilt
                self.positions.append((cx + rotated_x, cy + rotated_y, cz + z))
                self.uvs.append(color_uv)
        stride = tube_segments + 1
        for major_index in range(major_segments):
            for tube_index in range(tube_segments):
                a = base + major_index * stride + tube_index
                b = a + stride
                self.indices.extend((a, b, a + 1, a + 1, b, b + 1))

    def add_cylinder(
        self,
        center: tuple[float, float, float],
        radius: tuple[float, float],
        height: float,
        color_uv: tuple[float, float],
        segments: int = 24,
    ) -> None:
        base = len(self.positions)
        cx, cy, cz = center
        rx, rz = radius
        for end in (-0.5, 0.5):
            for segment in range(segments + 1):
                angle = 2 * math.pi * segment / segments
                self.positions.append((cx + rx * math.cos(angle), cy + height * end, cz + rz * math.sin(angle)))
                self.uvs.append(color_uv)
        stride = segments + 1
        for segment in range(segments):
            a = base + segment
            b = a + stride
            self.indices.extend((a, b, a + 1, a + 1, b, b + 1))

    def arrays(self) -> tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray]:
        positions = np.asarray(self.positions, dtype=np.float32)
        uvs = np.asarray(self.uvs, dtype=np.float32)
        indices = np.asarray(self.indices, dtype=np.int32)
        normals = calculate_normals(positions, indices)
        return positions, normals, uvs, indices


def build_anime_bust(
    face_positions: np.ndarray,
    face_uvs: np.ndarray,
    face_indices: np.ndarray,
) -> tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray]:
    mesh = MeshBuilder(face_positions, face_uvs, face_indices)

    # Texture lookups sample stable regions of the generated anime portrait.
    turban_uv = (0.50, 0.84)
    beard_uv = (0.50, 0.40)
    jacket_uv = (0.16, 0.10)
    skin_uv = (0.50, 0.56)

    # Rear head and ears sit behind the measured face surface. The rear volume uses the
    # turban sample: a skin-coloured shell showed as a pale halo around turbaned avatars.
    mesh.add_ellipsoid(
        (0.0, 0.0, -0.78),
        (1.00, 1.28, 1.00),
        turban_uv,
        16,
        18,
        -math.pi / 2,
        math.pi / 2,
        math.pi,
        2 * math.pi,
    )
    mesh.add_ellipsoid((-0.98, -0.02, -0.72), (0.11, 0.25, 0.17), skin_uv, 9, 14)
    mesh.add_ellipsoid((0.98, -0.02, -0.72), (0.11, 0.25, 0.17), skin_uv, 9, 14)

    # Map the clean identity plate onto the front of the cap, preserving the owner's actual
    # turban folds. Side/back samples clamp to dark turban pixels instead of showing a flat dome.
    def turban_map(x: float, y: float, _z: float) -> tuple[float, float]:
        # Stay inside the dark central folds. The wider v2 lookup reached the portrait's
        # pale background at the cap edge and the forehead at the cap base.
        u = 0.50 + (x / 1.20) * 0.15
        v = 0.80 + ((y - 0.65) / 1.27) * 0.15
        return (min(0.66, max(0.34, u)), min(0.95, max(0.80, v)))

    mesh.add_ellipsoid(
        (0.0, 1.02, -0.92),
        (1.20, 0.90, 1.10),
        turban_uv,
        18,
        36,
        -0.42,
        math.pi / 2,
        uv_mapper=turban_map,
    )

    # The landmark surface already carries the full textured beard. A front beard shell used by
    # v1 hid the mouth and jaw, so v2 keeps only small rear volumes for a readable side silhouette.
    mesh.add_ellipsoid((-0.56, -0.72, -0.78), (0.27, 0.48, 0.24), beard_uv, 10, 16)
    mesh.add_ellipsoid((0.56, -0.72, -0.78), (0.27, 0.48, 0.24), beard_uv, 10, 16)
    mesh.add_ellipsoid((0.0, -1.03, -0.63), (0.48, 0.28, 0.24), beard_uv, 9, 18)

    # Neck and broad stylized shoulders form an anime bust for the Room view.
    mesh.add_cylinder((0.0, -1.52, -0.90), (0.34, 0.36), 0.66, jacket_uv, 28)
    mesh.add_ellipsoid((0.0, -2.04, -1.02), (1.62, 0.68, 0.76), jacket_uv, 14, 32)

    return mesh.arrays()


def generate(
    image_path: Path,
    topology_path: Path,
    output: Path,
    portrait_source: Path | None = None,
    talking_portrait_source: Path | None = None,
    blink_portrait_source: Path | None = None,
    look_left_portrait_source: Path | None = None,
    look_right_portrait_source: Path | None = None,
) -> Path:
    output.mkdir(parents=True, exist_ok=True)
    image = load_photo(image_path)
    landmarks = detect_landmarks(image)
    face_indices = parse_topology(topology_path)
    face_positions, _, face_uvs = build_geometry(landmarks, face_indices)
    positions, normals, uvs, indices = build_anime_bust(face_positions, face_uvs, face_indices)

    mesh_path = output / FILES["mesh"]
    obj_path = output / FILES["obj"]
    material_path = output / FILES["material"]
    texture_path = output / FILES["texture"]
    portrait_path = output / PORTRAIT_FILE
    talking_portrait_path = output / TALKING_PORTRAIT_FILE
    blink_portrait_path = output / BLINK_PORTRAIT_FILE
    look_left_portrait_path = output / LOOK_LEFT_PORTRAIT_FILE
    look_right_portrait_path = output / LOOK_RIGHT_PORTRAIT_FILE
    manifest_path = output / FILES["manifest"]
    package_path = output / "ERAYA-anime-avatar.zip"

    write_binary(mesh_path, positions, normals, uvs, indices)
    write_obj(obj_path, material_path, positions, normals, uvs, indices)
    image.save(texture_path, format="JPEG", quality=94, optimize=True)
    portrait = load_photo(portrait_source or image_path)
    portrait.save(portrait_path, format="JPEG", quality=95, optimize=True)
    talking_portrait = load_photo(talking_portrait_source or portrait_source or image_path)
    talking_portrait.save(talking_portrait_path, format="JPEG", quality=95, optimize=True)
    blink_portrait = load_photo(blink_portrait_source or portrait_source or image_path)
    blink_portrait.save(blink_portrait_path, format="JPEG", quality=95, optimize=True)
    look_left_portrait = load_photo(look_left_portrait_source or portrait_source or image_path)
    look_left_portrait.save(look_left_portrait_path, format="JPEG", quality=95, optimize=True)
    look_right_portrait = load_photo(look_right_portrait_source or portrait_source or image_path)
    look_right_portrait.save(look_right_portrait_path, format="JPEG", quality=95, optimize=True)
    manifest = {
        "schema": "eraya.replica.mesh.v1",
        "generated_at": int(time.time() * 1000),
        "device_only": True,
        "source": "laptop_import",
        "engine": "mediapipe-468-plus-anime-portrait-v6-gaze",
        "presentation": "portrait_only",
        "style": "anime",
        "reconstruction_scope": "solid_anime_bust_with_multiview_identity_reference",
        "recommended_rotation_degrees": 42,
        "landmark_vertex_count": 468,
        "vertex_count": int(len(positions)),
        "triangle_count": int(len(indices) // 3),
        "mesh": FILES["mesh"],
        "blender_obj": FILES["obj"],
        "material": FILES["material"],
        "texture": FILES["texture"],
        "portrait": PORTRAIT_FILE,
        "talking_portrait": TALKING_PORTRAIT_FILE,
        "blink_portrait": BLINK_PORTRAIT_FILE,
        "look_left_portrait": LOOK_LEFT_PORTRAIT_FILE,
        "look_right_portrait": LOOK_RIGHT_PORTRAIT_FILE,
    }
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    with zipfile.ZipFile(package_path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for name in (
            *FILES.values(),
            PORTRAIT_FILE,
            TALKING_PORTRAIT_FILE,
            BLINK_PORTRAIT_FILE,
            LOOK_LEFT_PORTRAIT_FILE,
            LOOK_RIGHT_PORTRAIT_FILE,
        ):
            archive.write(output / name, arcname=name)

    print(f"ERAYA anime avatar: {len(positions)} vertices, {len(indices) // 3} triangles")
    print(f"Blender OBJ: {obj_path}")
    print(f"Phone package: {package_path}")
    return package_path


def main() -> int:
    root = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image", type=Path, required=True, help="Generated front-facing anime portrait")
    parser.add_argument("--portrait", type=Path, help="Optional wide hero portrait for the Android room card")
    parser.add_argument("--talking-portrait", type=Path, help="Optional matched open-mouth speaking frame")
    parser.add_argument("--blink-portrait", type=Path, help="Optional matched closed-eye blink frame")
    parser.add_argument("--look-left-portrait", type=Path, help="Optional matched screen-left eye-gaze frame")
    parser.add_argument("--look-right-portrait", type=Path, help="Optional matched screen-right eye-gaze frame")
    parser.add_argument("--topology", type=Path, default=root / "canonical_face_model.obj")
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    generate(
        args.image.resolve(),
        args.topology.resolve(),
        args.out.resolve(),
        args.portrait.resolve() if args.portrait else None,
        args.talking_portrait.resolve() if args.talking_portrait else None,
        args.blink_portrait.resolve() if args.blink_portrait else None,
        args.look_left_portrait.resolve() if args.look_left_portrait else None,
        args.look_right_portrait.resolve() if args.look_right_portrait else None,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
