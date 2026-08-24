package nablarch.test.junit5.extension.event;

import nablarch.test.event.TestEventDispatcher;
import nablarch.test.junit5.extension.JupiterEngineRunner;
import org.junit.Rule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.junit.runners.model.TestTimedOutException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * 解説書が例に挙げている {@link Timeout} を
 * {@link TestEventDispatcherExtension#resolveTestRules()} で追加したときの振る舞いをテストする。
 * <p>
 * {@link Timeout} はテスト本体を別スレッドで実行するため、
 * テストがタイムアウトすることに加えて、テスト本体を実行するスレッドが
 * {@code beforeEach} を実行したスレッドとは別になることも押さえる。
 * 後者は、テスト本体から素の {@link ThreadLocal} に束縛した値が見えなくなることを意味する。
 * </p>
 * <p>
 * このクラスは実行ログも記録用ルールも使わないが、
 * {@link ConfigurableTestRuleExtension} に設定したルールをテストごとに解除する {@code @AfterEach} を
 * 得るために {@link RuleIntegrationTestBase} を継承している。
 * </p>
 * @author Ito Kiyohito
 */
public class TimeoutRuleIntegrationTest extends RuleIntegrationTestBase {

    /**
     * タイムアウト値(ミリ秒)。解説書の例と同じ値を使用する。
     */
    private static final long TIMEOUT_MILLIS = 1000L;

    /**
     * タイムアウト値より長いスリープ時間(ミリ秒)。解説書の例と同じ値を使用する。
     */
    private static final long SLEEP_MILLIS = 2000L;

    /**
     * {@code beforeEach} でスレッドに値を束縛するために使用する {@link ThreadLocal}。
     */
    private static final ThreadLocal<String> THREAD_LOCAL = new ThreadLocal<>();

    /**
     * {@code beforeEach} を実行したスレッド。
     */
    static Thread threadOfBeforeEach;

    /**
     * テスト本体を実行したスレッド。
     */
    static Thread threadOfTestMethod;

    /**
     * テスト本体から見えた {@link #THREAD_LOCAL} の値。
     */
    static String threadLocalValueInTestMethod;

    @AfterEach
    void removeThreadLocal() {
        THREAD_LOCAL.remove();
    }

    @Test
    void 解説書の例と同じ実装でTimeoutを追加するとテストがタイムアウトすることをテスト() {
        JupiterEngineRunner.ExecutionSummary summary = JupiterEngineRunner.run(SleepingTestFixture.class);

        Throwable failure = summary.getOnlyFailure();
        assertThat(failure, is(instanceOf(TestTimedOutException.class)));
        assertThat(failure.getMessage(), is("test timed out after " + TIMEOUT_MILLIS + " milliseconds"));
    }

    @Test
    void Timeoutを追加するとテスト本体が別スレッドで実行され_素のThreadLocalに束縛した値が見えなくなることをテスト() {
        ConfigurableTestRuleExtension.setTestRules(Timeout.seconds(30));

        JupiterEngineRunner.ExecutionSummary summary = JupiterEngineRunner.run(ThreadRecordingTestFixture.class);

        assertThat(summary.getSuccessfulTestCount(), is(1));
        assertThat("beforeEach で束縛した値がテスト本体から見えない",
                threadLocalValueInTestMethod, is(nullValue()));
        assertThat("テスト本体は beforeEach とは別のスレッドで実行される",
                threadOfTestMethod, is(not(sameInstance(threadOfBeforeEach))));
        assertThat("テスト本体を実行するスレッドは Timeout が起動したスレッドである",
                threadOfTestMethod.getName(), startsWith("Time-limited test"));
    }

    /**
     * 解説書の例と同じ形の、 {@link Timeout} をルールとして宣言した独自サポートクラス。
     */
    static class CustomTestSupport extends TestEventDispatcher {
        @Rule
        public final Timeout timeout = new Timeout(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    /**
     * 解説書の例と同じ形の、 {@link CustomTestSupport} のルールを再現する Extension。
     * <p>
     * 基底実装が空のリストを返すため、解説書の例と同じく {@code super.resolveTestRules()} を
     * ベースにせず、空のリストにルールを追加して返す。
     * </p>
     */
    static class CustomTestSupportExtension extends TestEventDispatcherExtension {
        @Override
        protected TestEventDispatcher createSupport(Object testInstance, ExtensionContext context) {
            return new CustomTestSupport();
        }

        @Override
        protected List<TestRule> resolveTestRules() {
            List<TestRule> testRules = new ArrayList<>();
            testRules.add(((CustomTestSupport) support).timeout);
            return testRules;
        }
    }

    /**
     * タイムアウト値より長くスリープする、実行対象のテストクラス。
     */
    @ExtendWith(CustomTestSupportExtension.class)
    static class SleepingTestFixture {
        @Test
        void test() throws InterruptedException {
            Thread.sleep(SLEEP_MILLIS);
        }
    }

    /**
     * テスト本体を実行しているスレッドと、そこから見える {@link #THREAD_LOCAL} の値を記録する Extension。
     */
    static class ThreadRecordingExtension extends ConfigurableTestRuleExtension {
        @Override
        public void beforeEach(ExtensionContext context) throws Exception {
            super.beforeEach(context);
            threadOfBeforeEach = Thread.currentThread();
            THREAD_LOCAL.set("beforeEach で束縛した値");
        }
    }

    /**
     * テスト本体を実行しているスレッドを記録する、実行対象のテストクラス。
     */
    @ExtendWith(ThreadRecordingExtension.class)
    static class ThreadRecordingTestFixture {
        @Test
        void test() {
            threadOfTestMethod = Thread.currentThread();
            threadLocalValueInTestMethod = THREAD_LOCAL.get();
        }
    }
}
