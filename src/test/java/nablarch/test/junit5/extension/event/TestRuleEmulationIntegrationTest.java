package nablarch.test.junit5.extension.event;

import nablarch.test.event.TestEventDispatcher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * {@link TestEventDispatcherExtension#resolveTestRules()} で追加した JUnit 4 の {@link TestRule} が、
 * テストメソッドの実行を包む形で再現されることをテストする。
 * <p>
 * <strong>このテストは、現時点では意図的に失敗する。</strong>
 * 現在の実装は {@link TestRule} を前処理({@code beforeEach})の中だけで評価するため、
 * ルールの後処理がテストメソッドの実行より前に終わってしまう。
 * この未実装の仕様を失敗として固定するのがこのテストの役割であり、
 * タスク #4「TestRule の適用先を分離する」で実装を修正した時点で成功するようになる。
 * </p>
 * <p>
 * 検証は {@link #afterAll()} でまとめて行う。
 * テストメソッド自身が失敗していると {@code @AfterEach} の失敗は suppressed 扱いになり、
 * surefire のレポートに出力されないため、テストメソッドの成否と切り離して報告される位置に置いている。
 * </p>
 * @author Claude
 */
public class TestRuleEmulationIntegrationTest {

    /** ルールが記録する、テストメソッドの実行前を表すラベル。 */
    private static final String RULE_BEFORE = "rule-before";

    /** テストメソッドが記録する、テスト本体の実行を表すラベル。 */
    private static final String TEST = "test";

    /** ルールが記録する、テストメソッドの実行後を表すラベル。 */
    private static final String RULE_AFTER = "rule-after";

    /**
     * 実行順を記録する {@link TestRule}。
     * <p>
     * {@code base} はテストメソッドの実行を表す {@link Statement} であるため、
     * {@code base.evaluate()} の前後の記録はテストメソッドの前後に行われる必要がある。
     * </p>
     */
    public static class RecordingRule implements TestRule {
        private final List<String> executionLog;
        private List<String> logSnapshotAfterBase;

        public RecordingRule(List<String> executionLog) {
            this.executionLog = executionLog;
        }

        @Override
        public Statement apply(Statement base, Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    executionLog.add(RULE_BEFORE);
                    try {
                        base.evaluate();
                        logSnapshotAfterBase = new ArrayList<>(executionLog);
                    } finally {
                        executionLog.add(RULE_AFTER);
                    }
                }
            };
        }

        /**
         * {@code base.evaluate()} から戻った時点の実行ログの写しを返す。
         * <p>
         * テストメソッドがルールの内側で実行されたのであれば、この写しにテスト本体の記録が含まれる。
         * </p>
         * @return {@code base.evaluate()} から戻った時点の実行ログ。戻ってきていない場合は {@code null}
         */
        public List<String> getLogSnapshotAfterBase() {
            return logSnapshotAfterBase;
        }
    }

    /**
     * {@link RecordingRule} を持つサポートクラス。
     * <p>
     * 実行ログはこのインスタンスが持つため、テストインスタンスごとに新しいログになる。
     * </p>
     */
    public static class MockSupport extends TestEventDispatcher {
        private final List<String> executionLog = new ArrayList<>();
        public final RecordingRule recordingRule = new RecordingRule(executionLog);

        /**
         * テスト本体が実行されたことを記録する。
         */
        public void recordTestMethodExecution() {
            executionLog.add(TEST);
        }

        /**
         * 実行ログを返す。
         * @return 実行ログ
         */
        public List<String> getExecutionLog() {
            return executionLog;
        }
    }

    /**
     * {@link MockSupport} が持つ {@link TestRule} を再現する Extension。
     * <p>
     * 検証を {@link TestRuleEmulationIntegrationTest#afterAll()} で行えるように、
     * 実行が終わったサポートクラスのインスタンスを保持する。
     * </p>
     */
    public static class MockSupportExtension extends TestEventDispatcherExtension {
        private final Map<String, MockSupport> executedSupports = new LinkedHashMap<>();

        @Override
        protected TestEventDispatcher createSupport(Object testInstance, ExtensionContext context) {
            return new MockSupport();
        }

        @Override
        protected List<TestRule> resolveTestRules() {
            List<TestRule> testRules = new ArrayList<>(super.resolveTestRules());
            testRules.add(((MockSupport) support).recordingRule);
            return testRules;
        }

        @Override
        public void afterEach(ExtensionContext context) throws Exception {
            super.afterEach(context);
            executedSupports.put(context.getDisplayName(), (MockSupport) support);
        }

        /**
         * テストメソッドの実行が終わったサポートクラスのインスタンスを、
         * テストメソッドの表示名をキーにして実行された順に返す。
         * @return テストメソッドの表示名とサポートクラスのインスタンスの {@link Map}
         */
        public Map<String, MockSupport> getExecutedSupports() {
            return executedSupports;
        }
    }

    @RegisterExtension
    static MockSupportExtension extension = new MockSupportExtension();

    MockSupport support;

    /**
     * テスト本体の実行を記録する。
     * <p>
     * 検証は {@link #afterAll()} で行うため、このメソッドでは表明を行わない。
     * </p>
     */
    @Test
    void テストメソッドの実行がTestRuleに包まれていることをテスト() {
        support.recordTestMethodExecution();
    }

    /**
     * {@link ParameterizedTest} でもテスト本体の実行を記録する。
     * <p>
     * {@link ParameterizedTest} は通常のテストメソッドとは別の経路で実行されるため、
     * 通常のテストメソッドとは別に実行順を固定する。
     * </p>
     * @param parameter テストの実行回数を増やすためだけのパラメータ
     */
    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void パラメータ化テストの実行がTestRuleに包まれていることをテスト(int parameter) {
        support.recordTestMethodExecution();
    }

    /**
     * 実行が終わった全てのテストメソッドについて、ルールがテスト本体を包んでいたことを検証する。
     * <p>
     * 検証するのは {@code rule-before} → {@code test} → {@code rule-after} の相対順序だけであり、
     * {@code @BeforeEach} や {@code @AfterEach} がこの間のどこに入るかは問わない。
     * </p>
     */
    @AfterAll
    static void afterAll() {
        for (Map.Entry<String, MockSupport> entry : extension.getExecutedSupports().entrySet()) {
            String testDisplayName = entry.getKey();
            MockSupport executedSupport = entry.getValue();
            assertThat("TestRule の前処理と後処理は、テストメソッドの実行を挟む形で実行される: " + testDisplayName,
                    executedSupport.getExecutionLog(), is(Arrays.asList(RULE_BEFORE, TEST, RULE_AFTER)));
            assertThat("テストメソッドは、TestRule の base.evaluate() の内側で実行される: " + testDisplayName,
                    executedSupport.recordingRule.getLogSnapshotAfterBase(),
                    is(Arrays.asList(RULE_BEFORE, TEST)));
        }
    }
}
