# YOLOv9 视频人脸马赛克后端

这个目录提供视频人脸打码的核心处理程序。手机录制的视频上传到服务器后，服务器可以调用 `face_mosaic.py`，生成保留原音频的 MP4 文件，再供手机下载和播放。

## 仓库内容

- `face_mosaic.py`：视频读取、YOLOv9 推理、人脸像素马赛克和音频回填。
- `requirements.txt`：Python 3.8 兼容的运行依赖，PyTorch 需要按服务器 CUDA 版本单独安装。
- `run_mosaic.bat`：Windows 演示用快捷入口。
- `THIRD_PARTY_NOTICES.md`：YOLOv9 第三方依赖和许可证说明。

以下内容不会提交到 Git：

- `best.pt`、`yolov9-c.pt` 等模型文件；
- `.venv` 虚拟环境；
- 上传的原视频和处理后的视频；
- 部署时克隆的 YOLOv9 官方源码。

## 准备 YOLOv9

在本目录执行：

```bash
git clone https://github.com/WongKinYiu/yolov9.git yolov9
```

程序默认查找当前目录下的 `yolov9`。服务器也可以通过环境变量指定其他位置：

```bash
export YOLOV9_ROOT=/opt/yolov9
```

Windows PowerShell：

```powershell
$env:YOLOV9_ROOT = "D:\services\yolov9"
```

## 安装依赖

Python 3.8、CUDA 12.1 的示例：

```bash
python -m venv .venv
```

Windows：

```powershell
.\.venv\Scripts\python.exe -m pip install `
  torch==2.4.1 torchvision==0.19.1 `
  --index-url https://download.pytorch.org/whl/cu121

.\.venv\Scripts\python.exe -m pip install -r requirements.txt
```

Linux：

```bash
./.venv/bin/python -m pip install \
  torch==2.4.1 torchvision==0.19.1 \
  --index-url https://download.pytorch.org/whl/cu121

./.venv/bin/python -m pip install -r requirements.txt
```

生产服务器应根据实际驱动和 CUDA 版本选择 PyTorch 构建。

## 配置人脸权重

`best.pt` 是部署文件，不提交到 GitHub。把它放在服务器模型目录，并设置：

```bash
export FACE_MODEL_PATH=/models/best.pt
```

Windows PowerShell：

```powershell
$env:FACE_MODEL_PATH = "D:\models\best.pt"
```

该权重必须包含 `face` 类别。标准 COCO `yolov9-c.pt` 只有 `person` 类别，不能代替人脸模型。

## 运行

```bash
python face_mosaic.py \
  --source /data/uploads/input.mp4 \
  --output /data/outputs/input_mosaic.mp4 \
  --imgsz 640 \
  --conf 0.25
```

显存不足时可以使用：

```bash
python face_mosaic.py --source input.mp4 --imgsz 512
```

或者切换 CPU：

```bash
python face_mosaic.py --source input.mp4 --device cpu
```

程序会在检测框区域添加像素马赛克，并通过 `imageio-ffmpeg` 尝试保留原视频音频。

## 后续服务器接口

正式部署时建议由任务队列调用本脚本：

```text
手机上传视频
→ 创建处理任务
→ 调用 face_mosaic.py
→ 保存处理结果
→ 返回下载地址
→ 手机下载并写入 MediaStore
```

不要让 HTTP 请求一直等待整个视频处理过程；接口应返回任务 ID，由手机查询处理状态。
