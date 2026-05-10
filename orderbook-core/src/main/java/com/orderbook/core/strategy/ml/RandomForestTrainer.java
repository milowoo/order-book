package com.orderbook.core.strategy.ml;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 基于自助聚合与分类回归树的随机森林训练。
 */
public class RandomForestTrainer {

    private final CartTrainer cartTrainer;
    private final Random random = new Random();

    public RandomForestTrainer(CartTrainer cartTrainer) {
        this.cartTrainer = cartTrainer;
    }

    /**
     * Train a RandomForestModel.
     *
     * @param name   model name
     * @param data   labeled training examples
     * @param config ML hyperparameters
     * @return trained RandomForestModel
     */
    public RandomForestModel train(String name, List<CartTrainer.TrainingExample> data, MLConfig config) {
        if (data == null || data.isEmpty()) {
            return new RandomForestModel(name, 10, new ArrayList<>());
        }

        int featureCount = data.get(0).features.length;
        int nTrees = Math.max(1, config.getRfNumTrees());
        int maxDepth = config.getRfMaxDepth();
        int minSamplesLeaf = config.getRfMinSamplesLeaf();
        double featureRatio = config.getRfFeatureRatio();
        int subsampleFeatures = Math.max(1, (int) (featureCount * featureRatio));

        List<DecisionTree> trees = new ArrayList<>(nTrees);

        for (int i = 0; i < nTrees; i++) {
            // Bootstrap sample
            List<CartTrainer.TrainingExample> bag = bootstrapSample(data);

            // Feature subsampling
            int[] featureSubset = subsampleFeatures(featureCount, subsampleFeatures);

            // Train tree
            DecisionTree tree = cartTrainer.train(bag, featureSubset, maxDepth, minSamplesLeaf);
            trees.add(tree);
        }

        return new RandomForestModel(name, featureCount, trees);
    }

    private List<CartTrainer.TrainingExample> bootstrapSample(List<CartTrainer.TrainingExample> data) {
        int n = data.size();
        List<CartTrainer.TrainingExample> sample = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            sample.add(data.get(random.nextInt(n)));
        }
        return sample;
    }

    private int[] subsampleFeatures(int totalFeatures, int subsampleSize) {
        // If subsample >= total, use all features
        if (subsampleSize >= totalFeatures) {
            int[] all = new int[totalFeatures];
            for (int i = 0; i < totalFeatures; i++) all[i] = i;
            return all;
        }

        // Random subset without replacement
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < totalFeatures; i++) indices.add(i);
        java.util.Collections.shuffle(indices, random);

        int[] result = new int[subsampleSize];
        for (int i = 0; i < subsampleSize; i++) {
            result[i] = indices.get(i);
        }
        return result;
    }
}
