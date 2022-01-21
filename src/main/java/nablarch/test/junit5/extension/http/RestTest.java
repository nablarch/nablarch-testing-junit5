package nablarch.test.junit5.extension.http;

import nablarch.core.util.annotation.Published;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@link RestTestExtension} を適用するためのメタアノテーション。
 * @author Tanaka Tomoyuki
 */
@Published
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ExtendWith(RestTestExtension.class)
public @interface RestTest {
}
