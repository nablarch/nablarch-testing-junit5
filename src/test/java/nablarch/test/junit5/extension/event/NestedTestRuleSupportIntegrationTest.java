package nablarch.test.junit5.extension.event;

import nablarch.test.event.TestEventDispatcher;
import nablarch.test.junit5.extension.JupiterEngineRunner;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.rules.TestRule;
import org.junit.runners.model.Statement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * {@code @Nested} を持つテストクラスで、 {@link TestEventDispatcherExtension#resolveTestRules()} が
 * 返す {@link TestRule} が {@link TestEventDispatcherExtension#support} を参照する場合に、
 * ルールが参照する {@code support} とテスト本体が参照する {@code support} が食い違うことをテストする。
 * <p>
 * <b>これは「対応する」ためのテストではなく、「壊れる現状を固定する」ためのテストである。</b>
 * 原因は {@link TestEventDispatcherExtension#support} フィールドを 1 枠しか持たないことにある。
 * {@code @Nested} クラスを持つテストクラスでは Extension のインスタンスが外側クラスと入れ子クラスとで
 * 共有されるため、入れ子インスタンスの {@code postProcessTestInstance} が外側インスタンスの
 * {@code support} を上書きする。 {@link #resolveTestRules()} はこの上書き後の値（＝最後に生成された
 * インスタンスの {@code support}）を読むため、外側インスタンス自身が持つ {@code support}
 * （テストクラスにインジェクションされたフィールド。フィールドへの代入はスナップショットなので
 * 上書きの影響を受けない）とは別物になる。
 * </p>
 * <p>
 * 食い違いが表に出るのは、テスト本体が「自分が保持するインジェクション先フィールド」ではなく
 * 「外側インスタンスのインジェクション先フィールド」（{@code Fixture.this.support}）を参照する場合である。
 * {@code @Nested} クラスは外側クラスを継承しないため、フィールドをインスタンスごとに個別に宣言せず
 * 外側だけに宣言する構成（ベースクラスにフィールドを 1 つだけ持つ典型的な使い方）でこれが起きる。
 * </p>
 * <p>
 * このテストが失敗したときは、 {@code support} フィールドの持ち方に変化があったことを意味する。
 * その場合は、実際の挙動に合わせてこのテストと Javadoc（{@code id="limitation-nested"}）を書き換えること。
 * </p>
 * @author Ito Kiyohito
 */
class NestedTestRuleSupportIntegrationTest {

    private static final List<String> executionLog = Collections.synchronizedList(new ArrayList<>());

    @Test
    void Nestedを持つテストクラスでruleがsupportを参照すると外側と入れ子で混線することをテスト() {
        JupiterEngineRunner.ExecutionSummary summary = JupiterEngineRunner.run(Fixture.class);

        assertThat("ルールがsupportを参照していても、テスト自体は例外にならず成功する",
                summary.getSuccessfulTestCount(), is(2));
        assertThat("外側のテストでは、ruleが参照するsupportとテスト本体が参照するsupportが一致する",
                executionLog.subList(0, 2), is(Arrays.asList("outerTest-rule:1", "outer-test:1")));
        assertThat("入れ子のテストでは、ruleが参照するsupport(id=3。直前のpostProcessTestInstanceが上書きした値)と、"
                        + "テスト本体が参照するFixture.this.support(id=2。外側インスタンス自身のsupport)が食い違う",
                executionLog.subList(2, 4), is(Arrays.asList("innerTest-rule:3", "inner-test:2")));
    }

    /**
     * 外側に 1 件、入れ子に 1 件テストを持つ、実行対象のテストクラス。
     */
    @ExtendWith(SupportReferencingRuleExtension.class)
    static class Fixture {

        IdentifiedSupport support;

        @Test
        void outerTest() {
            executionLog.add("outer-test:" + support.id);
        }

        /**
         * {@code support} フィールドをあえて再宣言しない。 {@code @Nested} クラスは外側クラスを
         * 継承しないため、フィールドを外側だけに宣言する構成では、テスト本体は
         * {@code Fixture.this.support} で外側インスタンス自身の {@code support} を参照することになる。
         */
        @Nested
        class Inner {

            @Test
            void innerTest() {
                executionLog.add("inner-test:" + Fixture.this.support.id);
            }
        }
    }

    /**
     * インスタンスごとに一意な {@code id} を持つ、テストのための {@link TestEventDispatcher} の仮実装クラス。
     */
    static class IdentifiedSupport extends TestEventDispatcher {
        private static final AtomicInteger COUNTER = new AtomicInteger();
        final int id = COUNTER.incrementAndGet();
    }

    /**
     * {@link #resolveTestRules()} で、 {@link #support} を参照する {@link TestRule} を返す、
     * テストのための {@link TestEventDispatcherExtension} の仮実装クラス。
     */
    public static class SupportReferencingRuleExtension extends TestEventDispatcherExtension {

        @Override
        protected TestEventDispatcher createSupport(Object testInstance, ExtensionContext context) {
            return new IdentifiedSupport();
        }

        @Override
        protected List<TestRule> resolveTestRules() {
            IdentifiedSupport captured = (IdentifiedSupport) support;
            return Collections.singletonList((base, description) -> new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    executionLog.add(description.getMethodName() + "-rule:" + captured.id);
                    base.evaluate();
                }
            });
        }
    }
}
