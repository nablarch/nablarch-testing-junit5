package nablarch.test.junit5.extension.event;

import nablarch.core.util.annotation.Published;
import nablarch.test.event.TestEventDispatcher;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.junit.platform.commons.util.ReflectionUtils;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * NTF の JUnit 5 用 Extension の基底となる抽象クラス。
 * <p>
 * このクラスは、 {@link TestEventDispatcher} が提供する JUnit 4 用の拡張機能を、
 * JUnit 5 の Extension の仕組みで再現する。
 * </p>
 * <p>
 * 各 Extension はこのクラスを継承して作成することで、共通する部分の処理を省略できる。
 * </p>
 * @author Tanaka Tomoyuki
 */
@Published(tag = "architect")
public abstract class TestEventDispatcherExtension implements
        TestInstancePostProcessor,
        BeforeAllCallback,
        AfterAllCallback,
        InvocationInterceptor {

    /**
     * Extension が生成しテストクラスにインジェクションする、サポートクラスのインスタンス。
     * <p>
     * このフィールドは、 {@link #postProcessTestInstance(Object, ExtensionContext)} が実行されたときに初期化される。
     * 設定される値は、 {@link #createSupport(Object, ExtensionContext)} が返却したインスタンスが使用される。
     * </p>
     */
    protected TestEventDispatcher support;

    @Override
    public void postProcessTestInstance(final Object testInstance, ExtensionContext context) throws Exception {
        support = createSupport(testInstance, context);

        Predicate<Field> isInjectionTarget = buildInjectionTargetCondition(support.getClass());
        List<Field> fields = ReflectionUtils.findFields(testInstance.getClass(),
                isInjectionTarget,
                ReflectionUtils.HierarchyTraversalMode.BOTTOM_UP);

        for (Field field : fields) {
            field.setAccessible(true);
            Object value = field.get(testInstance);
            if (value != null) {
                String message =
                        String.format("The %s field of %s is already set some value.",
                                field.getName(),
                                testInstance.getClass().getSimpleName());
                throw new IllegalStateException(message);
            }

            field.set(testInstance, support);
        }
    }

    /**
     * テストインスタンスにインジェクションするサポートクラスのインスタンスを生成する。
     * @param testInstance テストインスタンス
     * @param context コンテキスト
     * @return サポートクラスのインスタンス
     */
    protected abstract TestEventDispatcher createSupport(final Object testInstance, ExtensionContext context);

    /**
     * 指定されたフィールドが、サポートクラスのインスタンスをインジェクションする対象となるか判定するための
     * {@link Predicate} を生成する。
     * @param supportClass 生成されたサポートクラスの {@link Class} オブジェクト
     * @return インジェクション対象の判定を行うための {@link Predicate}
     */
    private Predicate<Field> buildInjectionTargetCondition(Class<? extends TestEventDispatcher> supportClass) {
        return field -> field.getType().isAssignableFrom(supportClass);
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        TestEventDispatcher.dispatchEventOfBeforeTestClassAndBeforeSuit();
    }

    /**
     * テストメソッドの前処理を実行する。
     * @param context コンテキスト
     * @throws Exception 例外がスローされた場合
     */
    protected void beforeEach(ExtensionContext context) throws Exception {
        support.dispatchEventOfBeforeTestMethod();
    }

    @Override
    public void interceptTestMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext, ExtensionContext extensionContext) throws Throwable {
        Statement base = new Statement() {
            @Override
            public void evaluate() throws Throwable {
                beforeEach(extensionContext);
                try {
                    invocation.proceed();
                } finally {
                    afterEach(extensionContext);
                }
            }
        };

        Description description = convert(extensionContext);

        List<TestRule> testRules = resolveTestRules();
        Statement statement = base;
        for (TestRule testRule : testRules) {
            statement = testRule.apply(statement, description);
        }

        statement.evaluate();
    }

    /**
     * {@link ExtensionContext}(JUnit5) の情報を {@link Description}(JUnit4) に詰め替える。
     * @param extensionContext {@link ExtensionContext}
     * @return {code extensionContext} の情報をもとに構築された {@link Description}
     */
    private Description convert(ExtensionContext extensionContext) {
        Class<?> testClass = extensionContext.getRequiredTestClass();
        String testName = extensionContext.getRequiredTestMethod().getName();
        return Description.createTestDescription(testClass, testName);
    }

    /**
     * テストに対して適用する JUnit 4 の {@link TestRule} のリストを取得する。
     * <p>
     * JUnit 4 時代に作成した独自のサポートクラスを移植する場合は、
     * このメソッドをオーバーライドしてサポートクラスで宣言したルールインスタンスを
     * リストにして返却するように実装する。<br>
     * オーバーライドした場合は、親クラスが返したリストに追加する形でルールを追加すること。
     * 以下に実装例を示す。
     * </p>
     * <pre>{@code
     * public List<TestRule> resolveTestRules() {
     *     // 親の resolveTestRules() が返したリストをベースにする
     *     List<TestRule> testRules = new ArrayList<>(super.resolveTestRules());
     *     // 独自の TestRule を追加する
     *     testRules.add(((YourSupport)support).yourTestRule);
     *     return testRules;
     * }
     * }</pre>
     * @return テストに適用したい JUnit 4 の {@link TestRule} のリスト
     */
    protected List<TestRule> resolveTestRules() {
        return Collections.singletonList(support.testName);
    }

    /**
     * テストメソッドの後処理を実行する。
     * @param context コンテキスト
     * @throws Exception 例外がスローされた場合
     */
    protected void afterEach(ExtensionContext context) throws Exception {
        support.dispatchEventOfAfterTestMethod();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        TestEventDispatcher.dispatchEventOfAfterTestClass();
    }

    /**
     * 指定されたテストインスタンスのクラスに設定されたアノテーションを取得する。
     * @param testInstance テストインスタンス(null不可)
     * @param annotationClass 取得するアノテーションの型
     * @param <A> アノテーションの型
     * @return テストクラスに設定されたアノテーション
     */
    protected <A extends Annotation> A findAnnotation(Object testInstance, Class<A> annotationClass) {
        return testInstance.getClass().getAnnotation(annotationClass);
    }
}
