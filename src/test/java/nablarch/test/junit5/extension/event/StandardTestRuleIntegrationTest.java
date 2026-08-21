package nablarch.test.junit5.extension.event;

import nablarch.test.junit5.extension.JupiterEngineRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.ErrorCollector;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.Stopwatch;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.rules.Verifier;
import org.junit.runner.Description;
import org.junit.runners.model.MultipleFailureException;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

/**
 * JUnit 4 が標準で提供する {@link TestRule} を
 * {@link TestEventDispatcherExtension#resolveTestRules()} で追加したときの振る舞いをテストする。
 * <p>
 * 「どのルールが使えて、使えるものにどんな制約が付くか」を実行結果として固定するのがこのクラスの役割である。
 * {@link org.junit.rules.Timeout} は {@link TimeoutRuleIntegrationTest} で、
 * {@link org.junit.rules.ExternalResource} は {@link TestRuleLifecycleIntegrationTest} で、
 * それぞれ固有の制約と合わせて確認しているため、このクラスでは扱わない。
 * </p>
 * @author Ito Kiyohito
 */
public class StandardTestRuleIntegrationTest extends RuleIntegrationTestBase {

    /**
     * 実行対象のテストクラスから参照する {@link TemporaryFolder}。
     */
    static TemporaryFolder temporaryFolder;

    /**
     * 実行対象のテストクラスから参照する {@link ErrorCollector}。
     */
    static ErrorCollector errorCollector;

    /**
     * {@link Stopwatch} が計測したテストメソッドの実行時間(ナノ秒)。
     */
    static long measuredRuntimeNanos;

    @Test
    void TemporaryFolderで作成した一時ファイルがテスト本体から使え_テストの後に削除されることをテスト() {
        temporaryFolder = new TemporaryFolder();
        ConfigurableTestRuleExtension.setTestRules(temporaryFolder);

        JupiterEngineRunner.ExecutionSummary summary = JupiterEngineRunner.run(TemporaryFolderTestFixture.class);

        assertThat(summary.getSuccessfulTestCount(), is(1));
        assertThat(executionLog, is(Collections.singletonList("file-exists")));
        assertThat("一時フォルダはルールの後処理で削除される",
                temporaryFolder.getRoot().exists(), is(false));
    }

    @Test
    void TemporaryFolderの一時フォルダはBeforeEachの時点ではまだ作られていないことをテスト() {
        temporaryFolder = new TemporaryFolder();
        ConfigurableTestRuleExtension.setTestRules(temporaryFolder);

        JupiterEngineRunner.ExecutionSummary summary =
                JupiterEngineRunner.run(TemporaryFolderInBeforeEachTestFixture.class);

        Throwable failure = summary.getOnlyFailure();
        assertThat(failure, is(instanceOf(IllegalStateException.class)));
        assertThat(failure.getMessage(), containsString("the temporary folder has not yet been created"));
        assertThat(executionLog, is(Collections.emptyList()));
    }

    /**
     * {@link ExpectedException#none()} は JUnit 4.13.1 で非推奨になっているが、
     * このテストは「ルールとして渡したときに動くか」を確かめるものであり、
     * ルールのインスタンスを得る手段が他にないため意図的に使用している。
     */
    @SuppressWarnings("deprecation")
    @Test
    void ExpectedExceptionで期待した例外を表明できることをテスト() {
        ExpectedException expectedException = ExpectedException.none();
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("テスト本体が投げた例外");
        ConfigurableTestRuleExtension.setTestRules(expectedException);

        JupiterEngineRunner.ExecutionSummary summary = JupiterEngineRunner.run(ThrowingTestFixture.class);

        assertThat(summary.getSuccessfulTestCount(), is(1));
    }

    @Test
    void ErrorCollectorが収集したエラーでテストが失敗することをテスト() {
        errorCollector = new ErrorCollector();
        ConfigurableTestRuleExtension.setTestRules(errorCollector);

        JupiterEngineRunner.ExecutionSummary summary = JupiterEngineRunner.run(ErrorCollectingTestFixture.class);

        Throwable failure = summary.getOnlyFailure();
        assertThat(failure, is(instanceOf(MultipleFailureException.class)));
        assertThat(((MultipleFailureException) failure).getFailures().size(), is(2));
        assertThat(executionLog, is(Collections.singletonList("test")));
    }

    @Test
    void Verifierが投げた例外でテストが失敗することをテスト() {
        ConfigurableTestRuleExtension.setTestRules(new FailingVerifier());

        JupiterEngineRunner.ExecutionSummary summary = JupiterEngineRunner.run(RecordingTestFixture.class);

        Throwable failure = summary.getOnlyFailure();
        assertThat(failure, is(instanceOf(IllegalStateException.class)));
        assertThat(failure.getMessage(), is("verify が失敗した"));
        assertThat(executionLog, is(Arrays.asList("test", "verify")));
    }

