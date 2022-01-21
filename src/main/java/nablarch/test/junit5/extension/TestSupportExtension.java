package nablarch.test.junit5.extension;

import nablarch.core.util.annotation.Published;
import nablarch.test.TestSupport;
import nablarch.test.event.TestEventDispatcher;
import nablarch.test.junit5.extension.event.TestEventDispatcherExtension;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * {@link TestSupport} を JUnit 5 で使用するための Extension 実装。
 * @author Tanaka Tomoyuki
 */
@Published
public class TestSupportExtension extends TestEventDispatcherExtension {
    @Override
    protected TestEventDispatcher createSupport(Object testInstance, ExtensionContext context) {
        return new TestSupport(testInstance.getClass());
    }
}
