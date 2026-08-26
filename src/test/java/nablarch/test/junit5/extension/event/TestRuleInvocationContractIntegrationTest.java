package nablarch.test.junit5.extension.event;

import nablarch.test.junit5.extension.JupiterEngineRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.platform.commons.JUnitException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * {@link TestEventDispatcherExtension#resolveTestRules()} で追加した {@link TestRule} が
 * {@code base} を呼ぶ回数によって、テストの実行結果がどうなるかをテストする。
 * <p>
 * JUnit 5 の {@link InvocationInterceptor} は、テストメソッドの呼び出しをちょうど 1 回行うことを実装に要求している。
 * ルールは {@code base.evaluate()} を呼ぶ位置で前処理と後処理を書き分けるものであり、
 * {@code base} を呼ばないルール(スキップ系)や 2 回以上呼ぶルール(リトライ系)も書けてしまうため、
 * それらを渡したときに何が起きるかをここで固定する。
 * </p>
 * <p>
 * あわせて、テスト本体が例外を投げた場合にルールの後処理まで到達し、
 * その例外が置き換えられずに伝播することも固定する。
 * </p>
 * @author Ito Kiyohito
 */
public class TestRuleInvocationContractIntegrationTest extends RuleIntegrationTestBase {

    /**
     * {@link ThrowingTestBodyFixture} のテスト本体が投げる例外。
     * <p>
     * 伝播した例外が同一のインスタンスであることを表明するため、あらかじめ生成しておく。
     * </p>
     */
    static final IllegalStateException THROWN_BY_TEST_BODY = new IllegalStateException("テスト本体が投げた例外");

    @Test
    void baseを呼ばないルールを追加した場合はテストが例外で失敗し_テスト本体が実行されないことをテスト() {
        ConfigurableTestRuleExtension.setTestRules(new SkippingRule());

        JupiterEngineRunner.ExecutionSummary summary = JupiterEngineRunner.run(RecordingTestFixture.class);

        Throwable failure = summary.getOnlyFailure();
        assertThat(failure, is(instanceOf(JUnitException.class)));
        assertThat(failure.getMessage(), containsString("never called invocation"));
        assertThat(executionLog, is(Collections.emptyList()));
    }

    @Test
    void baseを2回呼ぶルールを追加した場合はテストが例外で失敗し_テスト本体は1回だけ実行されることをテスト() {
        ConfigurableTestRuleExtension.setTestRules(new TwiceInvokingRule());

        JupiterEngineRunner.ExecutionSummary summary = JupiterEngineRunner.run(RecordingTestFixture.class);

        Throwable failure = summary.getOnlyFailure();
        assertThat(failure, is(instanceOf(JUnitException.class)));
        assertThat(failure.getMessage(), containsString("multiple times"));
        assertThat(executionLog, is(Collections.singletonList("test")));
    }

    @Test
    void ルールがbaseを呼ぶ前に投げた例外は_置き換えられずそのまま伝播することをテスト() {
        IllegalStateException thrown = new IllegalStateException("ルールが投げた例外");
        ConfigurableTestRuleExtension.setTestRules(new ThrowingRule(thrown));

        JupiterEngineRunner.ExecutionSummary summary = JupiterEngineRunner.run(RecordingTestFixture.class);

        assertThat(summary.getOnlyFailure(), is(sameInstance(thrown)));
        assertThat(executionLog, is(Collections.emptyList()));
    }

    @Test
    void テスト本体が投げた例外はルールの後処理を経てから_置き換えられずそのまま伝播することをテスト() {
        ConfigurableTestRuleExtension.setTestRules(recordingRule("rule"));

        JupiterEngineRunner.ExecutionSummary summary = JupiterEngineRunner.run(ThrowingTestBodyFixture.class);

        assertThat("テスト本体が投げた例外はそのまま失敗の原因になる",
                summary.getOnlyFailure(), is(sameInstance(THROWN_BY_TEST_BODY)));
        assertThat("テスト本体が例外を投げてもルールの後処理は実行される",
                executionLog, is(Arrays.asList("rule-before", "test", "rule-after")));
    }

    /**
     * テスト本体が実行されたことを記録したうえで例外を投げる、実行対象のテストクラス。
     */
    @ExtendWith(ConfigurableTestRuleExtension.class)
    static class ThrowingTestBodyFixture {
        @Test
        void test() {
            executionLog.add("test");
            throw THROWN_BY_TEST_BODY;
        }
    }

    /**
     * {@code base} を呼ばない {@link TestRule}。
     */
    private static class SkippingRule implements TestRule {
        @Override
        public Statement apply(Statement base, Description description) {
            return new Statement() {
                @Override
                public void evaluate() {
                    // テストをスキップするルールを模して base を実行しない
                }
            };
        }
    }

    /**
     * {@code base} を 2 回呼ぶ {@link TestRule}。
     */
    private static class TwiceInvokingRule implements TestRule {
        @Override
        public Statement apply(Statement base, Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    base.evaluate();
                    base.evaluate();
                }
            };
        }
    }

    /**
     * {@code base} を呼ぶ前に、コンストラクタで指定された例外を投げる {@link TestRule}。
     */
    private static class ThrowingRule implements TestRule {
        private final Throwable thrown;

        /**
         * 投げる例外を指定してインスタンスを生成する。
         * @param thrown 投げる例外
         */
        private ThrowingRule(Throwable thrown) {
            this.thrown = thrown;
        }

        @Override
        public Statement apply(Statement base, Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    throw thrown;
                }
            };
        }
    }
}
