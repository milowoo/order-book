package com.orderbook.core.strategy.ml;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 用于回归的决策树。
 内部节点：根据特征索引和阈值进行分裂（判断）。
 叶子节点：返回一个常数值。
 支持通过 toJson/fromJson 进行 JSON 序列化。
 */
public class DecisionTree {

    private Node root;

    public DecisionTree(Node root) {
        this.root = root;
    }

    /**
     * 对给定的特征向量进行数值预测。
     */
    public double predict(double[] features) {
        if (root == null) return 0.0;
        Node node = root;
        while (!node.isLeaf) {
            if (features[node.featureIndex] <= node.threshold) {
                node = node.left;
            } else {
                node = node.right;
            }
        }
        return node.leafValue;
    }

    /**
     * 获取根节点（由序列化器和模型检查功能使用）。
     */
    public Node getRoot() {
        return root;
    }

    /** Number of nodes in this tree. */
    public int size() {
        if (root == null) return 0;
        int count = 0;
        Deque<Node> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            Node node = stack.pop();
            count++;
            if (!node.isLeaf) {
                stack.push(node.right);
                stack.push(node.left);
            }
        }
        return count;
    }

    // ---- Serialization ----

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        serializeNode(root, sb);
        sb.append('}');
        return sb.toString();
    }

    private void serializeNode(Node node, StringBuilder sb) {
        if (node.isLeaf) {
            sb.append("\"leaf\":").append(node.leafValue);
        } else {
            sb.append("\"feature\":").append(node.featureIndex)
              .append(",\"threshold\":").append(node.threshold)
              .append(",\"left\":{");
            serializeNode(node.left, sb);
            sb.append("},\"right\":{");
            serializeNode(node.right, sb);
            sb.append('}');
        }
    }

    /** Parse a tree from JSON format produced by toJson(). */
    public static DecisionTree fromJson(String json) {
        return new DecisionTree(parseNode(json, new int[1]));
    }

    private static Node parseNode(String json, int[] pos) {
        // Skip whitespace and {
        skipWhitespace(json, pos);
        if (json.charAt(pos[0]) == '{') pos[0]++;

        Node node = new Node();

        // Check if this is a leaf or internal node
        skipWhitespace(json, pos);
        if (json.startsWith("\"leaf\"", pos[0])) {
            node.isLeaf = true;
            pos[0] += 6; // skip "leaf"
            skipWhitespace(json, pos);
            if (json.charAt(pos[0]) == ':') pos[0]++;
            skipWhitespace(json, pos);
            // Read number value
            int start = pos[0];
            while (pos[0] < json.length() && (Character.isDigit(json.charAt(pos[0]))
                    || json.charAt(pos[0]) == '.' || json.charAt(pos[0]) == '-'
                    || json.charAt(pos[0]) == 'e' || json.charAt(pos[0]) == 'E'
                    || json.charAt(pos[0]) == '+')) {
                pos[0]++;
            }
            node.leafValue = Double.parseDouble(json.substring(start, pos[0]));
        } else {
            node.isLeaf = false;
            // Parse feature index
            expectKey("feature", json, pos);
            node.featureIndex = (int) readNumber(json, pos);

            skipWhitespace(json, pos);
            if (json.charAt(pos[0]) == ',') pos[0]++;

            // Parse threshold
            expectKey("threshold", json, pos);
            node.threshold = readNumber(json, pos);

            skipWhitespace(json, pos);
            if (json.charAt(pos[0]) == ',') pos[0]++;

            // Parse left child
            expectKey("left", json, pos);
            node.left = parseNode(json, pos);

            skipWhitespace(json, pos);
            if (json.charAt(pos[0]) == ',') pos[0]++;

            // Parse right child
            expectKey("right", json, pos);
            node.right = parseNode(json, pos);
        }

        // Skip closing }
        skipWhitespace(json, pos);
        if (pos[0] < json.length() && json.charAt(pos[0]) == '}') pos[0]++;

        return node;
    }

    private static void expectKey(String key, String json, int[] pos) {
        skipWhitespace(json, pos);
        if (json.startsWith("\"" + key + "\"", pos[0])) {
            pos[0] += key.length() + 2;
        }
        skipWhitespace(json, pos);
        if (json.charAt(pos[0]) == ':') pos[0]++;
    }

    private static double readNumber(String json, int[] pos) {
        skipWhitespace(json, pos);
        int start = pos[0];
        while (pos[0] < json.length() && (Character.isDigit(json.charAt(pos[0]))
                || json.charAt(pos[0]) == '.' || json.charAt(pos[0]) == '-'
                || json.charAt(pos[0]) == 'e' || json.charAt(pos[0]) == 'E'
                || json.charAt(pos[0]) == '+')) {
            pos[0]++;
        }
        return Double.parseDouble(json.substring(start, pos[0]));
    }

    private static void skipWhitespace(String json, int[] pos) {
        while (pos[0] < json.length() && Character.isWhitespace(json.charAt(pos[0]))) {
            pos[0]++;
        }
    }

    // ---- Node ----

    public static class Node {
        int featureIndex;
        double threshold;
        Node left;
        Node right;
        double leafValue;
        boolean isLeaf;

        /** Create a leaf node. */
        public static Node leaf(double value) {
            Node n = new Node();
            n.isLeaf = true;
            n.leafValue = value;
            return n;
        }

        /** Create an internal split node. */
        public static Node split(int featureIndex, double threshold, Node left, Node right) {
            Node n = new Node();
            n.featureIndex = featureIndex;
            n.threshold = threshold;
            n.left = left;
            n.right = right;
            return n;
        }
    }
}
