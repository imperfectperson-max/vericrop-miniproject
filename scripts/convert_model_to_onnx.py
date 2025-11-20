#!/usr/bin/env python3
"""
Convert PyTorch model to ONNX format for production deployment.
This script converts the existing vericrop_quality_model_scripted.pt to ONNX format.
"""

import torch
import os
import sys
import json

def convert_to_onnx():
    """Convert PyTorch scripted model to ONNX format"""
    
    model_dir = os.path.join(os.path.dirname(__file__), "..", "docker", "ml-service", "model")
    pytorch_model_path = os.path.join(model_dir, "vericrop_quality_model_scripted.pt")
    onnx_model_path = os.path.join(model_dir, "vericrop_quality_model.onnx")
    
    print(f"Converting PyTorch model to ONNX...")
    print(f"  Input:  {pytorch_model_path}")
    print(f"  Output: {onnx_model_path}")
    
    if not os.path.exists(pytorch_model_path):
        print(f"❌ Error: PyTorch model not found at {pytorch_model_path}")
        sys.exit(1)
    
    try:
        # Load the PyTorch scripted model (map to CPU if trained on CUDA)
        print("Loading PyTorch model...")
        model = torch.jit.load(pytorch_model_path, map_location=torch.device('cpu'))
        model.eval()
        
        # Create dummy input (batch_size=1, channels=3, height=224, width=224)
        dummy_input = torch.randn(1, 3, 224, 224)
        
        # Export to ONNX using the older legacy API
        print("Exporting to ONNX format...")
        import warnings
        with warnings.catch_warnings():
            warnings.filterwarnings("ignore", category=UserWarning)
            # Use dynamo=False to use the legacy exporter which works with ScriptModule
            torch.onnx.export(
                model,
                dummy_input,
                onnx_model_path,
                export_params=True,
                opset_version=11,
                do_constant_folding=True,
                input_names=['input'],
                output_names=['output'],
                dynamo=False  # Use legacy exporter
            )
        
        print(f"✅ Successfully converted model to ONNX format")
        print(f"✅ ONNX model saved to: {onnx_model_path}")
        
        # Verify the ONNX model
        try:
            import onnx
            import onnxruntime as ort
            
            print("\nVerifying ONNX model...")
            onnx_model = onnx.load(onnx_model_path)
            onnx.checker.check_model(onnx_model)
            print("✅ ONNX model structure is valid")
            
            # Test inference
            print("Testing ONNX inference...")
            session = ort.InferenceSession(onnx_model_path)
            test_input = dummy_input.numpy()
            outputs = session.run(None, {'input': test_input})
            print(f"✅ ONNX inference test successful - output shape: {outputs[0].shape}")
            
        except ImportError:
            print("⚠️  onnx/onnxruntime not installed - skipping verification")
            print("   Install with: pip install onnx onnxruntime")
        
        return True
        
    except Exception as e:
        print(f"❌ Error during conversion: {e}")
        import traceback
        traceback.print_exc()
        return False

if __name__ == "__main__":
    success = convert_to_onnx()
    sys.exit(0 if success else 1)
