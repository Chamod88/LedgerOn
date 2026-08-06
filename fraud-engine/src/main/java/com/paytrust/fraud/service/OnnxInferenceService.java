package com.paytrust.fraud.service;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class OnnxInferenceService {

    private static final Logger log = LoggerFactory.getLogger(OnnxInferenceService.class);

    private OrtEnvironment env;
    private OrtSession session;
    private boolean isModelLoaded = false;

    @PostConstruct
    public synchronized void init() {
        try {
            log.info("Initializing ONNX Runtime Environment...");
            this.env = OrtEnvironment.getEnvironment();

            ClassPathResource resource = new ClassPathResource("fraud_model.onnx");
            if (!resource.exists()) {
                log.warn("ONNX model file 'fraud_model.onnx' not found in classpath resources. Model inference will be disabled.");
                return;
            }

            try (InputStream is = resource.getInputStream()) {
                byte[] modelBytes = is.readAllBytes();
                this.session = env.createSession(modelBytes, new OrtSession.SessionOptions());
                this.isModelLoaded = true;
                log.info("Successfully loaded ONNX XGBoost Fraud Model into ONNX Runtime Session!");
            }
        } catch (Exception e) {
            log.error("Failed to initialize ONNX Runtime session for fraud model", e);
            this.isModelLoaded = false;
        }
    }

    public boolean isModelLoaded() {
        return isModelLoaded;
    }

    /**
     * Run ONNX model inference on the 12-dimension feature vector.
     * @param featureVector float array of length 12
     * @return predicted fraud probability between 0.0 and 1.0
     */
    public float predictFraudProbability(float[] featureVector) {
        if (!isModelLoaded || session == null) {
            log.warn("ONNX model session not active. Returning default score 0.0 (fail open).");
            return 0.0f;
        }

        if (featureVector == null || featureVector.length != 12) {
            log.error("Invalid feature vector length. Expected 12 features, got: {}",
                    featureVector != null ? featureVector.length : "null");
            return 0.0f;
        }

        try {
            // Input shape: [1, 12]
            long[] shape = new long[]{1, 12};
            OnnxTensor inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(featureVector), shape);

            try (OrtSession.Result result = session.run(Collections.singletonMap("float_input", inputTensor))) {
                // XGBoost ONNX model returns outputs: [label_predictions, probabilities]
                // Output 1 is usually Map/Tensor of class probabilities
                if (result.size() >= 2) {
                    Object probObj = result.get(1).getValue();
                    if (probObj instanceof List<?> list && !list.isEmpty()) {
                        Object item = list.get(0);
                        if (item instanceof Map<?, ?> map) {
                            Object rawProb = map.get(1L); // Probability for Fraud class (1)
                            if (rawProb == null) {
                                rawProb = map.get(1);
                            }
                            if (rawProb instanceof Number num) {
                                return num.floatValue();
                            }
                        }
                    } else if (probObj instanceof float[][] floatMatrix) {
                        return floatMatrix[0][1];
                    }
                }

                // Fallback to first output tensor if single output
                Object labelObj = result.get(0).getValue();
                if (labelObj instanceof long[] labels && labels.length > 0) {
                    return labels[0] == 1L ? 1.0f : 0.0f;
                } else if (labelObj instanceof int[] labels && labels.length > 0) {
                    return labels[0] == 1 ? 1.0f : 0.0f;
                }
            }
        } catch (Exception e) {
            log.error("Error during ONNX model inference", e);
        }

        return 0.0f;
    }

    @PreDestroy
    public synchronized void close() {
        try {
            if (session != null) {
                session.close();
            }
            if (env != null) {
                env.close();
            }
            log.info("ONNX Runtime environment closed cleanly.");
        } catch (Exception e) {
            log.error("Error closing ONNX Runtime session", e);
        }
    }
}
