package nablarch.test.junit5.extension;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@link TestSupportExtension} を適用するためのメタアノテーション。
 * @author Tanaka Tomoyuki
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ExtendWith(TestSupportExtension.class)
public @interface NablarchTest {
}
