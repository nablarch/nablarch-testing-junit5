package nablarch.test.junit5.extension.event;

import nablarch.test.event.TestEventDispatcher;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThrows;

/**
 * {@link TestEventDispatcherExtension#postProcessTestInstance(Object, org.junit.jupiter.api.extension.ExtensionContext)}
 * によるインジェクションの対象範囲をテストする。
 * <p>
 * {@link TestEventDispatcherExtensionTest} が押さえているのはフィールドの可視性と、
 * 型が非互換なフィールドにはインジェクションされないことである。
 * このクラスは、それ以外の 4 点、すなわち
 * スーパクラスで宣言されたフィールドも対象になること、
 * 対象が複数ある場合は全てに同じインスタンスが代入されること、
 * {@code Object} 型のフィールドも対象になること、
 * その {@code Object} 型のフィールドに初期値があると {@link IllegalStateException} になることを押さえる。
 * </p>
 * @author Ito Kiyohito
 */
public class TestEventDispatcherExtensionInjectionTest {

    final MockTestEventDispatcherExtension sut = new MockTestEventDispatcherExtension();

    @Test
    void スーパクラスで宣言されたフィールドにもインジェクションされることをテスト() throws Exception {
        SubClassFixture testInstance = new SubClassFixture();

        sut.postProcessTestInstance(testInstance, null);

        assertThat("スーパクラスで宣言されたフィールド",
                testInstance.declaredInSuperClass, is(instanceOf(MockTestEventDispatcher.class)));
        assertThat("サブクラスで宣言されたフィールド",
                testInstance.declaredInSubClass, is(instanceOf(MockTestEventDispatcher.class)));
    }

    @Test
    void 対象のフィールドが複数ある場合は全てに同じインスタンスが代入されることをテスト() throws Exception {
        MultipleFieldsFixture testInstance = new MultipleFieldsFixture();

        sut.postProcessTestInstance(testInstance, null);

        assertThat(testInstance.first, is(instanceOf(MockTestEventDispatcher.class)));
        assertThat("2 つのフィールドには同じインスタンスが代入される",
                testInstance.second, is(sameInstance(testInstance.first)));
    }

    @Test
    void Object型のフィールドもインジェクションの対象になることをテスト() throws Exception {
        ObjectFieldFixture testInstance = new ObjectFieldFixture();

        sut.postProcessTestInstance(testInstance, null);

        assertThat("Object 型は生成したインスタンスを代入できる型に該当する",
                testInstance.objectField, is(instanceOf(MockTestEventDispatcher.class)));
    }

    @Test
    void 初期値を設定したObject型のフィールドを宣言しているとエラーになることをテスト() {
        InitializedObjectFieldFixture testInstance = new InitializedObjectFieldFixture();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> sut.postProcessTestInstance(testInstance, null));

        assertThat(exception.getMessage(),
                is("The objectField field of InitializedObjectFieldFixture is already set some value."));
    }

    /**
     * インジェクションの対象になるフィールドを宣言した基底クラス。
     */
    static class SuperClassFixture {
        TestEventDispatcher declaredInSuperClass;
    }

    /**
     * インジェクションの対象になるフィールドを、自身でもスーパクラスでも宣言しているテストクラス。
     */
    static class SubClassFixture extends SuperClassFixture {
        TestEventDispatcher declaredInSubClass;
    }

    /**
     * インジェクションの対象になるフィールドを 2 つ宣言したテストクラス。
     */
    static class MultipleFieldsFixture {
        TestEventDispatcher first;
        TestEventDispatcher second;
    }

    /**
     * 初期値を設定していない {@code Object} 型のフィールドを宣言したテストクラス。
     */
    static class ObjectFieldFixture {
        Object objectField;
    }

    /**
     * 初期値を設定した {@code Object} 型のフィールドを宣言したテストクラス。
     */
    static class InitializedObjectFieldFixture {
        Object objectField = new Object();
    }
}
