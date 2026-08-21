package nablarch.test.junit5.extension.event;

import nablarch.test.event.TestEventDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
 * @author Claude
 */
@ExtendWith(TestRuleEmulationIntegrationTest.RecordingSupportExtension.class)
public class TestRuleEmulationIntegrationTest {

    /** 内側に入るルールが記録するラベルの接頭辞。 */
    private static final String INNER = "inner";

    /** 外側に入るルールが記録するラベルの接頭辞。 */
    private static final String OUTER = "outer";

    /** ルールがテストメソッドの実行前に記録するラベルの接尾辞。 */
    private static final String BEFORE = "-before";

    /** ルールがテストメソッドの実行後に記録するラベルの接尾辞。 */
    private static final String AFTER = "-after";

    /** テストメソッドが記録する、テスト本体の実行を表すラベル。 */
    private static final String TEST = "test";

    /**
     * 実行順と実行スレッドを記録する {@link TestRule}。
     * <p>
     * {@code base} はテストメソッドの実行を表す {@link Statement} であるため、
     * {@code base.evaluate()} の前後の記録はテストメソッドの前後に行われる必要がある。
     * </p>
     */
    static class RecordingRule implements TestRule {
        private final RecordingSupport support;
        private final String labelPrefix;

        RecordingRule(RecordingSupport support, String labelPrefix) {
            this.support = support;
            this.labelPrefix = labelPrefix;
        }

        @Override
        public Statement apply(Statement base, Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    support.record(labelPrefix + BEFORE);
                    try {
                        base.evaluate();
                    } finally {
                        support.record(labelPrefix + AFTER);
                    }
                }
            };
        }
    }

    /**
     * 入れ子になる 2 本の {@link RecordingRule} を持つサポートクラス。
     * <p>
     * 実行ログはこのインスタンスが持つため、テストインスタンスごとに新しいログになる。
     * </p>
     */
    static class RecordingSupport extends TestEventDispatcher {
        private final List<String> executionLog = new ArrayList<>();
        private final Set<String> recordedThreadNames = new LinkedHashSet<>();

        public final RecordingRule innerRule = new RecordingRule(this, INNER);
        public final RecordingRule outerRule = new RecordingRule(this, OUTER);

        /**
         * テスト本体が実行されたことを記録する。
         */
        void recordTestMethodExecution() {
            record(TEST);
        }

        /**
         * ラベルと、そのラベルを記録したスレッドの名前を記録する。
         * @param label 記録するラベル
         */
        void record(String label) {
            executionLog.add(label);
            recordedThreadNames.add(Thread.currentThread().getName());
        }

        /**
         * 実行ログを返す。
         * @return 実行ログ
         */
        List<String> getExecutionLog() {
            return executionLog;
        }

        /**
         * 記録が行われたスレッドの名前を返す。
         * @return 記録が行われたスレッドの名前
         */
        Set<String> getRecordedThreadNames() {
            return recordedThreadNames;
        }
    }

    /**
     * {@link RecordingSupport} が持つ {@link TestRule} を再現する Extension。
     */
    static class RecordingSupportExtension extends TestEventDispatcherExtension {

        @Override
        protected TestEventDispatcher createSupport(Object testInstance, ExtensionContext context) {
            return new RecordingSupport();
        }

        /**
         * {@inheritDoc}
         * <p>
         * ルールはリストの先頭から順に一つ前の {@link Statement} へ適用されていくため、
         * リストの末尾に追加したルールほど外側になる（{@link TestEventDispatcherExtension} のルール適用ループ）。
         * </p>
         */
        @Override
        protected List<TestRule> resolveTestRules() {
            RecordingSupport recordingSupport = (RecordingSupport) support;
            List<TestRule> testRules = new ArrayList<>(super.resolveTestRules());
            testRules.add(recordingSupport.innerRule);
            testRules.add(recordingSupport.outerRule);
            return testRules;
        }
    }

    RecordingSupport support;

    @Test
    void テストメソッドの実行がTestRuleに包まれていることをテスト() {
        support.recordTestMethodExecution();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void パラメータ化テストの実行がTestRuleに包まれていることをテスト(int parameter) {
        support.recordTestMethodExecution();
    }

    @AfterEach
    void ルールがテスト本体を包んでいたことを検証する() {
        assertThat("TestRule とテストメソッドは同一のスレッドで実行される: " + support.getRecordedThreadNames(),
                support.getRecordedThreadNames().size(), is(1));
        assertThat("TestRule の前処理と後処理は、テストメソッドの実行を挟む形で、入れ子を保って実行される",
                support.getExecutionLog(),
                is(Arrays.asList(OUTER + BEFORE, INNER + BEFORE, TEST, INNER + AFTER, OUTER + AFTER)));
    }
}
