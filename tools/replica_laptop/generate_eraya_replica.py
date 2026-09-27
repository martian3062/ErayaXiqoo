"""Generate an ERAYA facial-relief package locally from one front-facing photo.

The output is deliberately honest: a textured 468-landmark facial surface for
front-facing room use, not a complete head or body scan. No image is uploaded.
"""

from __future__ import annotations

import argparse
import json
import math
import struct
import sys
import time
import zipfile
from pathlib import Path

import mediapipe as mp
import numpy as np
from PIL import Image, ImageOps

MAGIC = 0x45524159  # ERAY
FORMAT_VERSION = 1
MAX_TEXTURE_EDGE = 1280
DEPTH_GAIN = 1.35
REQUIRED_VERTICES = 468
FILES = {
    "mesh": "replica.mesh",
    "obj": "replica.obj",
    "material": "replica.mtl",
    "texture": "replica_texture.jpg",
    "manifest": "replica.json",
}


def parse_topology(path: Path) -> np.ndarray:
    faces: list[tuple[int, int, int]] = []
    vertex_count = 0
    for raw in path.read_text(encoding="utf-8").splitlines():
        if raw.startswith("v "):
            vertex_count += 1
        elif raw.startswith("f "):
            parts = raw.split()[1:]
            if len(parts) != 3:
                raise ValueError("Canonical topology contains a non-triangle face")
            a, b, c = (int(part.split("/", 1)[0]) - 1 for part in parts)
            # Image Y is flipped below, so reverse the canonical winding.
            faces.append((a, c, b))
    if vertex_count != REQUIRED_VERTICES or not faces:
        raise ValueError(
            f"Expected {REQUIRED_VERTICES} canonical vertices; got {vertex_count}"
        )
    indices = np.asarray(faces, dtype=np.int32).reshape(-1)
    if indices.min() < 0 or indices.max() >= REQUIRED_VERTICES:
        raise ValueError("Canonical topology contains an invalid vertex index")
    return indices


def load_photo(path: Path) -> Image.Image:
    with Image.open(path) as source:
        image = ImageOps.exif_transpose(source).convert("RGB")
    scale = min(1.0, MAX_TEXTURE_EDGE / max(image.size))
    if scale < 1.0:
        image = image.resize(
            (max(1, round(image.width * scale)), max(1, round(image.height * scale))),
            Image.Resampling.LANCZOS,
        )
    return image


def detect_landmarks(image: Image.Image) -> np.ndarray:
    rgb = np.asarray(image)
    with mp.solutions.face_mesh.FaceMesh(
        static_image_mode=True,
        max_num_faces=2,
        refine_landmarks=False,
        min_detection_confidence=0.55,
    ) as detector:
        result = detector.process(rgb)
    faces = result.multi_face_landmarks or []
    if not faces:
        raise ValueError(
            "No face found. Use an evenly lit, front-facing photo without glasses or a hat."
        )
    if len(faces) != 1:
        raise ValueError("More than one face was found. Use a photo containing only you.")
    points = faces[0].landmark
    if len(points) != REQUIRED_VERTICES:
        raise ValueError(f"Expected 468 landmarks; detector returned {len(points)}")
    return np.asarray([(p.x, p.y, p.z) for p in points], dtype=np.float32)


def build_geometry(landmarks: np.ndarray, indices: np.ndarray) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    min_xy = landmarks[:, :2].min(axis=0)
    max_xy = landmarks[:, :2].max(axis=0)
    center = (min_xy + max_xy) * 0.5
    half_width = max(float(max_xy[0] - min_xy[0]) * 0.5, 1e-6)
    mean_z = float(landmarks[:, 2].mean())

    positions = np.empty((REQUIRED_VERTICES, 3), dtype=np.float32)
    positions[:, 0] = (landmarks[:, 0] - center[0]) / half_width
    positions[:, 1] = (center[1] - landmarks[:, 1]) / half_width
    positions[:, 2] = -(landmarks[:, 2] - mean_z) / half_width * DEPTH_GAIN

    uvs = np.empty((REQUIRED_VERTICES, 2), dtype=np.float32)
    uvs[:, 0] = np.clip(landmarks[:, 0], 0.0, 1.0)
    uvs[:, 1] = np.clip(1.0 - landmarks[:, 1], 0.0, 1.0)
    normals = calculate_normals(positions, indices)
    return positions, normals, uvs


def calculate_normals(positions: np.ndarray, indices: np.ndarray) -> np.ndarray:
    triangles = indices.reshape(-1, 3)
    p0 = positions[triangles[:, 0]]
    p1 = positions[triangles[:, 1]]
    p2 = positions[triangles[:, 2]]
    face_normals = np.cross(p1 - p0, p2 - p0)
    normals = np.zeros_like(positions)
    for corner in range(3):
        np.add.at(normals, triangles[:, corner], face_normals)
    lengths = np.linalg.norm(normals, axis=1, keepdims=True)
    good = lengths[:, 0] > 1e-9
    normals[good] /= lengths[good]
    normals[~good] = (0.0, 0.0, 1.0)
    return normals.astype(np.float32)


