package nablarch.test.junit5.extension.http;

import nablarch.test.core.http.AbstractHttpRequestTestTemplate;
import nablarch.test.core.http.TestCaseInfo;
import nablarch.test.event.TestEventDispatcher;
import nablarch.test.junit5.extension.JupiterEngineRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * {@link AbstractHttpRequestTestTemplate} を直接継承した独自拡張クラスに対して、
 * {@link BasicHttpRequestTestExtension} を継承した Extension が使えることをテストする。
 * <p>
 * {@link BasicHttpRequestTestExtensionTest} が押さえているのは
 * {@link nablarch.test.core.http.BasicHttpRequestTestTemplate} を生成する既定の実装である。
 * ここでは {@code createSupport} をオーバーライドして
 * {@link AbstractHttpRequestTestTemplate} の直接のサブクラスを返し、
 * それがテストクラスのフィールドにインジェクションされること、
 * および独自の合成アノテーションで渡した {@code baseUri} が
 * {@code getBaseUri()} の戻り値になることを確かめる。
 * </p>
 * @author Ito Kiyohito
 */
public class AbstractHttpRequestTestTemplateIntegrationTest {

    /**
     * 実行対象のテストクラスにインジェクションされたサポートクラスのインスタンス。
     */
    static CustomHttpRequestTestSupport injectedSupport;

    @BeforeEach
    void clearInjectedSupport() {
        injectedSupport = null;
    }

    @Test
    void AbstractHttpRequestTestTemplateを直接継承した独自拡張クラスがインジェクションされることをテスト() {
        JupiterEngineRunner.ExecutionSummary summary = JupiterEngineRunner.run(CustomTestFixture.class);

        assertThat(summary.getSuccessfulTestCount(), is(1));
        assertThat(injectedSupport, is(instanceOf(CustomHttpRequestTestSupport.class)));
        assertThat("合成アノテーションに指定した baseUri が getBaseUri() の戻り値になる",
                injectedSupport.getBaseUri(), is("/custom/"));
    }

    /**
     * {@link AbstractHttpRequestTestTemplate} を直接継承した独自拡張クラス。
     */
    static class CustomHttpRequestTestSupport extends AbstractHttpRequestTestTemplate<TestCaseInfo> {
        private final String baseUri;

        CustomHttpRequestTestSupport(Class<?> testClass, String baseUri) {
            super(testClass);
            this.baseUri = baseUri;
        }

        @Override
        protected String getBaseUri() {
            return baseUri;
        }
    }

    /**
     * {@link CustomHttpRequestTestSupport} を生成する、
     * {@link BasicHttpRequestTestExtension} を継承した Extension。
     */
    static class CustomHttpRequestTestExtension extends BasicHttpRequestTestExtension {
        @Override
        protected TestEventDispatcher createSupport(Object testInstance, ExtensionContext context) {
            CustomHttpRequestTest annotation = findAnnotation(testInstance, CustomHttpRequestTest.class);
            return new CustomHttpRequestTestSupport(testInstance.getClass(), annotation.baseUri());
        }
    }

    /**
     * {@code baseUri} を渡すための独自の合成アノテーション。
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @ExtendWith(CustomHttpRequestTestExtension.class)
    @interface CustomHttpRequestTest {
        /**
         * ベース URI。
         * @return ベース URI
         */
        String baseUri();
    }

    /**
     * 独自の合成アノテーションを設定した、実行対象のテストクラス。
     */
    @CustomHttpRequestTest(baseUri = "/custom/")
    static class CustomTestFixture {
        CustomHttpRequestTestSupport support;

        @Test
        void test() {
            injectedSupport = support;
        }
    }
}
