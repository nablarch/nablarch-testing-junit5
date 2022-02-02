package nablarch.test.junit5.extension.http;

import nablarch.core.util.annotation.Published;
import nablarch.test.core.http.RestTestSupport;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * {@link RestTestSupport} を JUnit 5 で使用するための Extension 実装。
 * @author Tanaka Tomoyuki
 */
@Published
public class RestTestExtension extends SimpleRestTestExtension {

    @Override
    protected RestTestSupport createSupport(Object testInstance, ExtensionContext context) {
        return new RestTestSupport(testInstance.getClass());
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        super.beforeEach(context);
        ((RestTestSupport) support).setUpDb();
    }
}
