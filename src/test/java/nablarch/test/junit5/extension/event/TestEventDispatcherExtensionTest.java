package nablarch.test.junit5.extension.event;

import mockit.Expectations;
import mockit.Mocked;
import mockit.Verifications;
import nablarch.core.repository.SystemRepository;
import nablarch.test.RepositoryInitializer;
import nablarch.test.core.batch.BatchRequestTestSupport;
import nablarch.test.event.TestEventDispatcher;
import nablarch.test.event.TestEventListener;
import nablarch.test.junit5.extension.NablarchTest;
import org.apache.activemq.broker.TransportStatusDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor.Invocation;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
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
    public BatchRequestTestSupport batchRequestTestSupport;
    protected TestEventDispatcher protectedDispatcher;
    TestEventDispatcher packagePrivateDispatcher;
    private TestEventDispatcher privateDispatcher;

    @Mocked
    Invocation<Void> mockInvocation;
    @Mocked
    ExtensionContext mockExtensionContext;

    @BeforeEach
    void beforeAll() {
        RepositoryInitializer.recreateRepository("unit-test.xml");
    }

    @Test
    void 実際に生成されたインスタンスと互換性があるフィールドは可視性に関係なくインスタンスがインジェクションされることをテスト() throws Exception {
        sut.postProcessTestInstance(this, null);

        assertThat(publicDispatcher, is(instanceOf(MockTestEventDispatcher.class)));
        assertThat(mockTestEventDispatcher, is(instanceOf(MockTestEventDispatcher.class)));
        assertThat(protectedDispatcher, is(instanceOf(MockTestEventDispatcher.class)));
        assertThat(packagePrivateDispatcher, is(instanceOf(MockTestEventDispatcher.class)));
        assertThat(privateDispatcher, is(instanceOf(MockTestEventDispatcher.class)));

        assertThat(batchRequestTestSupport, is(nullValue()));
    }

    @Test
    void インジェクション対象のフィールドがnullでない場合はエラーになることをテスト() {
        this.mockTestEventDispatcher = new MockTestEventDispatcher();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> sut.postProcessTestInstance(this, null));

        assertThat(exception.getMessage(), is("The mockTestEventDispatcher field of TestEventDispatcherExtensionTest is already set some value."));
    }

    @Test
    void beforeAllでTestEventListenerのbeforeTestSuiteとbeforeTestClassメソッドが実行されることをテスト() throws Exception {
        /*
         * TestEventDispatcher の first クラスフィールドを true に設定する。
         * このクラスフィールドは、 beforeTestSuite() を一度実行すると false に設定され、
         * 以後 true になることはない。
         * そして、このクラスフィールドは外部からアクセス不可能な static 変数で定義されている。
         * このため、他のテストで一度でも first が false になっていると、本テストが正常に実施できない。
         * この問題を回避するため、リフレクションで強制的に true にしてからテストするようにしている。
         */
        final Field first = TestEventDispatcher.class.getDeclaredField("first");
        first.setAccessible(true);
        first.set(null, true);

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
            mockExtensionContext.getRequiredTestMethod();
            result = TestEventDispatcherExtensionTest.class.getDeclaredMethod("testForMock");

            mockExtensionContext.getRequiredTestClass();
            result = TestEventDispatcherExtensionTest.class;
        }};

        sut.postProcessTestInstance(this, null);
        sut.interceptTestMethod(mockInvocation, null, mockExtensionContext);

        assertThat(publicDispatcher.testName.getMethodName(), is("testForMock"));

        new Verifications() {{
            mockInvocation.proceed(); times = 1;
        }};
    }

    @Test
    void findAnnotationのテスト_指定したアノテーションが見つかる場合() {
        @NablarchTest
        class TemporaryClass {}

        NablarchTest annotation = sut.findAnnotation(new TemporaryClass(), NablarchTest.class);

        assertThat(annotation, is(instanceOf(NablarchTest.class)));
    }

    @Test
    void findAnnotationのテスト_指定したアノテーションが見つからない場合はnullを返す() {
        class TemporaryClass {}

        NablarchTest annotation = sut.findAnnotation(new TemporaryClass(), NablarchTest.class);

        assertThat(annotation, is(nullValue()));
    }

    @Test
    void emulateRuleでJUnit4のRuleをエミュレートできることをテスト(
            @Mocked Invocation<Void> mockInvocation,
            @Mocked ReflectiveInvocationContext<Method> mockInvocationContext,
            @Mocked ExtensionContext mockExtensionContext) throws Throwable {

        Method mockTestMethod = TestEventDispatcherExtensionTest.class.getDeclaredMethod("testForMock");
        new Expectations() {{
            mockExtensionContext.getRequiredTestClass(); result = TestEventDispatcherExtensionTest.class;
            mockExtensionContext.getRequiredTestMethod(); result = mockTestMethod;
        }};

        MockRule mockRule = new MockRule();

        sut.emulateRule(mockRule, mockInvocation, mockInvocationContext, mockExtensionContext);

        assertThat(mockRule.description.getTestClass(), is(equalTo(mockExtensionContext.getRequiredTestClass())));
        assertThat(mockRule.description.getDisplayName(),
                is(String.format("%s(%s)",
                        mockExtensionContext.getRequiredTestMethod().getName(),
                        mockExtensionContext.getRequiredTestClass().getName())));
        assertThat(mockRule.description.getMethodName(), is(mockExtensionContext.getRequiredTestMethod().getName()));

        new Verifications() {{
            mockInvocation.proceed(); times = 1;
        }};
    }

    /**
     * Method オブジェクトを取得するために定義した空メソッド。
     * 実行して使用することはない。
     */
    private void testForMock() {
        // noop
    }

    public static class MockRule implements TestRule {
        Description description;

        @Override
        public Statement apply(Statement base, Description description) {
            this.description = description;
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    base.evaluate();
                }
            };
        }
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