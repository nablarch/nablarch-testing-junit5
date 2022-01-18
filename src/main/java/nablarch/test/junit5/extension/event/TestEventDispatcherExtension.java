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

        Predicate<Field> condition = this::isInjectionTarget;
        List<Field> fields = ReflectionUtils.findFields(testInstance.getClass(), condition, ReflectionUtils.HierarchyTraversalMode.BOTTOM_UP);

        for (Field field : fields) {
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
     * 指定されたフィールドが、サポートクラスのインスタンスをインジェクションする対象となるか判定する。
     * @param field 判定対象のフィールド
     * @return インジェクション対象である場合は {@code ture}
     */
    private boolean isInjectionTarget(Field field) {
        return !Modifier.isPrivate(field.getModifiers())
                && field.getType().isAssignableFrom(TestEventDispatcher.class);
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
