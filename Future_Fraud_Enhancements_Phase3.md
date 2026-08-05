# Future Fraud Engine Enhancements (Phase 3+)

Based on the review of the "SL-BFIDS / Algorq" federated surveillance framework research, we have identified several high-value additions for the fraud engine. 

To maintain momentum, the project will first deploy the Phase 2 baseline (XGBoost + ONNX). Once live, we will iteratively introduce the following improvements:

## 1. Unsupervised Machine Learning (Isolation Forest)
**Concept:** Anomaly detection that does not require labeled training data.
**Value:** While XGBoost learns from the synthetic `Faker` data we generate, an Isolation Forest model will learn from actual production data to detect structural outliers (e.g., completely new fraud patterns) from Day 1 without needing historical fraud labels.

## 2. Statistical Baselines (CUSUM Control Charts & Benford's Law)
**Concept:** Tracking mathematical drift rather than hard limits.
**Value:** Our current Rule Engine blocks static thresholds (e.g., > $50k or > 5 tx/hr). CUSUM (Cumulative Sum) tracks small, sustained deviations over time (e.g., a fraudster doing many $900 transactions to stay under limits). Benford's Law detects manual, unnatural manipulation of transaction amounts.

## 3. Explainable AI (SHAP Values)
**Concept:** Translating opaque model scores into plain-language reasons.
**Value:** Instead of the API just returning a risk score of `0.85`, calculating SHAP values allows the engine to output specific reasons: `"Score: 0.85. Primary driver: tx_velocity_1h is unusually high."` This is critical for analysts reviewing cases in the queue.

## 4. Graph Neural Networks (GNNs) and Entity Resolution
**Concept:** Network analysis to detect collusion rings.
**Value:** This directly addresses "Employee-external collusion," where a small group of internal accounts routes funds to a concentrated set of external counterparties. GNNs map these relationships to flag abnormal network structures.

## 5. Offline Analytics (ClickHouse)
**Concept:** Column-oriented decision logging.
**Value:** Storing every scored transaction and its feature vector for offline analysis and model retraining, replacing standard application logs with a queryable datastore.
