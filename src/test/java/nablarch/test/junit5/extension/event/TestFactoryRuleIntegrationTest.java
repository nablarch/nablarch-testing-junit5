package nablarch.test.junit5.extension.event;

import nablarch.test.junit5.extension.JupiterEngineRunner;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.rules.TestRule;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * {@code @TestFactory} が生成した {@link DynamicTest} には
 * {@link TestEventDispatcherExtension#resolveTestRules()} で追加した {@link TestRule} が
 * 適用されないことをテストする。
 * <p>
 * <b>これは「対応する」ためのテストではなく、「対応しない現状を固定する」ためのテストである。</b>
 * {@code @TestFactory} への対応は、
 * 動的テストへ渡す {@code Description} の作り方と、 1 個しかないルールのインスタンスを
 * N 件の動的テストへ適用するときの寿命を先に決める必要があるため、見送られている。
 * </p>
 * <p>
 * 問題は、ルールが適用されないことが例外にもならず、テストが成功してしまう点にある。
 * このテストが失敗したときは、 {@code interceptTestFactoryMethod} または
 * {@code interceptDynamicTest} の既定実装に変化があったことを意味する。
 * その場合は、対応することを決めたうえでこのテストを書き換えること。
 * </p>
 * @author Ito Kiyohito
 */
public class TestFactoryRuleIntegrationTest extends RuleIntegrationTestBase {

    @Test
    void TestFactoryで生成した動的テストにはルールが適用されず_例外にもならないことをテスト() {
        ConfigurableTestRuleExtension.setTestRules(recordingRule("rule"));

        JupiterEngineRunner.ExecutionSummary summary = JupiterEngineRunner.run(TestFactoryFixture.class);

        assertThat("ルールが適用されないまま、テストは成功する", summary.getSuccessfulTestCount(), is(1));
        assertThat("ルールの前処理も後処理も実行ログに現れない",
                executionLog, is(Arrays.asList("factory-body", "dynamic-1")));
    }

    /**
     * {@code @TestFactory} で動的テストを 1 件生成する、実行対象のテストクラス。
     */
    @ExtendWith(ConfigurableTestRuleExtension.class)
    static class TestFactoryFixture {
        @TestFactory
        Stream<DynamicTest> testFactory() {
            executionLog.add("factory-body");
            return Stream.of(DynamicTest.dynamicTest("dynamic-1", () -> executionLog.add("dynamic-1")));
        }
    }
}
