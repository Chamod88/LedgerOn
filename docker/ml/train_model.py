import os
import pandas as pd
import numpy as np
import xgboost as xgb
from sklearn.model_selection import train_test_split
from sklearn.metrics import (
    classification_report,
    confusion_matrix,
    roc_auc_score,
    precision_score,
    recall_score,
    f1_score
)
import joblib

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

TARGET_COLUMN = "is_fraud"

def train_xgboost_pipeline(dataset_path):
    print(f"Loading dataset from: {dataset_path}")
    df = pd.read_csv(dataset_path)

    X = df[FEATURE_COLUMNS]
    y = df[TARGET_COLUMN]

    print(f"Dataset shape: {X.shape}, Target distribution: {np.bincount(y)}")

    # 80/20 Train-Test Stratified Split
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.20, random_state=42, stratify=y
    )

    # Compute scale_pos_weight for handling 95/5 class imbalance
    num_neg = (y_train == 0).sum()
    num_pos = (y_train == 1).sum()
    scale_pos_weight = num_neg / num_pos
    print(f"Train Set: {len(X_train)} rows | Test Set: {len(X_test)} rows")
    print(f"Scale Pos Weight (Class Imbalance Ratio): {scale_pos_weight:.2f}")

    # Initialize XGBoost Classifier
    model = xgb.XGBClassifier(
        n_estimators=150,
        max_depth=5,
        learning_rate=0.05,
        subsample=0.8,
        colsample_bytree=0.8,
        scale_pos_weight=scale_pos_weight,
        random_state=42,
        eval_metric='logloss'
    )

    print("\nTraining XGBoost model on 12-dimension feature vector...")
    model.fit(X_train, y_train)

    # Evaluate on Test Set
    y_pred = model.predict(X_test)
    y_proba = model.predict_proba(X_test)[:, 1]

    roc_auc = roc_auc_score(y_test, y_proba)
    precision = precision_score(y_test, y_pred)
    recall = recall_score(y_test, y_pred)
    f1 = f1_score(y_test, y_pred)
    cm = confusion_matrix(y_test, y_pred)

    print("\n=================== MODEL EVALUATION METRICS ===================")
    print(f"ROC-AUC Score : {roc_auc:.4f}")
    print(f"Precision     : {precision:.4f}")
    print(f"Recall        : {recall:.4f}")
    print(f"F1-Score      : {f1:.4f}")
    print("\nConfusion Matrix:")
    print(f"  TN: {cm[0][0]}  |  FP: {cm[0][1]}")
    print(f"  FN: {cm[1][0]}  |  TP: {cm[1][1]}")

    print("\nFull Classification Report:")
    print(classification_report(y_test, y_pred, target_names=["Legitimate (0)", "Fraud (1)"]))

    # Feature Importance Analysis
    importances = model.feature_importances_
    feature_importance_df = pd.DataFrame({
        'feature': FEATURE_COLUMNS,
        'importance': importances
    }).sort_values('importance', ascending=False)

    print("\n=================== FEATURE IMPORTANCE RANKING ===================")
    for idx, row in feature_importance_df.iterrows():
        print(f"  {row['feature']:<30}: {row['importance']:.4f}")

    # Save Model Artifacts
    ml_dir = os.path.dirname(os.path.abspath(dataset_path))
    json_model_path = os.path.join(ml_dir, "xgboost_fraud_model.json")
    joblib_model_path = os.path.join(ml_dir, "xgboost_fraud_model.joblib")

    model.save_model(json_model_path)
    joblib.dump(model, joblib_model_path)

    print(f"\n[SUCCESS] XGBoost model saved to:")
    print(f"  - Native JSON: {json_model_path}")
    print(f"  - Joblib PKL:  {joblib_model_path}")

    return model, feature_importance_df

if __name__ == "__main__":
    ml_dir = os.path.dirname(os.path.abspath(__file__))
    dataset_path = os.path.join(ml_dir, "synthetic_fraud_dataset.csv")

    if not os.path.exists(dataset_path):
        raise FileNotFoundError(f"Dataset not found at {dataset_path}. Run generate_synthetic_data.py first.")

    train_xgboost_pipeline(dataset_path)
