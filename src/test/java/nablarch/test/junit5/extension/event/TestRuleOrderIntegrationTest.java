package nablarch.test.junit5.extension.event;

import nablarch.test.junit5.extension.JupiterEngineRunner;
import org.junit.jupiter.api.Test;
import org.junit.rules.TestRule;

import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * {@link TestEventDispatcherExtension#resolveTestRules()} が返すリストの並びが、
 * {@link TestRule} の入れ子順を決めることをテストする。
 * <p>
 * リストの先頭にあるルールほど内側、末尾にあるルールが最も外側になる
 * (JUnit 4 の {@link org.junit.rules.RunRules} と同じ順序)。
 * </p>
 * <p>
 * 同じ 2 本のルールを順序だけ入れ替えて 2 通り実行し、実行ログがその順序に従って入れ替わることを表明する。
 * 片方向だけを表明すると、リストの並びを無視してルールを適用する実装でも
 * 「たまたま期待どおりに見える」ことがあるため、両方向を押さえている。
 * </p>
 * @author Ito Kiyohito
 */
public class TestRuleOrderIntegrationTest extends RuleIntegrationTestBase {

    @Test
    void リストの先頭のルールほど内側になることをテスト() {
        ConfigurableTestRuleExtension.setTestRules(recordingRule("A"), recordingRule("B"));

        JupiterEngineRunner.ExecutionSummary summary = JupiterEngineRunner.run(RecordingTestFixture.class);

        assertThat(summary.getSuccessfulTestCount(), is(1));
        assertThat("先頭の A が内側、末尾の B が外側になる",
                executionLog, is(Arrays.asList("B-before", "A-before", "test", "A-after", "B-after")));
    }

    @Test
    void リストの順序を入れ替えると入れ子順も入れ替わることをテスト() {
        ConfigurableTestRuleExtension.setTestRules(recordingRule("B"), recordingRule("A"));

        JupiterEngineRunner.ExecutionSummary summary = JupiterEngineRunner.run(RecordingTestFixture.class);

        assertThat(summary.getSuccessfulTestCount(), is(1));
        assertThat("先頭の B が内側、末尾の A が外側になる",
                executionLog, is(Arrays.asList("A-before", "B-before", "test", "B-after", "A-after")));
    }
}
