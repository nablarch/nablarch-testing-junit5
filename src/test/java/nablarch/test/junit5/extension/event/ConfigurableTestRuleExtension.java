package nablarch.test.junit5.extension.event;

import nablarch.test.event.TestEventDispatcher;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.rules.TestRule;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * クラス変数に設定された {@link TestRule} を {@link TestEventDispatcherExtension#resolveTestRules()} 経由で
 * 適用する、テストのための {@link TestEventDispatcherExtension} の仮実装クラス。
 * <p>
 * 適用するルールは {@link #setTestRules(TestRule...)} で設定する。
 * 検証したい振る舞いごとに小さなテストクラスを用意して
 * {@link nablarch.test.junit5.extension.JupiterEngineRunner} で実行する使い方を想定しているため、
 * ルールの受け渡しをクラス変数で行い、テストクラス側には何も持たせないようにしている。
 * </p>
 * <p>
 * 適用するルールはクラス変数であり、この Extension を使う全てのテストクラスで共有される。
 * したがって {@code junit.jupiter.execution.parallel.enabled} を有効にすると、
 * テストが互いのルールを壊し合う。
 * 現在は surefire にも {@code junit-platform.properties} にも並列実行の設定が無いため直列に実行される。
 * </p>
 * @author Ito Kiyohito
 */
public class ConfigurableTestRuleExtension extends TestEventDispatcherExtension {

    /**
     * 適用する {@link TestRule} のリスト。
     */
    private static List<TestRule> testRules = Collections.emptyList();

    /**
     * 適用する {@link TestRule} を設定する。
     * @param testRules 適用する {@link TestRule}
     */
    public static void setTestRules(TestRule... testRules) {
        ConfigurableTestRuleExtension.testRules = Arrays.asList(testRules);
    }

    /**
     * 適用する {@link TestRule} の設定を解除する。
     */
    public static void clearTestRules() {
        testRules = Collections.emptyList();
    }

    @Override
    protected TestEventDispatcher createSupport(Object testInstance, ExtensionContext context) {
        return new MockTestEventDispatcher();
    }

    @Override
    protected List<TestRule> resolveTestRules() {
        return testRules;
    }
}
