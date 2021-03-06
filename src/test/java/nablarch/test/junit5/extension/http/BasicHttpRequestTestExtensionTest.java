package nablarch.test.junit5.extension.http;

import nablarch.test.core.http.BasicHttpRequestTestTemplate;
import nablarch.test.event.TestEventDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.ReflectionUtils;

import java.lang.reflect.Method;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;

/**
 * {@link BasicHttpRequestTestExtension}の単体テスト。
 * @author Tanaka Tomoyuki
 */
public class BasicHttpRequestTestExtensionTest {

    final BasicHttpRequestTestExtension sut = new BasicHttpRequestTestExtension();

    @Test
    void BasicHttpRequestTestアノテーションのbaseUriに設定された値がgetBaseUriの戻り値として利用されることをテスト() throws Exception {
        @BasicHttpRequestTest(baseUri = "/foo-bar")
        class TemporaryTest {}

        TemporaryTest temporaryTest = new TemporaryTest();

        TestEventDispatcher support = sut.createSupport(temporaryTest, null);

        // getBaseUri() は protected で外部から実行できないのでリフレクションで実行して値を確認する
        Method getBaseUriMethod = ReflectionUtils.findMethod(BasicHttpRequestTestTemplate.class, "getBaseUri").get();
        getBaseUriMethod.setAccessible(true);
        Object baseUri = getBaseUriMethod.invoke(support);

        assertThat(baseUri, is("/foo-bar"));
    }

    @Test
    void テストクラスにBasicHttpRequestTestアノテーションが設定されていない場合は例外をスローすること() {
        class TemporaryTest {}

        TemporaryTest temporaryTest = new TemporaryTest();

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> sut.createSupport(temporaryTest, null));

        assertThat(exception.getMessage(),
                is(TemporaryTest.class.getName() + " is not annotated by BasicHttpRequestTest."));
    }
}
