package nablarch.test.junit5.extension.event;

import mockit.Expectations;
import mockit.Mocked;
import mockit.Verifications;
import nablarch.core.repository.SystemRepository;
import nablarch.test.RepositoryInitializer;
import nablarch.test.event.TestEventDispatcher;
import nablarch.test.event.TestEventListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor.Invocation;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThrows;

/**
 * {@link TestEventDispatcherExtension}の単体テスト。
 * @author Tanaka Tomoyuki
 */
class TestEventDispatcherExtensionTest {
    final MockTestEventDispatcherExtension sut = new MockTestEventDispatcherExtension();

    public TestEventDispatcher publicDispatcher;
    public MockTestEventDispatcher mockTestEventDispatcher;
    protected TestEventDispatcher protectedDispatcher;
    TestEventDispatcher packagePrivateDispatcher;
    private TestEventDispatcher privateDispatcher;

    @Mocked
    Invocation<Void> mockInvocation;
    @Mocked
    ExtensionContext mockExtensionContext;

    @Test
    void 実際に生成されたインスタンスと互換性があり_publicなフィールドにインスタンスがインジェクションされることをテスト() throws Exception {
        sut.postProcessTestInstance(this, null);

        assertThat(publicDispatcher, is(instanceOf(MockTestEventDispatcher.class)));
        assertThat(mockTestEventDispatcher, is(instanceOf(MockTestEventDispatcher.class)));
        assertThat(protectedDispatcher, is(nullValue()));
        assertThat(packagePrivateDispatcher, is(nullValue()));
        assertThat(privateDispatcher, is(nullValue()));
    }

    @Test
    void インジェクション対象のフィールドがnullでない場合はエラーになることをテスト() {
        this.mockTestEventDispatcher = new MockTestEventDispatcher();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> sut.postProcessTestInstance(this, null));

        assertThat(exception.getMessage(), is("The mockTestEventDispatcher field of TestEventDispatcherExtensionTest is already set some value."));
    }

    @Test
    void beforeAllでTestEventListenerのbeforeTestSuiteとbeforeTestClassメソッドが実行されることをテスト() {
        RepositoryInitializer.initializeDefaultRepository();

        MockTestEventListener listener = SystemRepository.get("mockTestEventListener");

        assertThat(listener.beforeTestSuiteInvokedCount, is(0));
        assertThat(listener.beforeTestClassInvokedCount, is(0));

        sut.beforeAll(null);
        assertThat(listener.beforeTestSuiteInvokedCount, is(1));
        assertThat(listener.beforeTestClassInvokedCount, is(1));

        sut.beforeAll(null);
        assertThat(listener.beforeTestSuiteInvokedCount, is(1));
        assertThat(listener.beforeTestClassInvokedCount, is(2));
    }

    @Test
    void beforeEachでTestEventListenerのbeforeTestMethodメソッドが実行されることをテスト() throws Exception {
        sut.postProcessTestInstance(this, null);

        MockTestEventListener listener = SystemRepository.get("mockTestEventListener");

        assertThat(listener.beforeTestMethodInvokedCount, is(0));

        sut.beforeEach(null);

        assertThat(listener.beforeTestMethodInvokedCount, is(1));
    }

    @Test
    void afterEachでTestEventListenerのafterTestMethodメソッドが実行されることをテスト() throws Exception {
        sut.postProcessTestInstance(this, null);

        MockTestEventListener listener = SystemRepository.get("mockTestEventListener");

        assertThat(listener.afterTestMethodInvokedCount, is(0));

        sut.afterEach(null);

        assertThat(listener.afterTestMethodInvokedCount, is(1));
    }

    @Test
    void afterAllでTestEventListenerのafterTestClassメソッドが実行されることをテスト() throws Exception {
        sut.postProcessTestInstance(this, null);

        MockTestEventListener listener = SystemRepository.get("mockTestEventListener");

        assertThat(listener.afterTestClassInvokedCount, is(0));

        sut.afterAll(null);

        assertThat(listener.afterTestClassInvokedCount, is(1));
    }

    @Test
    void interceptTestMethodを実行すると_TestNameのRuleがエミュレートされ_本来のテストもコールされることをテスト() throws Throwable {
        new Expectations() {{
            mockExtensionContext.getDisplayName(); result = "テスト表示名";
            mockExtensionContext.getRequiredTestClass(); result = TestEventDispatcherExtensionTest.class;
        }};

        sut.postProcessTestInstance(this, null);
        sut.interceptTestMethod(mockInvocation, null, mockExtensionContext);

        assertThat(publicDispatcher.testName.getMethodName(), is("テスト表示名"));

        new Verifications() {{
            mockInvocation.proceed(); times = 1;
        }};
    }

    public static class MockTestEventListener implements TestEventListener {
        int beforeTestSuiteInvokedCount;
        int beforeTestClassInvokedCount;
        int beforeTestMethodInvokedCount;
        int afterTestMethodInvokedCount;
        int afterTestClassInvokedCount;

        @Override
        public void beforeTestSuite() {
            beforeTestSuiteInvokedCount++;
        }

        @Override
        public void beforeTestClass() {
            beforeTestClassInvokedCount++;
        }

        @Override
        public void beforeTestMethod() {
            beforeTestMethodInvokedCount++;
        }

        @Override
        public void afterTestMethod() {
            afterTestMethodInvokedCount++;
        }

        @Override
        public void afterTestClass() {
            afterTestClassInvokedCount++;
        }
    }
}