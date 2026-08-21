package nablarch.test.junit5.extension.event;

import nablarch.test.junit5.extension.JupiterEngineRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.platform.commons.JUnitException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
 * @author Ito Kiyohito
 */
public class TestRuleInvocationContractIntegrationTest {

    /**
     * 実行対象のテストクラスとルールが書き込む実行ログ。
     */
    static final List<String> executionLog = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUpExecutionLog() {
        executionLog.clear();
    }

    @AfterEach
    void clearTestRules() {
        ConfigurableTestRuleExtension.clearTestRules();
    }

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

    /**
     * テスト本体が実行されたことだけを記録する、実行対象のテストクラス。
     */
    @ExtendWith(ConfigurableTestRuleExtension.class)
    static class RecordingTestFixture {
        @Test
        void test() {
            executionLog.add("test");
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
