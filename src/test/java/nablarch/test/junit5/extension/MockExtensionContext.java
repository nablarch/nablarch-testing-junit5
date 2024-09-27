package nablarch.test.junit5.extension;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExecutableInvoker;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstances;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * {@link ExtensionContext}のモック。
 */
public class MockExtensionContext implements ExtensionContext {
    private Class<?> testClass;
    private Method testMethod;

    /**
     * 適当なテストクラス、テストメソッドで初期化した{@link MockExtensionContext}を生成して返却する。
     * <p>
     * テストで{@link ExtensionContext}が必要になるが、特にコンテキストの内容がテストに影響を与えない場合に
     * 簡単にインスタンスを得るための手段として使用する。
     * </p>
     * @return 適当な設定で生成された{@link {@link MockExtensionContext}}
     */
    public static MockExtensionContext any() {
        try {
            return new MockExtensionContext(MockExtensionContext.class, MockExtensionContext.class.getMethod("any"));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 指定したテストクラス、テストメソッドを持つコンテキストを生成する。
     * @param testClass テストクラス
     * @param testMethod テストメソッド
     */
    public MockExtensionContext(Class<?> testClass, Method testMethod) {
        this.testClass = testClass;
        this.testMethod = testMethod;
    }

    @Override
    public Optional<ExtensionContext> getParent() {
        return Optional.empty();
    }

    @Override
    public ExtensionContext getRoot() {
        return null;
    }

    @Override
    public String getUniqueId() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public Set<String> getTags() {
        return null;
    }

    @Override
    public Optional<AnnotatedElement> getElement() {
        return Optional.empty();
    }

    @Override
    public Optional<Class<?>> getTestClass() {
        return Optional.of(testClass);
    }

    @Override
    public Optional<TestInstance.Lifecycle> getTestInstanceLifecycle() {
        return Optional.empty();
    }

    @Override
    public Optional<Object> getTestInstance() {
        return Optional.empty();
    }

    @Override
    public Optional<TestInstances> getTestInstances() {
        return Optional.empty();
    }

    @Override
    public Optional<Method> getTestMethod() {
        return Optional.of(testMethod);
    }

    @Override
    public Optional<Throwable> getExecutionException() {
        return Optional.empty();
    }

    @Override
    public Optional<String> getConfigurationParameter(String key) {
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> getConfigurationParameter(String key, Function<String, T> transformer) {
        return Optional.empty();
    }

    @Override
    public void publishReportEntry(Map<String, String> map) {

    }

    @Override
    public Store getStore(Namespace namespace) {
        return null;
    }

    @Override
    public ExecutionMode getExecutionMode() {
        return null;
    }

    @Override
    public ExecutableInvoker getExecutableInvoker() {
        return null;
    }
}
