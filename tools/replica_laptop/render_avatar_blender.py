"""Import an ERAYA OBJ in Blender, save a .blend, and render a QA preview."""

from __future__ import annotations

import argparse
import math
from pathlib import Path

import bpy
from mathutils import Vector


def point_at(camera: bpy.types.Object, target: tuple[float, float, float]) -> None:
    direction = Vector(target) - camera.location
    camera.rotation_euler = direction.to_track_quat("-Z", "Y" if camera.type == "CAMERA" else "Z").to_euler()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--obj", type=Path, required=True)
    parser.add_argument("--blend", type=Path, required=True)
    parser.add_argument("--preview", type=Path, required=True)
    args = parser.parse_args(__import__("sys").argv[__import__("sys").argv.index("--") + 1 :])

    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.wm.obj_import(filepath=str(args.obj.resolve()))
    avatar = bpy.context.selected_objects[0]
    avatar.name = "ERAYA_Anime_Avatar"
    for polygon in avatar.data.polygons:
        polygon.use_smooth = True
    for material in avatar.data.materials:
        material.use_nodes = True
        nodes = material.node_tree.nodes
        links = material.node_tree.links
        shader = next((node for node in nodes if node.type == "BSDF_PRINCIPLED"), None)
        image = next((node for node in nodes if node.type == "TEX_IMAGE"), None)
        if shader is not None:
            shader.inputs["Roughness"].default_value = 0.78
            if image is not None:
                if not shader.inputs["Base Color"].is_linked:
                    links.new(image.outputs["Color"], shader.inputs["Base Color"])
                emission = shader.inputs.get("Emission Color") or shader.inputs.get("Emission")
                strength = shader.inputs.get("Emission Strength")
                if emission is not None:
                    links.new(image.outputs["Color"], emission)
                if strength is not None:
                    strength.default_value = 0.32

    world = bpy.context.scene.world or bpy.data.worlds.new("ERAYA World")
    bpy.context.scene.world = world
    world.color = (0.008, 0.003, 0.025)
    world.use_nodes = True
    background = world.node_tree.nodes.get("Background")
    background.inputs["Color"].default_value = (0.008, 0.002, 0.035, 1.0)
    background.inputs["Strength"].default_value = 0.35

    camera_data = bpy.data.cameras.new("ERAYA_Camera")
    camera = bpy.data.objects.new("ERAYA_Camera", camera_data)
    bpy.context.collection.objects.link(camera)
    # Blender's OBJ importer converts ERAYA's Y-up coordinates to Blender Z-up.
    camera.location = (0.0, -8.8, -0.15)
    camera.data.lens = 62
    point_at(camera, (0.0, 0.65, -0.55))
    bpy.context.scene.camera = camera

    for name, location, energy, color, size in (
        ("Key", (-3.5, -4.5, 3.8), 1150.0, (0.55, 0.30, 1.0), 4.0),
        ("Fill", (3.8, -3.0, 1.8), 900.0, (1.0, 0.24, 0.55), 3.0),
        ("Rim", (0.0, 3.8, 2.0), 1250.0, (0.18, 0.35, 1.0), 3.0),
    ):
        light_data = bpy.data.lights.new(name, "AREA")
        light_data.energy = energy
        light_data.color = color
        light_data.shape = "DISK"
        light_data.size = size
        light = bpy.data.objects.new(name, light_data)
        light.location = location
        bpy.context.collection.objects.link(light)
        point_at(light, (0.0, 0.65, -0.55))

    scene = bpy.context.scene
    scene.render.engine = "BLENDER_EEVEE"
    scene.render.resolution_x = 1024
    scene.render.resolution_y = 1024
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.render.filepath = str(args.preview.resolve())
    scene.render.film_transparent = False
    scene.view_settings.look = "AgX - Medium High Contrast"

    args.blend.parent.mkdir(parents=True, exist_ok=True)
    args.preview.parent.mkdir(parents=True, exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=str(args.blend.resolve()))
    bpy.ops.render.render(write_still=True)


if __name__ == "__main__":
    main()
