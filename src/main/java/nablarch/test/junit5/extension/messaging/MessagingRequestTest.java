package nablarch.test.junit5.extension.messaging;

import nablarch.core.util.annotation.Published;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@link MessagingRequestTestExtension} を適用するための合成アノテーション。
 * @author Tanaka Tomoyuki
 */
@Published
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ExtendWith(MessagingRequestTestExtension.class)
public @interface MessagingRequestTest {
}
