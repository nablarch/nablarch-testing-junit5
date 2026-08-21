package nablarch.test.junit5.extension.event;

import nablarch.test.junit5.extension.JupiterEngineRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.rules.ExternalResource;
import org.junit.rules.TestRule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * {@link TestEventDispatcherExtension#resolveTestRules()} で追加した {@link TestRule} が、
 * テストのライフサイクルのどの位置で実行されるかをテストする。
 * <p>
 * ルールが包むのはテストメソッドの呼び出しだけであり、 {@code @BeforeEach} / {@code @AfterEach} は含まれない。
 * JUnit 4 ではルールが {@code @Before} / {@code @After} の外側にあったため、
 * ルールの前処理・後処理の位置は JUnit 4 とは異なる。この違いを実行ログで固定する。
 * </p>
 * @author Ito Kiyohito
 */
public class TestRuleLifecycleIntegrationTest {

    /**
     * 実行対象のテストクラスとルールが書き込む実行ログ。
     */
    static final List<String> executionLog = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUpExecutionLog() {
        executionLog.clear();
        ConfigurableTestRuleExtension.setTestRules(new RecordingExternalResource());
    }

    @AfterEach
    void clearTestRules() {
        ConfigurableTestRuleExtension.clearTestRules();
    }

    @Test
    void ExternalResourceのbeforeはBeforeEachの後に_afterはAfterEachの前に実行されることをテスト() {
        JupiterEngineRunner.ExecutionSummary summary = JupiterEngineRunner.run(LifecycleRecordingFixture.class);

        assertThat(summary.getFailures(), is(Collections.emptyList()));
        assertThat(summary.getTestCount(), is(1));
        assertThat(executionLog, is(Arrays.asList(
                "@BeforeEach", "resource-before", "test", "resource-after", "@AfterEach")));
    }

    @Test
    void BeforeEachが失敗した場合はルールの前処理も後処理も実行されないことをテスト() {
        JupiterEngineRunner.ExecutionSummary summary = JupiterEngineRunner.run(FailingBeforeEachFixture.class);

        assertThat(summary.getOnlyFailure().getMessage(), is("@BeforeEach が失敗した"));
        assertThat(executionLog, is(Arrays.asList("@BeforeEach", "@AfterEach")));
    }

    /**
     * ライフサイクルメソッドとテスト本体の実行を記録する、実行対象のテストクラス。
     */
    @ExtendWith(ConfigurableTestRuleExtension.class)
    static class LifecycleRecordingFixture {
        @BeforeEach
        void setUp() {
            executionLog.add("@BeforeEach");
        }

        @Test
        void test() {
            executionLog.add("test");
        }

        @AfterEach
        void tearDown() {
            executionLog.add("@AfterEach");
        }
    }

    /**
     * {@code @BeforeEach} が例外を投げる、実行対象のテストクラス。
     */
    @ExtendWith(ConfigurableTestRuleExtension.class)
    static class FailingBeforeEachFixture {
        @BeforeEach
        void setUp() {
            executionLog.add("@BeforeEach");
            throw new IllegalStateException("@BeforeEach が失敗した");
        }

        @Test
        void test() {
            executionLog.add("test");
        }

        @AfterEach
        void tearDown() {
            executionLog.add("@AfterEach");
        }
    }

    /**
     * 前処理と後処理の実行を記録する {@link ExternalResource}。
     */
    private static class RecordingExternalResource extends ExternalResource {
        @Override
        protected void before() {
            executionLog.add("resource-before");
        }

        @Override
        protected void after() {
            executionLog.add("resource-after");
        }
    }
}
