package nablarch.test.junit5.extension.http;

import nablarch.test.core.http.RestTestSupport;
import nablarch.test.core.http.SimpleRestTestSupport;
import nablarch.test.junit5.extension.event.TestEventDispatcherExtension;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * {@link RestTestSupport} を JUnit 5 で使用するための Extension 実装。
 * @author Tanaka Tomoyuki
 */
public class RestTestExtension extends SimpleRestTestExtension {
    private RestTestSupport support;

    @Override
    protected RestTestSupport createSupport(Object testInstance, ExtensionContext context) {
        support = new RestTestSupport();
        return support;
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        super.beforeEach(context);
        support.setUpDb();
    }
}
