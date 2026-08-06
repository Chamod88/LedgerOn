import os
import shutil
import numpy as np
import joblib
import xgboost as xgb
from onnxmltools.convert.common.data_types import FloatTensorType
import onnxmltools
import onnxruntime as ort

FEATURE_COLUMNS = [
    "amount",
    "tx_velocity_1h",
    "tx_velocity_24h",
    "user_avg_amount_24h",
    "amount_to_avg_ratio",
    "time_of_day_hour",
    "is_weekend",
    "device_fingerprint_age_days",
    "is_international",
    "account_age_days",
    "failed_attempts_1h",
    "distance_from_home_km"
]

def export_model_to_onnx():
    ml_dir = os.path.dirname(os.path.abspath(__file__))
    model_path = os.path.join(ml_dir, "xgboost_fraud_model.joblib")
    onnx_path = os.path.join(ml_dir, "fraud_model.onnx")

    target_resources_dir = os.path.join(ml_dir, "..", "src", "main", "resources")
    target_onnx_path = os.path.join(target_resources_dir, "fraud_model.onnx")

    if not os.path.exists(model_path):
        raise FileNotFoundError(f"Trained model not found at {model_path}. Run train_model.py first.")

    print(f"Loading trained XGBoost model from: {model_path}")
    model = joblib.load(model_path)

    # Clear DataFrame feature names on booster so ONNX converter uses f0..f11 indices
    booster = model.get_booster()
    booster.feature_names = None

    initial_types = [('float_input', FloatTensorType([None, len(FEATURE_COLUMNS)]))]
    onnx_model = onnxmltools.convert_xgboost(model, initial_types=initial_types, target_opset=12)

    # Save ONNX model in ml folder
    onnxmltools.utils.save_model(onnx_model, onnx_path)
    print(f"[SUCCESS] Saved ONNX model to: {onnx_path}")

    # Copy to Spring Boot src/main/resources directory
    os.makedirs(target_resources_dir, exist_ok=True)
    shutil.copy(onnx_path, target_onnx_path)
    print(f"[SUCCESS] Copied ONNX model to Spring Boot resources: {target_onnx_path}")

    # Python ONNXRuntime Sanity Check
    print("\nRunning ONNXRuntime inference sanity check...")
    session = ort.InferenceSession(onnx_path)

    input_name = session.get_inputs()[0].name
    output_names = [output.name for output in session.get_outputs()]

    print(f"ONNX Model Input Name: '{input_name}', Output Names: {output_names}")

    # Sample test vector: [amount=1500, tx_vel_1h=6, tx_vel_24h=12, user_avg=100, ratio=15.0, hour=2, weekend=1, dev_age=1, intl=1, acc_age=15, failed=3, dist=850.0]
    sample_vector = np.array([[1500.0, 6.0, 12.0, 100.0, 15.0, 2.0, 1.0, 1.0, 1.0, 15.0, 3.0, 850.0]], dtype=np.float32)

    outputs = session.run(None, {input_name: sample_vector})
    print(f"Sample Input Vector: {sample_vector[0]}")
    print(f"Raw ONNX Outputs: {outputs}")

    # XGBoost ONNX model usually outputs [label_predictions, probabilities_map/tensor]
    label_pred = outputs[0][0]
    probabilities = outputs[1]
    
    fraud_prob = 0.0
    if isinstance(probabilities, list) and len(probabilities) > 0 and isinstance(probabilities[0], dict):
        fraud_prob = probabilities[0].get(1, 0.0)
    elif isinstance(probabilities, np.ndarray):
        fraud_prob = float(probabilities[0][1])

    print(f"Predicted Class Label: {label_pred}")
    print(f"Predicted Fraud Probability: {fraud_prob:.4f}")
    print("\n[VERIFIED] ONNX Model Export & Inference Sanity Check PASSED!")

if __name__ == "__main__":
    export_model_to_onnx()
