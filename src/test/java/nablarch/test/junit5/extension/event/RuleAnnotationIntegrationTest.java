package nablarch.test.junit5.extension.event;

import nablarch.test.event.TestEventDispatcher;
import nablarch.test.junit5.extension.JupiterEngineRunner;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.rules.TestRule;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * 独自拡張クラスのフィールドに {@code @Rule} を付けただけでは、
 * JUnit 5 上ではそのルールが発火しないことをテストする。
 * <p>
 * {@code @Rule} は JUnit 4 のランナーが解釈するアノテーションであり、 JUnit 5 は解釈しない。
 * ルールを適用するには {@link TestEventDispatcherExtension#resolveTestRules()} を
 * オーバーライドして返す必要がある。
 * ここでは同じサポートクラス・同じルールに対して、
 * {@link TestEventDispatcherExtension#resolveTestRules()} を実装しない Extension と
 * 実装した Extension の 2 通りを実行し、差がその実装の有無だけであることを示す。
 * </p>
 * @author Ito Kiyohito
 */
public class RuleAnnotationIntegrationTest extends RuleIntegrationTestBase {

    @Test
    void resolveTestRulesを実装しないとRuleを付けたフィールドのルールが発火しないことをテスト() {
        JupiterEngineRunner.ExecutionSummary summary =
                JupiterEngineRunner.run(RuleNotResolvedTestFixture.class);

        assertThat("ルールが発火しないまま、テストは成功する", summary.getSuccessfulTestCount(), is(1));
        assertThat("ルールの前処理も後処理も実行ログに現れない",
                executionLog, is(Collections.singletonList("test")));
    }

    @Test
    void resolveTestRulesで返すと同じルールが発火することをテスト() {
        JupiterEngineRunner.ExecutionSummary summary =
                JupiterEngineRunner.run(RuleResolvedTestFixture.class);

        assertThat(summary.getSuccessfulTestCount(), is(1));
        assertThat(executionLog, is(Arrays.asList("rule-before", "test", "rule-after")));
    }

    /**
     * {@code @Rule} を付けた {@link TestRule} のフィールドを持つ独自サポートクラス。
     */
    static class CustomTestSupport extends TestEventDispatcher {
        @Rule
        public final TestRule customRule = recordingRule("rule");
    }

    /**
     * {@link CustomTestSupport} を生成するだけで、
     * {@link TestEventDispatcherExtension#resolveTestRules()} をオーバーライドしない Extension。
     */
    static class RuleNotResolvedExtension extends TestEventDispatcherExtension {
        @Override
        protected TestEventDispatcher createSupport(Object testInstance, ExtensionContext context) {
            return new CustomTestSupport();
        }
    }

    /**
     * {@link RuleNotResolvedExtension} との差が
     * {@link TestEventDispatcherExtension#resolveTestRules()} の実装だけである Extension。
     */
    static class RuleResolvedExtension extends RuleNotResolvedExtension {
        @Override
        protected List<TestRule> resolveTestRules() {
            return Collections.singletonList(((CustomTestSupport) support).customRule);
        }
    }

    /**
     * {@link RuleNotResolvedExtension} を適用した、実行対象のテストクラス。
     */
    @ExtendWith(RuleNotResolvedExtension.class)
    static class RuleNotResolvedTestFixture {
        @Test
        void test() {
            executionLog.add("test");
        }
    }

    /**
     * {@link RuleResolvedExtension} を適用した、実行対象のテストクラス。
     */
    @ExtendWith(RuleResolvedExtension.class)
    static class RuleResolvedTestFixture {
        @Test
        void test() {
            executionLog.add("test");
        }
    }
}
