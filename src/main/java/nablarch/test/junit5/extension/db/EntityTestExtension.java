package nablarch.test.junit5.extension.db;


import nablarch.core.util.annotation.Published;
import nablarch.test.core.db.EntityTestSupport;
import nablarch.test.event.TestEventDispatcher;
import nablarch.test.junit5.extension.event.TestEventDispatcherExtension;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * {@link EntityTestSupport} を JUnit 5 で使用するための Extension 実装。
 * @author Tanaka Tomoyuki
 */
@Published
public class EntityTestExtension extends TestEventDispatcherExtension {
    @Override
    protected TestEventDispatcher createSupport(Object testInstance, ExtensionContext context) {
        return new EntityTestSupport(testInstance.getClass());
    }
}
