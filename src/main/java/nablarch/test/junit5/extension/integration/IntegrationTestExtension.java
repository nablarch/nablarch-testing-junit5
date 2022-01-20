package nablarch.test.junit5.extension.integration;

import nablarch.core.util.annotation.Published;
import nablarch.test.core.integration.IntegrationTestSupport;
import nablarch.test.event.TestEventDispatcher;
import nablarch.test.junit5.extension.event.TestEventDispatcherExtension;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * {@link IntegrationTestSupport} を JUnit 5 で使用するための Extension 実装。
 * @author Tanaka Tomoyuki
 */
@Published
public class IntegrationTestExtension extends TestEventDispatcherExtension {
    @Override
    protected TestEventDispatcher createSupport(Object testInstance, ExtensionContext context) {
        return new IntegrationTestSupport(testInstance.getClass());
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        super.beforeEach(context);
        ((IntegrationTestSupport) support).setUpDbBeforeTestMethod();
    }
}
