package nablarch.test.junit5.extension.event;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * {@link TestEventDispatcherExtension}のライフサイクルメソッドに関する結合テスト。
 * @author Tanaka Tomoyuki
 */
public class TestEventDispatcherExtensionLifecycleMethodTest {
    @RegisterExtension
    static MockTestEventDispatcherExtension extension = new MockTestEventDispatcherExtension();

    MockTestEventDispatcher support;

    @BeforeAll
    static void beforeAll() {
        assertThat("beforeEach at beforeAll", extension.isBeforeEachInvoked(), is(false));
        assertThat("afterEach at beforeAll", extension.isAfterEachInvoked(), is(false));
    }

    @BeforeEach
    void beforeEach() {
        assertThat("beforeEach at beforeEach",extension.isBeforeEachInvoked(), is(true));
        assertThat("afterEach at beforeEach", extension.isAfterEachInvoked(), is(false));
        assertThat("testName at beforeEach", support.testName.getMethodName(), is("test"));
    }

    @Test
    void test() {
        assertThat("beforeEach at test",extension.isBeforeEachInvoked(), is(true));
        assertThat("afterEach at test", extension.isAfterEachInvoked(), is(false));
    }

    @AfterEach
    void afterEach() {
        assertThat("beforeEach at afterEach",extension.isBeforeEachInvoked(), is(true));
        assertThat("afterEach at afterEach", extension.isAfterEachInvoked(), is(false));
    }

    @AfterAll
    static void afterAll() {
        assertThat("beforeEach at afterAll",extension.isBeforeEachInvoked(), is(true));
        assertThat("afterEach at afterAll", extension.isAfterEachInvoked(), is(true));
    }
}
