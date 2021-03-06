package nablarch.test.junit5.extension.event;

import nablarch.core.repository.SystemRepository;
import nablarch.test.RepositoryInitializer;
import nablarch.test.core.batch.BatchRequestTestSupport;
import nablarch.test.event.TestEventDispatcher;
import nablarch.test.event.TestEventListener;
import nablarch.test.junit5.extension.MockExtensionContext;
import nablarch.test.junit5.extension.NablarchTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

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
    public BatchRequestTestSupport batchRequestTestSupport;
    protected TestEventDispatcher protectedDispatcher;
    TestEventDispatcher packagePrivateDispatcher;
    private TestEventDispatcher privateDispatcher;

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

        sut.beforeEach(MockExtensionContext.any());

        assertThat(listener.beforeTestMethodInvokedCount, is(1));
    }

    @Test
    void beforeEachを実行すると_TestRuleのエミュレートが行われることをテスト() throws Throwable {
        sut.postProcessTestInstance(this, null);

        ExtensionContext mockContext = new MockExtensionContext(TestEventDispatcherExtensionTest.class,
                TestEventDispatcherExtensionTest.class.getDeclaredMethod("testForMock"));

        sut.beforeEach(mockContext);

        assertThat(publicDispatcher.testName.getMethodName(), is("testForMock"));
    }

    @Test
    void TestRuleエミュレート時に例外が発生した場合は_発生した例外を原因として持つ実行時例外がスローされること() throws Throwable {
        Exception exception = new Exception("test");

        TestEventDispatcherExtension sut = new MockTestEventDispatcherExtension() {
            @Override
            protected List<TestRule> resolveTestRules() {
                return Collections.singletonList(new ErrorTestRule(exception));
            }
        };

        sut.postProcessTestInstance(this, null);

        final RuntimeException e = assertThrows(RuntimeException.class,
                () -> sut.beforeEach(MockExtensionContext.any()));

        assertThat(e.getCause(), is(exception));
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

    /**
     * Method オブジェクトを取得するために定義した空メソッド。
     * 実行して使用することはない。
     */
    private void testForMock() {
        // noop
    }

    /**
     * 実行時にコンストラクタで指定された例外をスローする {@link Statement}.
     */
    public static class ErrorTestRule implements TestRule {
        private final Throwable exception;

        public ErrorTestRule(Throwable exception) {
            this.exception = exception;
        }

        @Override
        public Statement apply(Statement base, Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    throw exception;
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