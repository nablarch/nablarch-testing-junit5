package nablarch.test.junit5.extension.event;

import nablarch.test.event.TestEventDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * {@link TestEventDispatcherExtension#resolveTestRules()} で追加した JUnit 4 の {@link TestRule} が、
 * テストメソッドの実行を包む形で再現されることをテストする。
 *
 * @author Claude
 */
@ExtendWith(TestRuleEmulationIntegrationTest.MockSupportExtension.class)
class TestRuleEmulationIntegrationTest {

    /** {@link TestRule} とテストメソッドの実行順を記録するリスト。 */
    static final List<String> EXECUTION_LOG = new ArrayList<>();

    /**
     * 実行順を {@link #EXECUTION_LOG} に記録する {@link TestRule}。
     * <p>
     * {@code base} はテストメソッドの実行を表す {@link Statement} であるため、
     * {@code base.evaluate()} の前後の記録はテストメソッドの前後に行われる必要がある。
     * </p>
     */
    static class RecordingRule implements TestRule {
        @Override
        public Statement apply(Statement base, Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    EXECUTION_LOG.add("rule-before");
                    try {
                        base.evaluate();
                    } finally {
                        EXECUTION_LOG.add("rule-after");
                    }
                }
            };
        }
    }

    /** {@link RecordingRule} を持つサポートクラス。 */
    public static class MockSupport extends TestEventDispatcher {
        public RecordingRule recordingRule = new RecordingRule();
    }

    /** {@link MockSupport} が持つ {@link TestRule} を再現する Extension。 */
    public static class MockSupportExtension extends TestEventDispatcherExtension {
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
    }

    MockSupport support;

    @Test
    void テストメソッドの実行がTestRuleに包まれていることをテスト() {
        EXECUTION_LOG.add("test");
        assertThat("テストメソッドの実行時点では、まだ TestRule の後処理は実行されていない",
                EXECUTION_LOG, is(Arrays.asList("rule-before", "test")));
    }

    @AfterEach
    void tearDown() {
        assertThat("TestRule の後処理は、テストメソッドの実行後に実行される",
                EXECUTION_LOG, is(Arrays.asList("rule-before", "test", "rule-after")));
    }
}
