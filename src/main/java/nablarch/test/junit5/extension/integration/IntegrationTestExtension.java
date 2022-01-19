package nablarch.test.junit5.extension.integration;

import nablarch.test.core.integration.IntegrationTestSupport;
import nablarch.test.junit5.extension.event.TestEventDispatcherExtension;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * {@link IntegrationTestSupport} を JUnit 5 で使用するための Extension 実装。
 * @author Tanaka Tomoyuki
 */
public class IntegrationTestExtension extends TestEventDispatcherExtension<IntegrationTestSupport> {
    @Override
    protected IntegrationTestSupport createSupport(Object testInstance, ExtensionContext context) {
        return new IntegrationTestSupport(testInstance.getClass());
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        super.beforeEach(context);
        support.setUpDbBeforeTestMethod();
    }
}
