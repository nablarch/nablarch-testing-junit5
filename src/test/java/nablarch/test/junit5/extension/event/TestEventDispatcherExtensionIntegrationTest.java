package nablarch.test.junit5.extension.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * {@link TestEventDispatcherExtension} を実際に JUnit5 環境で動かすテスト。
 * @author Tanaka Tomoyuki
 */
@ExtendWith(MockTestEventDispatcherExtension.class)
public class TestEventDispatcherExtensionIntegrationTest {
    MockTestEventDispatcher support;

    @Test
    void testNameのルールが正しくエミュレートできていることをテスト() {
        assertThat(support.testName.getMethodName(), is("testNameのルールが正しくエミュレートできていることをテスト"));
    }
}
