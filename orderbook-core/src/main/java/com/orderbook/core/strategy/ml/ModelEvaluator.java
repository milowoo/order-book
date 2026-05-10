package com.orderbook.core.strategy.ml;

import java.util.List;

/**
 * 在预留的测试数据上评估训练好的机器学习模型的性能。
 */
public class ModelEvaluator {

    /**
     * 在测试数据上评估随机森林模型。
     */
    public EvaluationResult evaluate(RandomForestModel model, List<CartTrainer.TrainingExample> testData) {
        if (testData == null || testData.isEmpty()) {
            return new EvaluationResult(0, 0, 0);
        }

        double sumPred = 0, sumActual = 0;
        double sumAbsErr = 0, sumSqErr = 0;
        double sumSqTotal = 0;

        for (CartTrainer.TrainingExample ex : testData) {
            double prediction = model.predict(ex.features);
            double actual = ex.label;

            sumPred += prediction;
            sumActual += actual;
            sumAbsErr += Math.abs(prediction - actual);
            sumSqErr += (prediction - actual) * (prediction - actual);
        }

        int n = testData.size();
        double meanActual = sumActual / n;
        double mae = sumAbsErr / n;
        double rmse = Math.sqrt(sumSqErr / n);

        // R-squared = 1 - SS_res / SS_tot
        for (CartTrainer.TrainingExample ex : testData) {
            double d = ex.label - meanActual;
            sumSqTotal += d * d;
        }
        double rSquared = sumSqTotal > 1e-12 ? 1.0 - sumSqErr / sumSqTotal : 0.0;

        return new EvaluationResult(rSquared, mae, rmse);
    }

    public static class EvaluationResult {
        private final double rSquared;
        private final double mae;
        private final double rmse;

        public EvaluationResult(double rSquared, double mae, double rmse) {
            this.rSquared = rSquared;
            this.mae = mae;
            this.rmse = rmse;
        }

        public double getRSquared() { return rSquared; }
        public double getMae() { return mae; }
        public double getRmse() { return rmse; }
    }
}
