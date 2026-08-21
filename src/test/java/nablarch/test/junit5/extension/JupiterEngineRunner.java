package nablarch.test.junit5.extension;

import org.junit.jupiter.engine.JupiterTestEngine;
import org.junit.platform.engine.ConfigurationParameters;
import org.junit.platform.engine.DiscoveryFilter;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.ExecutionRequest;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.discovery.ClassSelector;
import org.junit.platform.engine.discovery.DiscoverySelectors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 指定したテストクラスを JUnit 5 のエンジンで実行し、その実行結果を収集するテスト用のランナー。
 * <p>
 * 「テストが失敗すること」自体を検証したい場合、検証対象のテストを surefire の実行対象に含めることはできない。
 * このランナーは、検証対象のテストクラスを {@link JupiterTestEngine} で直接実行することで、
 * 失敗も含めた実行結果を、それを検証する側のテストメソッドから観測できるようにする。
 * </p>
 * <p>
 * 検証対象のテストクラスは、 surefire に拾われないよう、
 * 検証する側のテストクラスの入れ子クラスとして宣言すること。
 * surefire は入れ子クラス({@code *$*})を既定で実行対象から除外する。
 * </p>
 * @author Ito Kiyohito
 */
public final class JupiterEngineRunner {

    /**
     * 設定値を何も持たない {@link ConfigurationParameters}。
     */
    private static final ConfigurationParameters EMPTY_CONFIGURATION_PARAMETERS = new ConfigurationParameters() {
        @Override
        public Optional<String> get(String key) {
            return Optional.empty();
        }

        @Override
        public Optional<Boolean> getBoolean(String key) {
            return Optional.empty();
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public Set<String> keySet() {
            return Collections.emptySet();
        }
    };

    /**
     * インスタンス化を禁止する。
     */
    private JupiterEngineRunner() {
    }

    /**
     * 指定されたテストクラスを JUnit 5 のエンジンで実行する。
     * @param testClass 実行するテストクラス
     * @return 実行結果
     */
    public static ExecutionSummary run(Class<?> testClass) {
        JupiterTestEngine engine = new JupiterTestEngine();

        TestDescriptor root = engine.discover(new SingleClassDiscoveryRequest(testClass),
                UniqueId.forEngine(engine.getId()));

        RecordingListener listener = new RecordingListener();
        engine.execute(ExecutionRequest.create(root, listener, EMPTY_CONFIGURATION_PARAMETERS));

        return new ExecutionSummary(listener.testCount, listener.failures);
    }

    /**
     * テストクラスを 1 つだけ選択する {@link EngineDiscoveryRequest}。
     */
    private static final class SingleClassDiscoveryRequest implements EngineDiscoveryRequest {
        private final ClassSelector selector;

        /**
         * 指定されたテストクラスを選択するリクエストを生成する。
         * @param testClass 選択するテストクラス
         */
        private SingleClassDiscoveryRequest(Class<?> testClass) {
            selector = DiscoverySelectors.selectClass(testClass);
        }

        @Override
        public <T extends DiscoverySelector> List<T> getSelectorsByType(Class<T> selectorType) {
            return selectorType.isInstance(selector)
                    ? Collections.singletonList(selectorType.cast(selector))
                    : Collections.emptyList();
        }

        @Override
        public <T extends DiscoveryFilter<?>> List<T> getFiltersByType(Class<T> filterType) {
            return Collections.emptyList();
        }

        @Override
        public ConfigurationParameters getConfigurationParameters() {
            return EMPTY_CONFIGURATION_PARAMETERS;
        }
    }

    /**
     * 実行されたテストの件数と、発生した例外を記録する {@link EngineExecutionListener}。
     */
    private static final class RecordingListener implements EngineExecutionListener {
        private final List<Throwable> failures = new ArrayList<>();
        private int testCount;

        @Override
        public void executionFinished(TestDescriptor testDescriptor, TestExecutionResult testExecutionResult) {
            if (testDescriptor.isTest()) {
                testCount++;
            }
            testExecutionResult.getThrowable().ifPresent(failures::add);
        }
    }

    /**
     * テストクラス 1 つ分の実行結果。
     */
    public static final class ExecutionSummary {
        private final int testCount;
        private final List<Throwable> failures;

        /**
         * 実行結果を生成する。
         * @param testCount 実行されたテストの件数
         * @param failures 発生した例外のリスト
         */
        private ExecutionSummary(int testCount, List<Throwable> failures) {
            this.testCount = testCount;
            this.failures = Collections.unmodifiableList(new ArrayList<>(failures));
        }

        /**
         * 実行されたテストの件数を返す。
         * @return 実行されたテストの件数
         */
        public int getTestCount() {
            return testCount;
        }

        /**
         * 実行中に発生した例外のリストを返す。
         * <p>
         * テストメソッドで発生した例外だけでなく、テストクラス単位で発生した例外も含む。
         * </p>
         * @return 発生した例外のリスト。すべて成功した場合は空のリスト
         */
        public List<Throwable> getFailures() {
            return failures;
        }

        /**
         * 発生した例外がただ 1 つであることを確かめたうえで、その例外を返す。
         * @return 発生した例外
         * @throws AssertionError 発生した例外が 1 つでない場合
         */
        public Throwable getOnlyFailure() {
            if (failures.size() != 1) {
                throw new AssertionError("発生した例外がちょうど 1 つであることを期待したが、"
                        + failures.size() + " 件だった : " + failures);
            }
            return failures.get(0);
        }
    }
}
