package com.orderbook.core.strategy.ml;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 这段注释描述的是随机森林回归模型的核心结构。
 * 它强调了“集思广益”的思想：通过多棵树的集体决策来提高预测的准确性。
 随机森林回归模型。
 决策树的集合。预测结果是所有树输出的平均值。
 支持通过 JSON（使用 Jackson 库）进行加载和保存。
 */
@Slf4j
public class RandomForestModel implements MLModel {

    private final String name;
    private final int featureCount;
    private final List<DecisionTree> trees;

    public RandomForestModel(String name, int featureCount, List<DecisionTree> trees) {
        this.name = name;
        this.featureCount = featureCount;
        this.trees = trees;
    }

    @Override
    public double predict(double[] features) {
        if (trees.isEmpty()) return 0.0;

        double sum = 0.0;
        for (DecisionTree tree : trees) {
            sum += tree.predict(features);
        }
        return sum / trees.size();
    }

    @Override
    public int featureCount() {
        return featureCount;
    }

    @Override
    public String getName() {
        return name;
    }

    /** Number of trees in the forest. */
    public int treeCount() {
        return trees.size();
    }

    // ---- Serialization ----

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Save model to a JSON file. */
    public void save(File file) throws IOException {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("name", name);
        root.put("featureCount", featureCount);

        ArrayNode treesArray = root.putArray("trees");
        for (DecisionTree tree : trees) {
            treesArray.add(tree.toJson());
        }

        MAPPER.writerWithDefaultPrettyPrinter().writeValue(file, root);
        log.info("Saved RandomForestModel '{}' with {} trees to {}", name, trees.size(), file);
    }

    /** Load model from a JSON file. */
    public static RandomForestModel load(File file) throws IOException {
        ObjectNode root = (ObjectNode) MAPPER.readTree(file);
        String modelName = root.get("name").asText();
        int fCount = root.get("featureCount").asInt();

        ArrayNode treesArray = (ArrayNode) root.get("trees");
        List<DecisionTree> treeList = new ArrayList<>();
        for (int i = 0; i < treesArray.size(); i++) {
            treeList.add(DecisionTree.fromJson(treesArray.get(i).toString()));
        }

        log.info("Loaded RandomForestModel '{}' with {} trees from {}", modelName, treeList.size(), file);
        return new RandomForestModel(modelName, fCount, treeList);
    }

    /** Load model from a classpath resource. */
    public static RandomForestModel loadFromResource(String resourcePath) throws IOException {
        return load(new File(RandomForestModel.class.getResource(resourcePath).getFile()));
    }

    /** Build a model from JSON string. */
    public static RandomForestModel fromJson(String json) throws JsonProcessingException {
        ObjectNode root = (ObjectNode) MAPPER.readTree(json);
        String modelName = root.get("name").asText();
        int fCount = root.get("featureCount").asInt();

        ArrayNode treesArray = (ArrayNode) root.get("trees");
        List<DecisionTree> treeList = new ArrayList<>();
        for (int i = 0; i < treesArray.size(); i++) {
            treeList.add(DecisionTree.fromJson(treesArray.get(i).toString()));
        }

        return new RandomForestModel(modelName, fCount, treeList);
    }
}
