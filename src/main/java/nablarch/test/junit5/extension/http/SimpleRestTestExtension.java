package nablarch.test.junit5.extension.http;

import nablarch.test.core.http.SimpleRestTestSupport;
import nablarch.test.junit5.extension.event.TestEventDispatcherExtension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

import java.lang.reflect.Method;

/**
 * {@link SimpleRestTestSupport} を JUnit 5 で使用するための Extension 実装。
 * @author Tanaka Tomoyuki
 */
public class SimpleRestTestExtension extends TestEventDispatcherExtension<SimpleRestTestSupport> {
    @Override
    protected SimpleRestTestSupport createSupport(Object testInstance, ExtensionContext context) {
        return new SimpleRestTestSupport();
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        super.beforeEach(context);
        support.setUp();
    }

    @Override
    public void interceptTestMethod(Invocation<Void> invocation,
                                    ReflectiveInvocationContext<Method> invocationContext,
                                    ExtensionContext extensionContext) throws Throwable {
        emulateRule(support.testDescription, invocation, invocationContext, extensionContext);
    }
}
