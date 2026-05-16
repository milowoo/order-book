package com.orderbook.core.strategy.ml;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XGBoostModel and XGBoostTrainer integration tests.
 * <p>
 * Requires XGBoost native library to be available.
 * If the native library is not installed on the platform, the tests
 * in this class are skipped (not failed).
 * </p>
 */
class XGBoostModelTest {

    private static boolean nativeLibAvailable = false;

    @BeforeAll
    static void checkNativeLib() {
        try {
            // Train a trivial model to verify native lib loads
            XGBoostTrainer trainer = new XGBoostTrainer();
            List<CartTrainer.TrainingExample> data = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                data.add(new CartTrainer.TrainingExample(
                        new double[]{i * 1.0, i * 2.0}, (double) i));
            }
            MLConfig config = MLConfig.builder()
                    .modelType("xgboost")
                    .xgbNumRound(2)
                    .xgbMaxDepth(2)
                    .xgbEta(0.3)
                    .build();
            XGBoostModel model = trainer.train("test", data, config);
            nativeLibAvailable = (model != null);
        } catch (Exception e) {
            System.err.println("XGBoost native library not available: " + e.getMessage());
            nativeLibAvailable = false;
        } catch (UnsatisfiedLinkError e) {
            System.err.println("XGBoost native library not available: " + e.getMessage());
            nativeLibAvailable = false;
        }
    }

    @Test
    void trainAndPredict() {
        if (!nativeLibAvailable) return;

        List<CartTrainer.TrainingExample> data = new ArrayList<>();
        // y = 2*x0 + 3*x1 (approximately)
        for (int i = 0; i < 50; i++) {
            double x0 = Math.random() * 10;
            double x1 = Math.random() * 10;
            double label = 2 * x0 + 3 * x1 + (Math.random() - 0.5) * 0.5;
            data.add(new CartTrainer.TrainingExample(new double[]{x0, x1}, label));
        }

        MLConfig config = MLConfig.builder()
                .modelType("xgboost")
                .xgbNumRound(10)
                .xgbMaxDepth(4)
                .xgbEta(0.3)
                .build();

        XGBoostModel model = new XGBoostTrainer().train("test_model", data, config);
        assertNotNull(model, "Model should train successfully");
        assertEquals("test_model", model.getName());
        assertEquals(2, model.featureCount());

        // Predict on known input
        double pred = model.predict(new double[]{5.0, 5.0});
        assertTrue(pred > 0, "Prediction should be positive for positive input");
        assertTrue(Double.isFinite(pred), "Prediction should be finite");
    }

    @Test
    void serializationRoundTrip() {
        if (!nativeLibAvailable) return;

        List<CartTrainer.TrainingExample> data = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            double x0 = Math.random() * 5;
            double x1 = Math.random() * 5;
            double label = x0 + x1 + 1;
            data.add(new CartTrainer.TrainingExample(new double[]{x0, x1}, label));
        }

        MLConfig config = MLConfig.builder()
                .modelType("xgboost")
                .xgbNumRound(5)
                .xgbMaxDepth(3)
                .xgbEta(0.3)
                .build();

        XGBoostModel original = new XGBoostTrainer().train("serialize_test", data, config);
        assertNotNull(original);

        // Serialize and deserialize
        String json = original.toJsonString();
        assertNotNull(json);
        assertTrue(json.startsWith("xgboost_base64:"));

        XGBoostModel restored = XGBoostModel.fromJson(json, "serialize_test", 2);
        assertNotNull(restored);

        // Compare predictions
        double[] testInput = new double[]{2.5, 3.5};
        double originalPred = original.predict(testInput);
        double restoredPred = restored.predict(testInput);
        assertEquals(originalPred, restoredPred, 1e-4,
                "Original and restored predictions should match");
    }

    @Test
    void predictWithWrongFeatureCount() {
        if (!nativeLibAvailable) return;

        XGBoostModel model = new XGBoostTrainer().train("feat_test",
                sampleData(), simpleConfig());
        assertNotNull(model);

        // Prediction with wrong number of features should not throw
        double result = model.predict(new double[]{1.0});
        assertTrue(Double.isFinite(result));
    }

    @Test
    void fromJsonWithInvalidString() {
        assertNull(XGBoostModel.fromJson("invalid_string"));
        assertNull(XGBoostModel.fromJson(null));
        assertNull(XGBoostModel.fromJson("xgboost_base64:invalidbase64!!"));
    }

    // ---- Helpers ----

    private static List<CartTrainer.TrainingExample> sampleData() {
        List<CartTrainer.TrainingExample> data = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            data.add(new CartTrainer.TrainingExample(
                    new double[]{i * 0.5, i * 1.5}, (double) i));
        }
        return data;
    }

    private static MLConfig simpleConfig() {
        return MLConfig.builder()
                .modelType("xgboost")
                .xgbNumRound(3)
                .xgbMaxDepth(2)
                .xgbEta(0.3)
                .build();
    }
}
