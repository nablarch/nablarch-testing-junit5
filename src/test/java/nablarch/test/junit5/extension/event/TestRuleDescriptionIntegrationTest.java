package nablarch.test.junit5.extension.event;

import nablarch.test.event.TestEventDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * {@link TestEventDispatcherExtension#resolveTestRules()} で追加した {@link TestRule} へ渡される
 * {@link Description} が、テストメソッドの情報を持っていることをテストする。
 * <p>
 * {@link Description#getAnnotation(Class)} で実行中のテストメソッドのアノテーションを見て
 * 振る舞いを変えるルールがあるため、アノテーションが載っていることは
 * テストメソッド名やテストクラスと同様に、ルールが依存する情報である。
 * </p>
 * <p>
 * ルールが記録する {@link Description} はクラス変数であるため、
 * {@code junit.jupiter.execution.parallel.enabled} を有効にするとテストが互いの記録を壊し合う。
 * 現在は surefire にも {@code junit-platform.properties} にも並列実行の設定が無いため直列に実行される。
 * </p>
 * @author Ito Kiyohito
 */
@ExtendWith(TestRuleDescriptionIntegrationTest.DescriptionCapturingExtension.class)
public class TestRuleDescriptionIntegrationTest {

    /**
     * ルールへ渡された {@link Description}。
     */
    static Description capturedDescription;

    @BeforeEach
    void clearRecords() {
        capturedDescription = null;
    }

    @Marked
    @Test
    void ルールへ渡されるDescriptionからテストメソッドのアノテーションを取得できることをテスト(TestInfo testInfo) {
        assertThat(capturedDescription.getClassName(), is(TestRuleDescriptionIntegrationTest.class.getName()));
        assertThat(capturedDescription.getMethodName(), is(testInfo.getTestMethod().orElseThrow().getName()));
        assertThat(capturedDescription.getAnnotation(Marked.class), is(notNullValue()));
        assertThat(capturedDescription.getAnnotation(Test.class), is(notNullValue()));
    }

    @Test
    void テストメソッドに付いていないアノテーションはDescriptionから取得できないことをテスト() {
        assertThat(capturedDescription.getAnnotation(Marked.class), is(nullValue()));
        assertThat(capturedDescription.getAnnotation(Test.class), is(notNullValue()));
    }

    /**
     * テストメソッドに付けるためだけのアノテーション。
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Marked {
    }

    /**
     * ルールへ渡された {@link Description} を記録する Extension。
     */
    static class DescriptionCapturingExtension extends TestEventDispatcherExtension {
        @Override
        protected TestEventDispatcher createSupport(Object testInstance, ExtensionContext context) {
            return new MockTestEventDispatcher();
        }

        @Override
        protected List<TestRule> resolveTestRules() {
            return Collections.singletonList(new DescriptionCapturingRule());
        }
    }

    /**
     * 渡された {@link Description} を記録する {@link TestRule}。
     */
    private static class DescriptionCapturingRule implements TestRule {
        @Override
        public Statement apply(Statement base, Description description) {
            capturedDescription = description;
            return base;
        }
    }
}
