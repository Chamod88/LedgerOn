# Daily Progress Log

This document tracks the daily progress, decisions, and milestones for the LedgerOn / Payverse Fraud Detection Engine project.

---

## Phase 1: Foundation (Completed prior to Aug 2, 2026)
* **Milestones:**
  * Scaffolded the `fraud-engine` Spring Boot service.
  * Built the **Feature Store** using Redis (`ZSET` to track transactions and calculate `tx_velocity_1h`, `tx_velocity_24h`, `user_avg_amount`).
  * Built the **Rule Engine** with deterministic rules (e.g., $50k critical limit, $10k review limit, 5 tx/hr velocity limit, blocked country check).
  * Implemented a real-time synchronous check at the API Gateway with a strict 100ms connection timeout (fails open to avoid customer friction).
  * Handled duplicate attempts by mapping fraud-declined idempotency keys to `declined_fraud` in Redis, blocking subsequent retries with a 403 Forbidden.
  * Verified logic via Unit Tests and Ledger Service Regression Tests.

---

## August 2, 2026
* **Discussions:** 
  * Initiated planning for Phase 2: Python ONNX ML Pipeline.
  * Confirmed that the model will be based on Gradient Boosted Trees (XGBoost).
  * Discussed the synthetic data generation approach using the Python `Faker` library to bootstrap our model without real customer data.
  * Defined the theoretical 12-dimension transaction feature vector (including elements like `amount`, `tx_velocity_1h`, `device_fingerprint_age_days`, `time_of_day_hour`, etc.).

---

## August 3, 2026
* **Discussions:** 
  * Debated strategy: Deploy Phase 1 immediately vs. wait to finish Phase 2.
* **Decisions:** 
  * Decided to **complete Phase 2 before deploying**. Phase 1 alone is just a rule engine and easily bypassed by smart attackers. Integrating the XGBoost ML pipeline (Phase 2) is required to consider the system a true "Fraud Detection Engine".

---

## August 4, 2026
* **Discussions:** 
  * Reviewed colleague's research paper on the "SL-BFIDS / Algorq" federated surveillance system.
  * Mapped concepts from the research paper directly to our Payverse stack.
* **Milestones:** 
  * Created `Future_Fraud_Enhancements_Phase3.md` to catalog advanced features for the next iteration (Isolation Forest for unsupervised ML, CUSUM/Benford's Law for statistical drift, SHAP for explainable AI, and GNNs for collusion detection).
  * Updated `01_Project Status.md` to align focus.
* **Decisions:** 
  * Stick to the original plan: Finish Phase 2 (XGBoost + ONNX) tomorrow, deploy the high-quality baseline, and then add Phase 3 enhancements iteratively.

---

## August 5, 2026
* **Milestones:**
  * Created `fraud-engine/ml/generate_synthetic_data.py` using `Faker` and `numpy` to generate 10,000 synthetic transactions across 12 feature dimensions.
  * Created `fraud-engine/ml/train_model.py` and trained an XGBoost Classifier on the 12-dimension feature vector (`ROC-AUC: 1.0000`, `F1: 1.0000`).
  * Created `fraud-engine/ml/export_onnx.py` to convert the XGBoost model to `fraud_model.onnx` and verified inference with ONNX Runtime.
  * Added `com.microsoft.onnxruntime:onnxruntime:1.17.1` dependency to `fraud-engine/pom.xml`.
  * Created `OnnxInferenceService.java` to load `fraud_model.onnx` into ONNX Runtime memory on `@PostConstruct`.
  * Updated `FraudController.java` to assemble the 12-dimension feature vector in real time combining Redis `FeatureStoreService` metrics (`tx_velocity_1h`, `tx_velocity_24h`, `user_avg_amount_24h`) and transaction metadata.
  * Created `OnnxInferenceServiceTest.java` and ran `./mvnw test -pl fraud-engine` with 8/8 tests passing (`BUILD SUCCESS`).
* **Decisions:**
  * Completed Phase 2 baseline. Set next session's focus on Docker Compose stack verification and server deployment.
