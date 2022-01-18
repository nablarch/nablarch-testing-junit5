package nablarch.test.junit5.extension.event;

import nablarch.test.junit5.extension.event.TestEventDispatcherExtensionTest.MockTestEventDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * {@link TestEventDispatcherExtension}を実際に {@link ExtendWith}で使用できることのテスト。
 * @author Tanaka Tomoyuki
 */
@ExtendWith(TestEventDispatcherExtensionTest.MockTestEventDispatcherExtension.class)
public class TestEventDispatcherExtensionIntegrationTest {

    MockTestEventDispatcher support;

    @Test
    void test() {
        assertThat(support, is(instanceOf(MockTestEventDispatcher.class)));
    }
}
