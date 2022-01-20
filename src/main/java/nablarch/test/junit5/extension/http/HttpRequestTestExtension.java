package nablarch.test.junit5.extension.http;

import nablarch.test.core.http.HttpRequestTestSupport;
import nablarch.test.event.TestEventDispatcher;
import nablarch.test.junit5.extension.event.TestEventDispatcherExtension;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * {@link HttpRequestTestSupport} を JUnit 5 で使用するための Extension 実装。
 * @author Tanaka Tomoyuki
 */
public class HttpRequestTestExtension extends TestEventDispatcherExtension {
    @Override
    protected TestEventDispatcher createSupport(Object testInstance, ExtensionContext context) {
        return new HttpRequestTestSupport(testInstance.getClass());
    }
}
