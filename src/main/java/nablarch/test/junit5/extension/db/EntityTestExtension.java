package nablarch.test.junit5.extension.db;


import nablarch.test.core.db.EntityTestSupport;
import nablarch.test.junit5.extension.event.TestEventDispatcherExtension;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * {@link EntityTestSupport} を JUnit 5 で使用するための Extension 実装。
 * @author Tanaka Tomoyuki
 */
public class EntityTestExtension extends TestEventDispatcherExtension<EntityTestSupport> {
    @Override
    protected EntityTestSupport createSupport(Object testInstance, ExtensionContext context) {
        return new EntityTestSupport(testInstance.getClass());
    }
}
