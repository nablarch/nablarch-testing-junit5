package nablarch.test.junit5.extension.event;

import nablarch.core.repository.SystemRepository;
import nablarch.test.RepositoryInitializer;
import nablarch.test.event.TestEventListener;
import nablarch.test.junit5.extension.MockExtensionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * 独自拡張用の Extension クラスで {@code beforeEach} をオーバーライドしたときに、
 * {@code super.beforeEach(context)} を呼ぶかどうかで
 * スーパクラスの事前処理が実行されるかどうかが変わることをテストする。
 * <p>
 * {@link MockTestEventDispatcherExtension} が {@code super} を呼ぶ版であり、
 * 呼ばない版と並べることで、差が {@code super} の呼び出しだけであることを示す。
 * スーパクラスの事前処理が実行されたかどうかは、
 * {@link TestEventListener#beforeTestMethod()} の呼び出し回数で観測する。
 * </p>
 * @author Ito Kiyohito
 */
public class TestEventDispatcherExtensionSuperCallTest {

    @BeforeEach
    void setUpRepository() {
        RepositoryInitializer.recreateRepository("unit-test.xml");
    }

    @Test
    void superを呼ぶとスーパクラスの事前処理が実行されることをテスト() throws Exception {
        TestEventDispatcherExtension sut = new MockTestEventDispatcherExtension();
        sut.postProcessTestInstance(new TestInstanceFixture(), null);

        TestEventDispatcherExtensionTest.MockTestEventListener listener =
                SystemRepository.get("mockTestEventListener");
        assertThat(listener.beforeTestMethodInvokedCount, is(0));

        sut.beforeEach(MockExtensionContext.any());

        assertThat(listener.beforeTestMethodInvokedCount, is(1));
    }

    @Test
    void superを呼ばないとスーパクラスの事前処理が実行されないことをテスト() throws Exception {
        TestEventDispatcherExtension sut = new SuperCallSkippingExtension();
        sut.postProcessTestInstance(new TestInstanceFixture(), null);

        TestEventDispatcherExtensionTest.MockTestEventListener listener =
                SystemRepository.get("mockTestEventListener");
        assertThat(listener.beforeTestMethodInvokedCount, is(0));

        sut.beforeEach(MockExtensionContext.any());

        assertThat("super.beforeEach を呼ばないと beforeTestMethod は呼ばれない",
                listener.beforeTestMethodInvokedCount, is(0));
    }

    /**
     * {@code beforeEach} をオーバーライドし、 {@code super} を呼ばない Extension。
     */
    static class SuperCallSkippingExtension extends MockTestEventDispatcherExtension {
        @Override
        public void beforeEach(ExtensionContext context) {
            // スーパクラスの beforeEach を呼ばない
        }
    }

    /**
     * インジェクションの対象になるフィールドを持たない、テストインスタンスの代わり。
     */
    static class TestInstanceFixture {
    }
}