    @Test
    void Stopwatchで実行時間を計測できるが_AfterEachの失敗はfailedで観測できないことをテスト() {
        measuredRuntimeNanos = -1L;
        ConfigurableTestRuleExtension.setTestRules(new RecordingStopwatch());

        JupiterEngineRunner.ExecutionSummary summary = JupiterEngineRunner.run(FailingAfterEachTestFixture.class);

        assertThat(summary.getOnlyFailure().getMessage(), is("@AfterEach が失敗した"));
        assertThat("テストは @AfterEach で失敗しているが、ルールからは成功として観測される",
                executionLog, is(Arrays.asList("test", "succeeded", "finished", "@AfterEach")));
        assertThat(measuredRuntimeNanos, is(greaterThanOrEqualTo(0L)));
    }

    @Test
    void RuleChainで指定した入れ子の順序が保たれることをテスト() {
        ConfigurableTestRuleExtension.setTestRules(RuleChain
                .outerRule(recordingRule("outer"))
                .around(recordingRule("inner")));

        JupiterEngineRunner.ExecutionSummary summary = JupiterEngineRunner.run(RecordingTestFixture.class);

        assertThat(summary.getSuccessfulTestCount(), is(1));
        assertThat(executionLog, is(Arrays.asList(
                "outer-before", "inner-before", "test", "inner-after", "outer-after")));
    }

    @Test
    void DisableOnDebugでラップしたルールがデバッグ実行でない場合に適用されることをテスト() {
        DisableOnDebug disableOnDebug = new DisableOnDebug(recordingRule("rule"));
        Assumptions.assumeFalse(disableOnDebug.isDebugging(),
                "デバッグ実行中はルールが無効化されるため、適用されることを検証できない");
        ConfigurableTestRuleExtension.setTestRules(disableOnDebug);

        JupiterEngineRunner.ExecutionSummary summary = JupiterEngineRunner.run(RecordingTestFixture.class);

        assertThat(summary.getSuccessfulTestCount(), is(1));
        assertThat(executionLog, is(Arrays.asList("rule-before", "test", "rule-after")));
    }

    /**
     * {@link #temporaryFolder} に一時ファイルを作成する、実行対象のテストクラス。
     */
    @ExtendWith(ConfigurableTestRuleExtension.class)
    static class TemporaryFolderTestFixture {
        @Test
        void test() throws IOException {
            File file = temporaryFolder.newFile("test.txt");
            executionLog.add(file.exists() ? "file-exists" : "file-not-exists");
        }
    }

    /**
     * {@code @BeforeEach} から {@link #temporaryFolder} を参照する、実行対象のテストクラス。
     */
    @ExtendWith(ConfigurableTestRuleExtension.class)
    static class TemporaryFolderInBeforeEachTestFixture {
        @BeforeEach
        void setUp() {
            temporaryFolder.getRoot();
        }

        @Test
        void test() {
            executionLog.add("test");
        }
    }

    /**
     * テスト本体で例外を投げる、実行対象のテストクラス。
     */
    @ExtendWith(ConfigurableTestRuleExtension.class)
    static class ThrowingTestFixture {
        @Test
        void test() {
            throw new IllegalArgumentException("テスト本体が投げた例外");
        }
    }

    /**
     * テスト本体で {@link #errorCollector} にエラーを収集する、実行対象のテストクラス。
     */
    @ExtendWith(ConfigurableTestRuleExtension.class)
    static class ErrorCollectingTestFixture {
        @Test
        void test() {
            executionLog.add("test");
            errorCollector.addError(new AssertionError("1 件目のエラー"));
            errorCollector.addError(new AssertionError("2 件目のエラー"));
        }
    }

    /**
     * {@code @AfterEach} が例外を投げる、実行対象のテストクラス。
     */
    @ExtendWith(ConfigurableTestRuleExtension.class)
    static class FailingAfterEachTestFixture {
        @Test
        void test() {
            executionLog.add("test");
        }

        @AfterEach
        void tearDown() {
            executionLog.add("@AfterEach");
            throw new IllegalStateException("@AfterEach が失敗した");
        }
    }

    /**
     * 検証で必ず失敗する {@link Verifier}。
     */
    private static class FailingVerifier extends Verifier {
        @Override
        protected void verify() {
            executionLog.add("verify");
            throw new IllegalStateException("verify が失敗した");
        }
    }

    /**
     * テストの結果と実行時間を記録する {@link Stopwatch}。
     */
    private static class RecordingStopwatch extends Stopwatch {
        @Override
        protected void succeeded(long nanos, Description description) {
            executionLog.add("succeeded");
        }

        @Override
        protected void failed(long nanos, Throwable e, Description description) {
            executionLog.add("failed");
        }

        @Override
        protected void finished(long nanos, Description description) {
            executionLog.add("finished");
            measuredRuntimeNanos = nanos;
        }
    }
}
