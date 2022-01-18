package nablarch.test.junit5.extension.event;

import nablarch.test.core.db.DbAccessTestSupport;
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
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
 * @param <S> インスタンスを生成するサポートクラスの型
 * @author Tanaka Tomoyuki
 */
public abstract class TestEventDispatcherExtension<S extends TestEventDispatcher> implements
        TestInstancePostProcessor,
        BeforeAllCallback,
        BeforeEachCallback,
        AfterAllCallback,
        AfterEachCallback,
        InvocationInterceptor {

    /**
     * Extension が生成しテストクラスにインジェクションする、サポートクラスのインスタンス。
     */
    protected S support;

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
    protected abstract S createSupport(final Object testInstance, ExtensionContext context);

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
        Class<?> testClass = extensionContext.getRequiredTestClass();
        String testName = extensionContext.getDisplayName();
        Description description = Description.createTestDescription(testClass, testName);

        Statement statement = support.testName.apply(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                invocation.proceed();
            }
        }, description);

        statement.evaluate();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        support.dispatchEventOfAfterTestMethod();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        TestEventDispatcher.dispatchEventOfAfterTestClass();
    }
}
