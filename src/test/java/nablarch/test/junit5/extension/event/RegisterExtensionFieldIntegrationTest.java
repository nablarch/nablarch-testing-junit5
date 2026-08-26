package nablarch.test.junit5.extension.event;

import nablarch.test.event.TestEventDispatcher;
import nablarch.test.junit5.extension.JupiterEngineRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

/**
 * {@code @RegisterExtension} で Extension クラスを適用したときに、
 * フィールドを {@code static} で宣言するかインスタンスフィールドで宣言するかによって
 * 振る舞いが変わることをテストする。
 * <p>
 * インスタンスフィールドで宣言すると、 JUnit 5 はテストインスタンスの生成より後に Extension を登録するため、
 * クラス単位の拡張ポイント({@code beforeAll} / {@code afterAll})も、
 * テストインスタンスへのインジェクション({@code postProcessTestInstance})も呼び出さない。
 * その結果、 {@code beforeEach} は {@code support} が {@code null} のまま実行され、
 * {@link NullPointerException} でテストが失敗する。
 * </p>
 * <p>
 * 各ライフサイクルメソッドは、スーパクラスの処理を呼ぶ前に実行ログへ記録する。
 * これにより、スーパクラスの処理が失敗した場合でも「そのメソッドが呼ばれたかどうか」を区別できる。
 * {@code static} フィールドで宣言した場合と並べて、差が宣言方法だけであることを示す。
 * </p>
 * @author Ito Kiyohito
 */
public class RegisterExtensionFieldIntegrationTest {

    /**
     * 実行対象のテストクラスと Extension が書き込む実行ログ。
     */
    static final List<String> executionLog = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUpExecutionLog() {
        executionLog.clear();
    }

    @Test
    void staticフィールドで宣言するとbeforeAllとafterAllが実行されることをテスト() {
        JupiterEngineRunner.ExecutionSummary summary = JupiterEngineRunner.run(StaticFieldTestFixture.class);

        assertThat(summary.getSuccessfulTestCount(), is(1));
        assertThat(executionLog,
                is(Arrays.asList("beforeAll", "beforeEach", "test", "afterEach", "afterAll")));
    }

    @Test
    void インスタンスフィールドで宣言するとbeforeAllとafterAllが実行されずExtensionが正しく動作しないことをテスト() {
        JupiterEngineRunner.ExecutionSummary summary = JupiterEngineRunner.run(InstanceFieldTestFixture.class);

        assertThat("beforeAll と afterAll は実行されない",
                executionLog, is(Arrays.asList("beforeEach", "afterEach")));
        assertThat("サポートクラスのインジェクションも行われないため、テスト本体まで到達しない",
                summary.getSuccessfulTestCount(), is(0));
        assertThat("support が null のまま beforeEach が実行される",
                summary.getOnlyFailure(), is(instanceOf(NullPointerException.class)));
    }

    /**
     * ライフサイクルメソッドの実行を、スーパクラスの処理を呼ぶ前に {@link #executionLog} へ記録する Extension。
     */
    static class LifecycleRecordingExtension extends TestEventDispatcherExtension {
        @Override
        protected TestEventDispatcher createSupport(Object testInstance, ExtensionContext context) {
            return new MockTestEventDispatcher();
        }

        @Override
        public void beforeAll(ExtensionContext context) {
            executionLog.add("beforeAll");
            super.beforeAll(context);
        }

        @Override
        public void beforeEach(ExtensionContext context) throws Exception {
            executionLog.add("beforeEach");
            super.beforeEach(context);
        }

        @Override
        public void afterEach(ExtensionContext context) throws Exception {
            executionLog.add("afterEach");
            super.afterEach(context);
        }

        @Override
        public void afterAll(ExtensionContext context) {
            executionLog.add("afterAll");
            super.afterAll(context);
        }
    }

    /**
     * Extension を {@code static} フィールドで宣言した、実行対象のテストクラス。
     */
    static class StaticFieldTestFixture {
        @RegisterExtension
        static LifecycleRecordingExtension extension = new LifecycleRecordingExtension();

        @Test
        void test() {
            executionLog.add("test");
        }
    }

    /**
     * Extension をインスタンスフィールドで宣言した、実行対象のテストクラス。
     */
    static class InstanceFieldTestFixture {
        @RegisterExtension
        LifecycleRecordingExtension extension = new LifecycleRecordingExtension();

        @Test
        void test() {
            executionLog.add("test");
        }
    }
}
