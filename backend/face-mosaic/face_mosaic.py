"""Use a YOLOv9 face detector to pixelate faces in a local video.

The YOLOv9 runtime is provided separately under GPL-3.0. See
THIRD_PARTY_NOTICES.md before distributing or deploying this component.
"""

import argparse
import os
import shutil
import subprocess
import sys
import time
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Sequence, Tuple

import cv2
import numpy as np
import torch

FILE = Path(__file__).resolve()
ROOT = FILE.parent
if os.getenv("YOLOV9_ROOT"):
    YOLOV9_ROOT = Path(os.environ["YOLOV9_ROOT"]).expanduser().resolve()
elif (ROOT / "yolov9" / "models").is_dir():
    YOLOV9_ROOT = ROOT / "yolov9"
else:
    YOLOV9_ROOT = ROOT

if not (YOLOV9_ROOT / "models").is_dir() or not (YOLOV9_ROOT / "utils").is_dir():
    raise RuntimeError(
        "找不到 YOLOv9 源码。请把官方仓库克隆到脚本旁的 yolov9 目录，"
        "或设置 YOLOV9_ROOT 环境变量。"
    )
if str(YOLOV9_ROOT) not in sys.path:
    sys.path.insert(0, str(YOLOV9_ROOT))

LOCAL_CONFIG_DIR = ROOT / ".runtime" / "ultralytics"
LOCAL_CONFIG_DIR.mkdir(parents=True, exist_ok=True)
os.environ.setdefault("YOLOV5_CONFIG_DIR", str(LOCAL_CONFIG_DIR))

from models.common import DetectMultiBackend
from utils.dataloaders import letterbox
from utils.general import check_img_size, non_max_suppression, scale_boxes
from utils.torch_utils import select_device, smart_inference_mode


VIDEO_SUFFIXES = {
    ".asf",
    ".avi",
    ".m4v",
    ".mkv",
    ".mov",
    ".mp4",
    ".mpeg",
    ".mpg",
    ".ts",
    ".wmv",
}


def default_weights() -> Path:
    configured_weights = os.getenv("FACE_MODEL_PATH")
    if configured_weights:
        return Path(configured_weights).expanduser()
    desktop_weights = Path.home() / "Desktop" / "yolo-v9" / "best.pt"
    return desktop_weights if desktop_weights.exists() else ROOT / "best.pt"


def model_names_to_dict(names) -> Dict[int, str]:
    if isinstance(names, dict):
        return {int(index): str(name) for index, name in names.items()}
    return {index: str(name) for index, name in enumerate(names)}


def choose_face_classes(names: Dict[int, str], requested: Optional[Sequence[int]]) -> List[int]:
    if requested:
        invalid = sorted(set(requested) - set(names))
        if invalid:
            raise ValueError("类别编号不存在：{}".format(", ".join(map(str, invalid))))
        return sorted(set(requested))

    if len(names) == 1:
        return [next(iter(names))]

    keywords = ("face", "head", "人脸", "脸")
    matches = [
        index
        for index, name in names.items()
        if any(keyword in name.lower() for keyword in keywords)
    ]
    if matches:
        return matches

    classes_text = ", ".join("{}:{}".format(index, name) for index, name in names.items())
    raise ValueError(
        "该权重有多个类别，但没有找到 face/head 类别。"
        "请用 --classes 指定真正的人脸类别编号。模型类别：{}".format(classes_text)
    )


def unwrap_prediction(prediction):
    """Support both converted YOLOv9 checkpoints and original dual-head checkpoints."""
    if torch.is_tensor(prediction):
        return prediction

    if isinstance(prediction, (list, tuple)):
        if (
            len(prediction) >= 1
            and isinstance(prediction[0], (list, tuple))
            and len(prediction[0]) > 1
            and torch.is_tensor(prediction[0][1])
        ):
            return prediction[0][1]
        if prediction and torch.is_tensor(prediction[0]):
            return prediction[0]

    raise TypeError(
        "无法识别模型输出结构；请确认权重来自官方 YOLOv9，"
        "或先使用官方 reparameterization/转换流程。"
    )


