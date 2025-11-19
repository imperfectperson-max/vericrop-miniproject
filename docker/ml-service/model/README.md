
VERICROP QUALITY DETECTION MODEL v1.0
=====================================

Model Overview:
---------------
- Architecture: ResNet18
- Input: 224x224 RGB images
- Output: 3 quality classes
- Validation Accuracy: 99.1%

Classes:
--------
0: fresh
1: low_quality
2: rotten

Files Included:
---------------
1. vericrop_quality_model.onnx - ONNX format for cross-platform deployment
2. vericrop_quality_model.pth - Full PyTorch model with metadata
3. vericrop_quality_model_weights.pth - Model weights only
4. vericrop_quality_model_scripted.pt - TorchScript for production
5. quality_label_map.json - Class label mappings
6. vericrop_model_metadata.json - Complete model specifications
7. model_performance.png - Training history and performance

Usage Examples:
---------------
See the inference examples in the metadata file.

Requirements:
-------------
- Python 3.8+
- PyTorch 1.9+
- torchvision
- onnxruntime (for ONNX models)

Deployment:
-----------
This model is ready for integration into the VeriCrop blockchain supply chain system.

Model trained on: 2025-11-17
