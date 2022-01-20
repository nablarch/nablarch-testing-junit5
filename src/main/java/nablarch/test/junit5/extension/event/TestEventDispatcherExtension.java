package nablarch.test.junit5.extension.event;

import nablarch.test.event.TestEventDispatcher;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
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
public abstract class TestEventDispatcherExtension implements
        TestInstancePostProcessor,
        BeforeAllCallback,
        BeforeEachCallback,
        AfterAllCallback,
        AfterEachCallback,
        InvocationInterceptor {

    /**
     * Extension が生成しテストクラスにインジェクションする、サポートクラスのインスタンス。
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
                    String.format("The %s field of %s is already set some value.", field.getName(), testInstance.getClass().getSimpleName());
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

    @Override
    public void beforeEach(ExtensionContext context) {
        support.dispatchEventOfBeforeTestMethod();
    }

    @Override
    public void interceptTestMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext, ExtensionContext extensionContext) throws Throwable {
        emulateRule(support.testName, invocation, invocationContext, extensionContext);
    }

    @Override
    public void afterEach(ExtensionContext context) {
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

    /**
     * JUnit4 の {@link TestRule} の動作を JUnit 5 環境上で再現する。
     * <p>
     * 引数に渡す {@link Invocation}, {@link ReflectiveInvocationContext}, {@link ExtensionContext} は、
     * {@link #interceptTestMethod(Invocation, ReflectiveInvocationContext, ExtensionContext)} で
     * 受け取った引数をそのまま連携する。
     * </p>
     *
     * @param testRule 再現対象の {@link TestRule}
     * @param invocation {@link Invocation}
     * @param invocationContext {@link ReflectiveInvocationContext}
     * @param extensionContext {@link ExtensionContext}
     */
    protected void emulateRule(TestRule testRule,
                               Invocation<Void> invocation,
                               ReflectiveInvocationContext<Method> invocationContext,
                               ExtensionContext extensionContext) throws Throwable {
        Class<?> testClass = extensionContext.getRequiredTestClass();
        String testName = extensionContext.getRequiredTestMethod().getName();
        Description description = Description.createTestDescription(testClass, testName);

        Statement statement = testRule.apply(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                invocation.proceed();
            }
        }, description);

        statement.evaluate();
    }
}
