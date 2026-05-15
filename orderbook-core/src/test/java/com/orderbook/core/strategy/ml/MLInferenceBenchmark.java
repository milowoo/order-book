package com.orderbook.core.strategy.ml;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * ML 推理延迟压力测试。
 * <p>
 * 不依赖 Spring 上下文，可直接在 IDE 或命令行运行。
 * 生成合成随机森林模型（50棵树，最大深度6，10个特征，匹配生产配置），
 * 测量 JIT 预热前后的延迟分布。
 * </p>
 * <pre>
 * 运行方式：
 *   mvn test-compile -pl orderbook-core -am -q
 *   java -cp "orderbook-core/target/test-classes:orderbook-core/target/classes:$(mvn -pl orderbook-core dependency:build-classpath -q -DincludeScope=runtime -Dmdep.outputFile=/dev/stdout)" \
 *        com.orderbook.core.strategy.ml.MLInferenceBenchmark
 * </pre>
 */
public class MLInferenceBenchmark {

    private static final int WARMUP_ITERATIONS = 5_000;
    private static final int BENCHMARK_ITERATIONS = 50_000;
    private static final int FEATURE_COUNT = 10;

    public static void main(String[] args) {
        System.out.println("=== ML Inference Latency Benchmark ===");
        System.out.println();

        int[] treeCounts = {10, 25, 50, 100};
        int[] maxDepths = {4, 6, 8, 10};

        // --- 基准配置: 50 棵树, 深度 6 ---
        System.out.println("--- Default config (50 trees, depth 6, 10 features) ---");
        RandomForestModel defaultModel = generateSyntheticModel("benchmark", FEATURE_COUNT, 50, 6, 42);
        runBenchmark(defaultModel, WARMUP_ITERATIONS, BENCHMARK_ITERATIONS);
        System.out.println();

        // --- 不同树数量对比 ---
        System.out.println("--- Tree count scaling ---");
        for (int n : treeCounts) {
            RandomForestModel model = generateSyntheticModel("trees" + n, FEATURE_COUNT, n, 6, 42);
            long elapsed = runTimed(model, WARMUP_ITERATIONS, BENCHMARK_ITERATIONS);
            System.out.printf("  trees=%-3d  total=%d ms  avg=%.2f μs/call%n",
                    n, elapsed, (double) elapsed * 1000 / BENCHMARK_ITERATIONS);
        }
        System.out.println();

        // --- 不同深度对比 ---
        System.out.println("--- Max depth scaling ---");
        for (int d : maxDepths) {
            RandomForestModel model = generateSyntheticModel("depth" + d, FEATURE_COUNT, 50, d, 42);
            long elapsed = runTimed(model, WARMUP_ITERATIONS, BENCHMARK_ITERATIONS);
            System.out.printf("  depth=%-2d  total=%d ms  avg=%.2f μs/call%n",
                    d, elapsed, (double) elapsed * 1000 / BENCHMARK_ITERATIONS);
        }
        System.out.println();

        // --- 预热前后对比 ---
        System.out.println("--- JIT warmup effect (50 trees, depth 6) ---");
        RandomForestModel warmModel = generateSyntheticModel("warmup", FEATURE_COUNT, 50, 6, 42);
        double[][] samples = generateRandomSamples(BENCHMARK_ITERATIONS, FEATURE_COUNT, 99);

        // 无预热：首次调用耗时
        long coldStart = System.nanoTime();
        warmModel.predict(samples[0]);
        long coldElapsed = System.nanoTime() - coldStart;
        System.out.printf("  Cold start (first call):     %d μs%n", TimeUnit.NANOSECONDS.toMicros(coldElapsed));

        // 少量预热
        for (int i = 0; i < 100; i++) {
            warmModel.predict(samples[i % samples.length]);
        }
        long warm100Avg = avgLatency(warmModel, samples, 100, 1000);
        System.out.printf("  After 100 calls (avg):       %d μs%n", TimeUnit.NANOSECONDS.toMicros(warm100Avg));

        // 充分预热
        for (int i = 0; i < 5000; i++) {
            warmModel.predict(samples[i % samples.length]);
        }
        long warmFull = avgLatency(warmModel, samples, 100, 5000);
        System.out.printf("  After 5000 calls (avg):      %d μs%n", TimeUnit.NANOSECONDS.toMicros(warmFull));
    }

