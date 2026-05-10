package com.orderbook.core.strategy.ml;

import java.util.*;

/**
 * CART（分类与回归树）训练算法。
 * 通过递归地分割数据以最小化均方误差（MSE），
 * 来构建一棵二叉回归树。
 */
public class CartTrainer {

    /**
     * 训练一棵回归决策树。
     *
     * @param data           training examples
     * @param maxDepth       maximum tree depth
     * @param minSamplesLeaf minimum samples per leaf
     * @return trained DecisionTree
     */
    public DecisionTree train(List<TrainingExample> data, int maxDepth, int minSamplesLeaf) {
        if (data == null || data.isEmpty()) {
            return new DecisionTree(DecisionTree.Node.leaf(0.0));
        }
        int[] allFeatures = new int[data.get(0).features.length];
        for (int i = 0; i < allFeatures.length; i++) allFeatures[i] = i;

        DecisionTree.Node root = buildTree(data, allFeatures, 0, maxDepth, minSamplesLeaf);
        return new DecisionTree(root);
    }

    /**
     * 使用特征子采样进行训练（由随机森林使用）。
     */
    public DecisionTree train(List<TrainingExample> data, int[] featureSubset,
                               int maxDepth, int minSamplesLeaf) {
        if (data == null || data.isEmpty()) {
            return new DecisionTree(DecisionTree.Node.leaf(0.0));
        }
        DecisionTree.Node root = buildTree(data, featureSubset, 0, maxDepth, minSamplesLeaf);
        return new DecisionTree(root);
    }

    private DecisionTree.Node buildTree(List<TrainingExample> data, int[] featureIndices,
                            int depth, int maxDepth, int minSamplesLeaf) {
        // Compute mean label value
        double mean = computeMean(data);

        // Stop conditions: max depth reached, too few samples, or all labels same
        if (depth >= maxDepth || data.size() <= minSamplesLeaf || isHomogeneous(data)) {
            return DecisionTree.Node.leaf(mean);
        }

        // Find best split
        SplitResult best = findBestSplit(data, featureIndices);
        if (best == null || best.mseReduction <= 1e-12) {
            return DecisionTree.Node.leaf(mean);
        }

        // Split data
        List<TrainingExample> leftData = new ArrayList<>();
        List<TrainingExample> rightData = new ArrayList<>();
        for (TrainingExample ex : data) {
            if (ex.features[best.featureIndex] <= best.threshold) {
                leftData.add(ex);
            } else {
                rightData.add(ex);
            }
        }

        // Guard against empty splits
        if (leftData.isEmpty() || rightData.isEmpty()) {
            return DecisionTree.Node.leaf(mean);
        }

        DecisionTree.Node leftChild = buildTree(leftData, featureIndices, depth + 1, maxDepth, minSamplesLeaf);
        DecisionTree.Node rightChild = buildTree(rightData, featureIndices, depth + 1, maxDepth, minSamplesLeaf);

        return DecisionTree.Node.split(best.featureIndex, best.threshold, leftChild, rightChild);
    }

    /**
     * 通过遍历特征和阈值来寻找最佳分裂点。
     */
    private SplitResult findBestSplit(List<TrainingExample> data, int[] featureIndices) {
        double totalMse = computeMse(data);
        SplitResult best = null;

        for (int fi : featureIndices) {
            // Find candidate thresholds for this feature
            Set<Double> uniqueValues = new TreeSet<>();
            for (TrainingExample ex : data) {
                uniqueValues.add(ex.features[fi]);
            }
            if (uniqueValues.size() <= 1) continue;

            // Consider midpoints between consecutive unique values
            List<Double> sorted = new ArrayList<>(uniqueValues);
            for (int i = 0; i < sorted.size() - 1; i++) {
                double threshold = (sorted.get(i) + sorted.get(i + 1)) / 2.0;

                // Compute weighted MSE after split
                double sumLeft = 0, sumRight = 0;
                int countLeft = 0, countRight = 0;

                for (TrainingExample ex : data) {
                    if (ex.features[fi] <= threshold) {
                        sumLeft += ex.label;
                        countLeft++;
                    } else {
                        sumRight += ex.label;
                        countRight++;
                    }
                }

                if (countLeft < 1 || countRight < 1) continue;

                // Compute MSE for each split
                double meanLeft = sumLeft / countLeft;
                double meanRight = sumRight / countRight;

                double mseLeft = 0, mseRight = 0;
                for (TrainingExample ex : data) {
                    if (ex.features[fi] <= threshold) {
                        double d = ex.label - meanLeft;
                        mseLeft += d * d;
                    } else {
                        double d = ex.label - meanRight;
                        mseRight += d * d;
                    }
                }

                double weightedMse = (mseLeft + mseRight) / data.size();
                double reduction = totalMse - weightedMse;

                if (best == null || reduction > best.mseReduction) {
                    best = new SplitResult(fi, threshold, reduction);
                }
            }
        }

        return best;
    }

    private double computeMean(List<TrainingExample> data) {
        double sum = 0;
        for (TrainingExample ex : data) sum += ex.label;
        return sum / data.size();
    }

    private double computeMse(List<TrainingExample> data) {
        if (data.isEmpty()) return 0;
        double mean = computeMean(data);
        double mse = 0;
        for (TrainingExample ex : data) {
            double d = ex.label - mean;
            mse += d * d;
        }
        return mse / data.size();
    }

    private boolean isHomogeneous(List<TrainingExample> data) {
        if (data.size() <= 1) return true;
        double first = data.get(0).label;
        for (int i = 1; i < data.size(); i++) {
            if (Math.abs(data.get(i).label - first) > 1e-12) return false;
        }
        return true;
    }

    // ---- Data classes ----

    public static class TrainingExample {
        public final double[] features;
        public final double label;

        public TrainingExample(double[] features, double label) {
            this.features = features;
            this.label = label;
        }
    }

    static class SplitResult {
        final int featureIndex;
        final double threshold;
        final double mseReduction;

        SplitResult(int featureIndex, double threshold, double mseReduction) {
            this.featureIndex = featureIndex;
            this.threshold = threshold;
            this.mseReduction = mseReduction;
        }
    }

}
