package nablarch.test.junit5.extension.event;

import nablarch.test.event.TestEventDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.rules.RunRules;
import org.junit.rules.TestRule;
import org.junit.runners.model.Statement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * {@link TestEventDispatcherExtension#resolveTestRules()} で追加した JUnit 4 の {@link TestRule} が、
 * テストメソッドの実行を包む形で再現されることをテストする。
 * <p>
 * 検証は {@link #ルールがテスト本体を包んでいたことを検証する(TestInfo)} でまとめて行い、
 * 各テストメソッドの本体は自身が実行されたことを記録するだけである。
 * したがって、このクラスに {@link Test} を追加する場合は、
 * 必ず {@link RecordingSupport#recordTestMethodExecution()} を呼ぶこと。
 * 記録を行わないテストを追加すると、そのテスト自身が {@code @AfterEach} の検証で失敗する。
 * </p>
 * <p>
 * {@link ParameterizedTest} と {@link RepeatedTest} を置いているのは、 {@link Test} とは別経路
 * ({@code interceptTestTemplateMethod}) で実行されるテストもルールに包まれることを固定するためであり、
 * {@link Test} の繰り返しではない。
 * どちらも複数回実行するようにしているのは、テストメソッドの実行ごとに
 * サポートクラスと実行ログが作り直されること(ログが持ち越されないこと)も同時に押さえるためである。
 * </p>
 * <p>
 * このクラスに {@link Nested} なテストクラスは追加できない。
 * Extension のインスタンスが外側のクラスと入れ子のクラスで共有されるため、
 * {@link #support} フィールドが後から生成されたサポートクラスで上書きされ、検証が成立しなくなる。
 * </p>
 * @author Claude
 */
@ExtendWith(TestRuleEmulationIntegrationTest.RecordingSupportExtension.class)
public class TestRuleEmulationIntegrationTest {

    /**
     * 入れ子になる 2 本の {@link RecordingRule} を持つサポートクラス。
     * <p>
     * 実行ログはこのインスタンスが持つため、テストインスタンスごとに新しいログになる。
     * 別スレッドから記録が行われる実装を検知するのが目的であるため、
     * 記録用のコレクションは同期化しておき、書き込みが検証側から見えることを保証する。
     * </p>
     */
    static class RecordingSupport extends TestEventDispatcher {
        final List<String> executionLog = Collections.synchronizedList(new ArrayList<>());
        final Set<Thread> recordedThreads = Collections.synchronizedSet(new LinkedHashSet<>());

        public final RecordingRule innerRule = new RecordingRule(this::record, "inner");
        public final RecordingRule outerRule = new RecordingRule(this::record, "outer");

        /**
         * テスト本体が実行されたことを記録する。
         */
        void recordTestMethodExecution() {
            record("test");
        }

        /**
         * ラベルと、そのラベルを記録したスレッドを記録する。
         * @param label 記録するラベル
         */
        private void record(String label) {
            executionLog.add(label);
            recordedThreads.add(Thread.currentThread());
        }
    }

    /**
     * {@link RecordingSupport} が持つ {@link TestRule} を再現する Extension。
     */
    static class RecordingSupportExtension extends TestEventDispatcherExtension {

        @Override
        protected TestEventDispatcher createSupport(Object testInstance, ExtensionContext context) {
            return new RecordingSupport();
        }

        /**
         * {@inheritDoc}
         * <p>
         * JUnit 4 は {@link RunRules} でルールをリストの先頭から順に
         * それまで組み立てた {@link Statement} へ適用していくため、
         * リストの末尾にあるルールほど外側になる。
         * よって {@code inner} → {@code outer} の順に追加すると、 {@code outer} が最外側になる。
         * </p>
         */
        @Override
        protected List<TestRule> resolveTestRules() {
            RecordingSupport recordingSupport = (RecordingSupport) support;
            List<TestRule> testRules = new ArrayList<>(super.resolveTestRules());
            testRules.add(recordingSupport.innerRule);
            testRules.add(recordingSupport.outerRule);
            return testRules;
        }
    }

    RecordingSupport support;

    @Test
    void テストメソッドの実行がTestRuleに包まれていることをテスト() {
        support.recordTestMethodExecution();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void パラメータ化テストの実行がTestRuleに包まれていることをテスト(int parameter) {
        support.recordTestMethodExecution();
    }

    @RepeatedTest(2)
    void 繰り返しテストの実行がTestRuleに包まれていることをテスト() {
        support.recordTestMethodExecution();
    }

    @AfterEach
    void ルールがテスト本体を包んでいたことを検証する(TestInfo testInfo) {
        assertThat(testInfo.getDisplayName()
                        + " : TestRule の前処理と後処理は、テストメソッドの実行を挟む形で、入れ子を保って実行される",
                support.executionLog,
                is(Arrays.asList("outer-before", "inner-before", "test", "inner-after", "outer-after")));
        assertThat(testInfo.getDisplayName()
                        + " : TestRule とテストメソッドは、テストを実行しているスレッドで実行される",
                support.recordedThreads,
                is(Collections.singleton(Thread.currentThread())));
    }
}