    /** Run full benchmark with warmup + latency percentiles. */
    private static void runBenchmark(RandomForestModel model, int warmup, int iterations) {
        double[][] samples = generateRandomSamples(warmup + iterations, FEATURE_COUNT, 42);

        // Warmup: trigger JIT compilation
        for (int i = 0; i < warmup; i++) {
            model.predict(samples[i]);
        }

        // Measured run
        long[] latencies = new long[iterations];
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            model.predict(samples[warmup + i]);
            latencies[i] = System.nanoTime() - start;
        }

        // Sort for percentiles
        java.util.Arrays.sort(latencies);
        long p50 = latencies[(int) (iterations * 0.50)];
        long p90 = latencies[(int) (iterations * 0.90)];
        long p95 = latencies[(int) (iterations * 0.95)];
        long p99 = latencies[(int) (iterations * 0.99)];
        long p999 = latencies[(int) (iterations * 0.999)];
        long max = latencies[iterations - 1];
        long sum = 0;
        for (long l : latencies) sum += l;
        long avg = sum / iterations;

        System.out.printf("  Calls:    %d%n", iterations);
        System.out.printf("  Avg:      %d μs%n", TimeUnit.NANOSECONDS.toMicros(avg));
        System.out.printf("  P50:      %d μs%n", TimeUnit.NANOSECONDS.toMicros(p50));
        System.out.printf("  P90:      %d μs%n", TimeUnit.NANOSECONDS.toMicros(p90));
        System.out.printf("  P95:      %d μs%n", TimeUnit.NANOSECONDS.toMicros(p95));
        System.out.printf("  P99:      %d μs%n", TimeUnit.NANOSECONDS.toMicros(p99));
        System.out.printf("  P999:     %d μs%n", TimeUnit.NANOSECONDS.toMicros(p999));
        System.out.printf("  Max:      %d μs%n", TimeUnit.NANOSECONDS.toMicros(max));
    }

    /** Time total execution (pre-warmed), return milliseconds. */
    private static long runTimed(RandomForestModel model, int warmup, int iterations) {
        double[][] samples = generateRandomSamples(warmup + iterations, FEATURE_COUNT, 42);

        for (int i = 0; i < warmup; i++) {
            model.predict(samples[i]);
        }

        long start = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            model.predict(samples[warmup + i]);
        }
        return System.currentTimeMillis() - start;
    }

    /** Measure average latency over N calls (pre-warmed). */
    private static long avgLatency(RandomForestModel model, double[][] samples, int n, int offset) {
        long sum = 0;
        for (int i = 0; i < n; i++) {
            long start = System.nanoTime();
            model.predict(samples[offset + i]);
            sum += System.nanoTime() - start;
        }
        return sum / n;
    }

    // ---- Synthetic model generation ----

    private static RandomForestModel generateSyntheticModel(String name, int featureCount,
                                                            int numTrees, int maxDepth, long seed) {
        Random rand = new Random(seed);
        List<DecisionTree> trees = new ArrayList<>(numTrees);
        for (int t = 0; t < numTrees; t++) {
            trees.add(generateTree(rand, featureCount, maxDepth));
        }
        return new RandomForestModel(name, featureCount, trees);
    }

    private static DecisionTree generateTree(Random rand, int featureCount, int maxDepth) {
        return new DecisionTree(generateNode(rand, featureCount, maxDepth, 0));
    }

    private static DecisionTree.Node generateNode(Random rand, int featureCount, int maxDepth, int depth) {
        if (depth >= maxDepth || rand.nextDouble() < 0.3) {
            // Leaf
            return DecisionTree.Node.leaf(rand.nextGaussian());
        }
        int featureIndex = rand.nextInt(featureCount);
        double threshold = rand.nextGaussian();
        DecisionTree.Node left = generateNode(rand, featureCount, maxDepth, depth + 1);
        DecisionTree.Node right = generateNode(rand, featureCount, maxDepth, depth + 1);
        return DecisionTree.Node.split(featureIndex, threshold, left, right);
    }

    private static double[][] generateRandomSamples(int count, int featureCount, long seed) {
        Random rand = new Random(seed);
        double[][] samples = new double[count][featureCount];
        for (int i = 0; i < count; i++) {
            for (int j = 0; j < featureCount; j++) {
                samples[i][j] = rand.nextGaussian();
            }
        }
        return samples;
    }
}
