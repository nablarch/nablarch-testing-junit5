package nablarch.test.junit5.extension.http;

import nablarch.test.core.http.BasicHttpRequestTestTemplate;
import nablarch.test.junit5.extension.event.TestEventDispatcherExtension;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * {@link BasicHttpRequestTestTemplate} を JUnit 5 で使用するための Extension 実装。
 * @author Tanaka Tomoyuki
 */
public class BasicHttpRequestTestExtension extends TestEventDispatcherExtension<BasicHttpRequestTestTemplate> {
    @Override
    protected BasicHttpRequestTestTemplate createSupport(Object testInstance, ExtensionContext context) {
        BasicHttpRequestTest annotation = findAnnotation(testInstance, BasicHttpRequestTest.class);
        if (annotation == null) {
            String message = String.format("%s is not annotated by %s.",
                    testInstance.getClass().getName(),
                    BasicHttpRequestTest.class.getSimpleName());
            throw new IllegalStateException(message);
        }

        return new BasicHttpRequestTestTemplate(testInstance.getClass()) {
            @Override
            protected String getBaseUri() {
                return annotation.baseUri();
            }
        };
    }
}