def pixelate_regions(
    image: np.ndarray,
    boxes: Iterable[Sequence[float]],
    block_size: int,
    expand: float,
    min_face: int,
) -> int:
    height, width = image.shape[:2]
    applied = 0

    for box in boxes:
        x1, y1, x2, y2 = (int(round(float(value))) for value in box[:4])
        face_width = x2 - x1
        face_height = y2 - y1
        if face_width < min_face or face_height < min_face:
            continue

        pad_x = int(round(face_width * expand))
        pad_y = int(round(face_height * expand))
        x1 = max(0, x1 - pad_x)
        y1 = max(0, y1 - pad_y)
        x2 = min(width, x2 + pad_x)
        y2 = min(height, y2 + pad_y)
        if x2 <= x1 or y2 <= y1:
            continue

        region = image[y1:y2, x1:x2]
        region_height, region_width = region.shape[:2]
        small_width = max(1, region_width // block_size)
        small_height = max(1, region_height // block_size)
        small = cv2.resize(region, (small_width, small_height), interpolation=cv2.INTER_AREA)
        mosaic = cv2.resize(small, (region_width, region_height), interpolation=cv2.INTER_NEAREST)
        image[y1:y2, x1:x2] = mosaic
        applied += 1

    return applied


def prepare_tensor(frame: np.ndarray, image_size: Tuple[int, int], stride: int, device, fp16: bool):
    resized = letterbox(frame, image_size, stride=stride, auto=True)[0]
    resized = resized.transpose((2, 0, 1))[::-1]
    resized = np.ascontiguousarray(resized)
    tensor = torch.from_numpy(resized).to(device)
    tensor = tensor.half() if fp16 else tensor.float()
    tensor /= 255.0
    return tensor.unsqueeze(0)


def find_ffmpeg() -> Optional[str]:
    executable = shutil.which("ffmpeg")
    if executable:
        return executable

    try:
        import imageio_ffmpeg

        return imageio_ffmpeg.get_ffmpeg_exe()
    except (ImportError, RuntimeError):
        return None


def merge_original_audio(
    ffmpeg: str,
    silent_video: Path,
    source_video: Path,
    output_video: Path,
) -> bool:
    command = [
        ffmpeg,
        "-hide_banner",
        "-loglevel",
        "error",
        "-y",
        "-i",
        str(silent_video),
        "-i",
        str(source_video),
        "-map",
        "0:v:0",
        "-map",
        "1:a?",
        "-c:v",
        "copy",
        "-c:a",
        "aac",
        "-shortest",
        str(output_video),
    ]
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode == 0 and output_video.exists() and output_video.stat().st_size > 0:
        silent_video.unlink()
        return True

    print("警告：音频合并失败，将输出无声视频。")
    if result.stderr:
        print(result.stderr.strip())
    if output_video.exists():
        output_video.unlink()
    os.replace(str(silent_video), str(output_video))
    return False


@smart_inference_mode()
def process_video(args: argparse.Namespace) -> Path:
    source = Path(args.source).expanduser().resolve()
    weights = Path(args.weights).expanduser().resolve()
    if not source.is_file():
        raise FileNotFoundError("找不到输入视频：{}".format(source))
    if source.suffix.lower() not in VIDEO_SUFFIXES:
        raise ValueError("输入文件看起来不是受支持的视频：{}".format(source.suffix))
    if not weights.is_file():
        raise FileNotFoundError("找不到权重：{}".format(weights))

    output = (
        Path(args.output).expanduser().resolve()
        if args.output
        else source.with_name("{}_mosaic.mp4".format(source.stem))
    )
    if output == source:
        raise ValueError("输出文件不能与输入视频相同。")
    if output.exists() and not args.overwrite:
        raise FileExistsError("输出已存在；如需覆盖，请添加 --overwrite：{}".format(output))
    output.parent.mkdir(parents=True, exist_ok=True)

    device = select_device(args.device)
    use_fp16 = device.type != "cpu" and not args.full_precision
    data_path = Path(args.data).expanduser().resolve()
    model = DetectMultiBackend(
        str(weights),
        device=device,
        dnn=False,
        data=str(data_path),
        fp16=use_fp16,
    )
    stride = int(model.stride)
    image_size = tuple(check_img_size((args.imgsz, args.imgsz), s=stride))
    names = model_names_to_dict(model.names)
    face_classes = choose_face_classes(names, args.classes)

    selected_text = ", ".join("{}:{}".format(index, names[index]) for index in face_classes)
    print("模型类别：{}".format(", ".join("{}:{}".format(i, n) for i, n in names.items())))
    print("打码类别：{}".format(selected_text))
    print("计算设备：{}，精度：{}".format(device, "FP16" if use_fp16 else "FP32"))

    capture = cv2.VideoCapture(str(source))
    if not capture.isOpened():
        raise RuntimeError("OpenCV 无法打开输入视频：{}".format(source))

    fps = float(capture.get(cv2.CAP_PROP_FPS))
    width = int(capture.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(capture.get(cv2.CAP_PROP_FRAME_HEIGHT))
    total_frames = int(capture.get(cv2.CAP_PROP_FRAME_COUNT))
    if not np.isfinite(fps) or fps <= 0:
        fps = 25.0
    if width <= 0 or height <= 0:
        capture.release()
        raise RuntimeError("无法读取视频尺寸。")

    silent_output = output.with_name(".{}.silent.mp4".format(output.stem))
    if silent_output.exists():
        silent_output.unlink()
    writer = cv2.VideoWriter(
        str(silent_output),
        cv2.VideoWriter_fourcc(*"mp4v"),
        fps,
        (width, height),
    )
    if not writer.isOpened():
        capture.release()
        raise RuntimeError("无法创建输出视频：{}".format(silent_output))

    model.warmup(imgsz=(1, 3, *image_size))
    frame_index = 0
    face_count = 0
    started = time.time()
    report_every = max(1, total_frames // 100) if total_frames > 0 else 30

    try:
        while True:
            ok, frame = capture.read()
            if not ok:
                break

            tensor = prepare_tensor(frame, image_size, stride, model.device, model.fp16)
            prediction = unwrap_prediction(model(tensor, augment=False, visualize=False))
            detections = non_max_suppression(
                prediction,
                args.conf,
                args.iou,
                face_classes,
                False,
                max_det=args.max_det,
            )[0]

            if len(detections):
                detections[:, :4] = scale_boxes(
                    tensor.shape[2:],
                    detections[:, :4],
                    frame.shape,
                ).round()
                face_count += pixelate_regions(
                    frame,
                    detections[:, :4].cpu().tolist(),
                    args.block_size,
                    args.expand,
                    args.min_face,
                )

            writer.write(frame)
            frame_index += 1
            if frame_index % report_every == 0:
                if total_frames > 0:
                    progress = min(100.0, frame_index * 100.0 / total_frames)
                    print(
                        "\r进度：{:6.2f}%  帧：{}/{}  已打码人脸：{}".format(
                            progress, frame_index, total_frames, face_count
                        ),
                        end="",
                        flush=True,
                    )
                else:
                    print(
                        "\r已处理帧：{}  已打码人脸：{}".format(frame_index, face_count),
                        end="",
                        flush=True,
                    )
    finally:
        capture.release()
        writer.release()

    print()
    if frame_index == 0 or not silent_output.exists() or silent_output.stat().st_size == 0:
        if silent_output.exists():
            silent_output.unlink()
        raise RuntimeError("没有从输入视频中读取到有效帧。")

    audio_kept = False
    ffmpeg = None if args.no_audio else find_ffmpeg()
    if ffmpeg:
        audio_kept = merge_original_audio(ffmpeg, silent_output, source, output)
    else:
        if output.exists():
            output.unlink()
        os.replace(str(silent_output), str(output))

    elapsed = time.time() - started
    print("完成：{}".format(output))
    print("处理 {} 帧，打码 {} 个人脸，用时 {:.1f} 秒。".format(frame_index, face_count, elapsed))
    if args.no_audio:
        print("音频：已按 --no-audio 选项移除。")
    elif audio_kept:
        print("音频：已从原视频保留。")
    else:
        print("音频：未保留（安装 imageio-ffmpeg 后可自动保留）。")
    return output


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="使用 YOLOv9 人脸检测权重，为视频中的人脸添加像素马赛克。"
    )
    parser.add_argument("--source", required=True, help="输入视频路径")
    parser.add_argument("--output", help="输出 MP4 路径，默认在输入旁生成 *_mosaic.mp4")
    parser.add_argument("--weights", default=str(default_weights()), help="YOLOv9 人脸权重路径")
    parser.add_argument("--data", default=str(YOLOV9_ROOT / "data" / "coco.yaml"), help="数据集 YAML 路径")
    parser.add_argument("--device", default="", help="计算设备，例如 0 或 cpu；默认自动选择")
    parser.add_argument("--imgsz", type=int, default=640, help="YOLO 推理尺寸")
    parser.add_argument("--conf", type=float, default=0.25, help="置信度阈值")
    parser.add_argument("--iou", type=float, default=0.45, help="NMS IoU 阈值")
    parser.add_argument("--max-det", type=int, default=1000, help="每帧最大检测数")
    parser.add_argument("--classes", nargs="+", type=int, help="要打码的类别编号")
    parser.add_argument("--block-size", type=int, default=12, help="马赛克像素块大小")
    parser.add_argument("--expand", type=float, default=0.12, help="检测框向外扩展比例")
    parser.add_argument("--min-face", type=int, default=4, help="忽略宽或高小于该值的框")
    parser.add_argument("--full-precision", action="store_true", help="显卡也使用 FP32")
    parser.add_argument("--no-audio", action="store_true", help="不尝试保留原视频音频")
    parser.add_argument("--overwrite", action="store_true", help="允许覆盖已存在的输出文件")
    args = parser.parse_args()

    if args.imgsz <= 0:
        parser.error("--imgsz 必须大于 0")
    if not 0.0 <= args.conf <= 1.0:
        parser.error("--conf 必须在 0 到 1 之间")
    if not 0.0 <= args.iou <= 1.0:
        parser.error("--iou 必须在 0 到 1 之间")
    if args.block_size < 2:
        parser.error("--block-size 必须至少为 2")
    if args.expand < 0.0:
        parser.error("--expand 不能为负数")
    if args.min_face < 1:
        parser.error("--min-face 必须至少为 1")
    return args


def main() -> int:
    args = parse_args()
    try:
        process_video(args)
        return 0
    except torch.cuda.OutOfMemoryError:
        print(
            "错误：显存不足。请改用 --imgsz 512，或加 --device cpu。",
            file=sys.stderr,
        )
        return 2
    except (FileNotFoundError, FileExistsError, RuntimeError, TypeError, ValueError) as error:
        print("错误：{}".format(error), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
