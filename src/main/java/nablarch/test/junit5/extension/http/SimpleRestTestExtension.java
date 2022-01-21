package nablarch.test.junit5.extension.http;

import nablarch.core.util.annotation.Published;
import nablarch.test.core.http.SimpleRestTestSupport;
import nablarch.test.event.TestEventDispatcher;
import nablarch.test.junit5.extension.event.TestEventDispatcherExtension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.rules.TestRule;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link SimpleRestTestSupport} を JUnit 5 で使用するための Extension 実装。
 * @author Tanaka Tomoyuki
 */
@Published
public class SimpleRestTestExtension extends TestEventDispatcherExtension {
    @Override
    protected TestEventDispatcher createSupport(Object testInstance, ExtensionContext context) {
        return new SimpleRestTestSupport();
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        super.beforeEach(context);
        ((SimpleRestTestSupport) support).setUp();
    }

    @Override
    protected List<TestRule> resolveTestRules() {
        List<TestRule> testRules = new ArrayList<>(super.resolveTestRules());
        testRules.add(((SimpleRestTestSupport) support).testDescription);
        return testRules;
    }
}