def write_binary(
    path: Path,
    positions: np.ndarray,
    normals: np.ndarray,
    uvs: np.ndarray,
    indices: np.ndarray,
) -> None:
    with path.open("wb") as out:
        out.write(struct.pack(">4i", MAGIC, FORMAT_VERSION, len(positions), len(indices)))
        for position, normal, uv in zip(positions, normals, uvs, strict=True):
            out.write(struct.pack(">8f", *position, *normal, *uv))
        out.write(struct.pack(f">{len(indices)}i", *indices.tolist()))


def write_obj(
    obj_path: Path,
    material_path: Path,
    positions: np.ndarray,
    normals: np.ndarray,
    uvs: np.ndarray,
    indices: np.ndarray,
) -> None:
    material_path.write_text(
        "\n".join(
            (
                "newmtl ErayaReplica",
                "Ka 0.200000 0.200000 0.200000",
                "Kd 1.000000 1.000000 1.000000",
                "Ks 0.080000 0.080000 0.080000",
                "Ns 18.000000",
                f"map_Kd {FILES['texture']}",
                "",
            )
        ),
        encoding="utf-8",
    )
    with obj_path.open("w", encoding="utf-8", newline="\n") as out:
        out.write("# ERAYA private facial relief mesh\n")
        out.write(f"mtllib {FILES['material']}\n")
        out.write("o ErayaReplicaFace\n")
        for x, y, z in positions:
            out.write(f"v {x:.7f} {y:.7f} {z:.7f}\n")
        for u, v in uvs:
            out.write(f"vt {u:.7f} {v:.7f}\n")
        for x, y, z in normals:
            out.write(f"vn {x:.7f} {y:.7f} {z:.7f}\n")
        out.write("usemtl ErayaReplica\n")
        for a, b, c in indices.reshape(-1, 3):
            a, b, c = int(a) + 1, int(b) + 1, int(c) + 1
            out.write(f"f {a}/{a}/{a} {b}/{b}/{b} {c}/{c}/{c}\n")


def write_package(image_path: Path, topology_path: Path, output: Path) -> Path:
    output.mkdir(parents=True, exist_ok=True)
    image = load_photo(image_path)
    landmarks = detect_landmarks(image)
    indices = parse_topology(topology_path)
    positions, normals, uvs = build_geometry(landmarks, indices)

    mesh_path = output / FILES["mesh"]
    obj_path = output / FILES["obj"]
    material_path = output / FILES["material"]
    texture_path = output / FILES["texture"]
    manifest_path = output / FILES["manifest"]
    package_path = output / "ERAYA-replica.zip"

    write_binary(mesh_path, positions, normals, uvs, indices)
    write_obj(obj_path, material_path, positions, normals, uvs, indices)
    image.save(texture_path, format="JPEG", quality=92, optimize=True)
    manifest = {
        "schema": "eraya.replica.mesh.v1",
        "generated_at": int(time.time() * 1000),
        "device_only": True,
        "source": "laptop_import",
        "engine": "mediapipe-face-mesh-468-laptop",
        "reconstruction_scope": "textured_front_face_relief",
        "recommended_rotation_degrees": 30,
        "vertex_count": int(len(positions)),
        "triangle_count": int(len(indices) // 3),
        "mesh": FILES["mesh"],
        "blender_obj": FILES["obj"],
        "material": FILES["material"],
        "texture": FILES["texture"],
    }
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    with zipfile.ZipFile(package_path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for name in FILES.values():
            archive.write(output / name, arcname=name)

    print(f"ERAYA replica: {len(positions)} vertices, {len(indices) // 3} triangles")
    print(f"Blender OBJ: {obj_path}")
    print(f"Phone package: {package_path}")
    print("Scope: textured front-face relief; faithful viewing range is about +/-30 degrees.")
    return package_path


def main() -> int:
    root = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image", type=Path, required=True, help="Clear front-facing photo")
    parser.add_argument(
        "--topology",
        type=Path,
        default=root / "canonical_face_model.obj",
        help="MediaPipe canonical 468-point OBJ",
    )
    parser.add_argument(
        "--out",
        type=Path,
        default=Path.cwd() / "outputs" / "eraya-replica",
        help="Private output directory",
    )
    args = parser.parse_args()
    for path in (args.image, args.topology):
        if not path.is_file():
            parser.error(f"file not found: {path}")
    try:
        write_package(args.image.resolve(), args.topology.resolve(), args.out.resolve())
    except Exception as exc:
        print(f"ERAYA replica generation failed: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
