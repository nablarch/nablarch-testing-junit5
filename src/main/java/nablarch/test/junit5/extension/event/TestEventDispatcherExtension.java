package nablarch.test.junit5.extension.event;

import nablarch.core.util.annotation.Published;
import nablarch.test.event.TestEventDispatcher;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.junit.platform.commons.util.ReflectionUtils;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * NTF の JUnit 5 用 Extension の基底となる抽象クラス。
 * <p>
 * このクラスは、 {@link TestEventDispatcher} が提供する JUnit 4 用の拡張機能を、
 * JUnit 5 の Extension の仕組みで再現する。
 * </p>
 * <p>
 * 各 Extension はこのクラスを継承して作成することで、共通する部分の処理を省略できる。
 * </p>
 * @author Tanaka Tomoyuki
 */
@Published(tag = "architect")
public abstract class TestEventDispatcherExtension implements
        TestInstancePostProcessor,
        BeforeAllCallback,
        AfterAllCallback,
        BeforeEachCallback,
        AfterEachCallback,
        InvocationInterceptor {

    /**
     * 何も処理を行わない{@link Statement}。
     */
    private static final Statement NOOP_STATEMENT = new Statement() {
        @Override
        public void evaluate() {
            // 内部で使用する TestRule の再現を行うときのベースとなる Statement になるため処理は何も行わない
        }
    };

    /**
     * Extension が生成しテストクラスにインジェクションする、サポートクラスのインスタンス。
     * <p>
     * このフィールドは、 {@link #postProcessTestInstance(Object, ExtensionContext)} が実行されたときに初期化される。
     * 設定される値は、 {@link #createSupport(Object, ExtensionContext)} が返却したインスタンスが使用される。
     * </p>
     */
    protected TestEventDispatcher support;

    @Override
    public void postProcessTestInstance(final Object testInstance, ExtensionContext context) throws Exception {
        support = createSupport(testInstance, context);

        Predicate<Field> isInjectionTarget = buildInjectionTargetCondition(support.getClass());
        List<Field> fields = ReflectionUtils.findFields(testInstance.getClass(),
                isInjectionTarget,
                ReflectionUtils.HierarchyTraversalMode.BOTTOM_UP);

        for (Field field : fields) {
            field.setAccessible(true);
            Object value = field.get(testInstance);
            if (value != null) {
                String message =
                        String.format("The %s field of %s is already set some value.",
                                field.getName(),
                                testInstance.getClass().getSimpleName());
                throw new IllegalStateException(message);
            }

            field.set(testInstance, support);
        }
    }

    /**
     * テストインスタンスにインジェクションするサポートクラスのインスタンスを生成する。
     * @param testInstance テストインスタンス
     * @param context コンテキスト
     * @return サポートクラスのインスタンス
     */
    protected abstract TestEventDispatcher createSupport(final Object testInstance, ExtensionContext context);

    /**
     * 指定されたフィールドが、サポートクラスのインスタンスをインジェクションする対象となるか判定するための
     * {@link Predicate} を生成する。
     * @param supportClass 生成されたサポートクラスの {@link Class} オブジェクト
     * @return インジェクション対象の判定を行うための {@link Predicate}
     */
    private Predicate<Field> buildInjectionTargetCondition(Class<? extends TestEventDispatcher> supportClass) {
        return field -> field.getType().isAssignableFrom(supportClass);
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        TestEventDispatcher.dispatchEventOfBeforeTestClassAndBeforeSuit();
    }

    /**
     * テストメソッドの前処理を実行する。
     * @param context コンテキスト
     * @throws Exception 例外がスローされた場合
     */
    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        applyInternalTestRules(context);
        support.dispatchEventOfBeforeTestMethod();
    }

    /**
     * NTF が内部で使用する{@link TestRule}を適用する。
     * <p>
     * 何も処理を行わない{@link Statement}に対して適用するため、
     * ルールの前処理と後処理は、どちらもテストメソッドが実行される前に完了する。
     * </p>
     * @param context コンテキスト
     */
    private void applyInternalTestRules(ExtensionContext context) {
        Statement statement = applyTestRules(resolveInternalTestRules(), NOOP_STATEMENT, context);

        try {
            statement.evaluate();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * {@link #resolveTestRules()}が返す{@link TestRule}で、テストメソッドの実行を包む。
     * @param invocation テストメソッドの実行
     * @param invocationContext テストメソッドの実行に関する情報
     * @param extensionContext コンテキスト
     * @throws Throwable テストメソッドまたは{@link TestRule}が例外をスローした場合
     */
    @Override
    public void interceptTestMethod(Invocation<Void> invocation,
                                    ReflectiveInvocationContext<Method> invocationContext,
                                    ExtensionContext extensionContext) throws Throwable {
        applyTestRules(resolveTestRules(), toStatement(invocation), extensionContext).evaluate();
    }

    /**
     * {@link #resolveTestRules()}が返す{@link TestRule}で、テストテンプレートメソッドの実行を包む。
     * <p>
     * {@code @ParameterizedTest}や{@code @RepeatedTest}のように、
     * {@code @TestTemplate}を用いて実装されたテストはこのメソッドを経由して実行される。
     * </p>
     * @param invocation テストテンプレートメソッドの実行
     * @param invocationContext テストテンプレートメソッドの実行に関する情報
     * @param extensionContext コンテキスト
     * @throws Throwable テストテンプレートメソッドまたは{@link TestRule}が例外をスローした場合
     */
    @Override
    public void interceptTestTemplateMethod(Invocation<Void> invocation,
                                            ReflectiveInvocationContext<Method> invocationContext,
                                            ExtensionContext extensionContext) throws Throwable {
        applyTestRules(resolveTestRules(), toStatement(invocation), extensionContext).evaluate();
    }

    /**
     * テストメソッドの実行を{@link Statement}に変換する。
     * @param invocation テストメソッドの実行
     * @return {@code invocation}を実行する{@link Statement}
     */
    private Statement toStatement(Invocation<Void> invocation) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                invocation.proceed();
            }
        };
    }

    /**
     * 指定された{@link TestRule}を、リストの先頭から順に{@link Statement}へ適用する。
     * <p>
     * 後から適用したルールほど外側になるため、リストの末尾にあるルールが最も外側になる。
     * これは JUnit 4 の{@link org.junit.rules.RunRules}と同じ順序である。
     * </p>
     * @param testRules 適用する{@link TestRule}のリスト
     * @param base ルールを適用する対象の{@link Statement}
     * @param context コンテキスト
     * @return {@code base}に全ての{@link TestRule}を適用した{@link Statement}
     */
    private Statement applyTestRules(List<TestRule> testRules, Statement base, ExtensionContext context) {
        Description description = convert(context);

        Statement statement = base;
        for (TestRule testRule : testRules) {
            statement = testRule.apply(statement, description);
        }
        return statement;
    }

    /**
     * {@link ExtensionContext}(JUnit5) の情報を {@link Description}(JUnit4) に詰め替える。
     * @param extensionContext {@link ExtensionContext}
     * @return {code extensionContext} の情報をもとに構築された {@link Description}
     */
    private Description convert(ExtensionContext extensionContext) {
        Class<?> testClass = extensionContext.getRequiredTestClass();
        Method testMethod = extensionContext.getRequiredTestMethod();
        return Description.createTestDescription(testClass, testMethod.getName(), testMethod.getAnnotations());
    }

    /**
     * NTF が内部で使用する JUnit 4 の {@link TestRule} のリストを取得する。
     * <p>
     * ここで返したルールは、テストメソッドの前処理({@link #beforeEach(ExtensionContext)})の中で、
     * 何も処理を行わない {@link Statement} に対して適用される。
     * テストメソッドの実行を包まないため、
     * {@link Description} からテストメソッド名を控えるだけのルールにしか使用できない。
     * </p>
     * <p>
     * このメソッドは、 NTF が提供する Extension が内部のルールを追加するためのものである。
     * テストに適用したいルールは {@link #resolveTestRules()} で返却すること。
     * </p>
     * @return NTF が内部で使用する JUnit 4 の {@link TestRule} のリスト
     */
    protected List<TestRule> resolveInternalTestRules() {
        return Collections.singletonList(support.testName);
    }

    /**
     * テストメソッドの実行に対して適用する JUnit 4 の {@link TestRule} のリストを取得する。
     * <p>
     * ここで返したルールは、テストメソッドの実行を包む形で適用される。
     * すなわち、ルールが {@code base.evaluate()} を呼ぶ前に記述した処理はテストメソッドの前に、
     * 後に記述した処理はテストメソッドの後に実行される。<br>
     * ただし、ルールが包むのはテストメソッドの実行だけであり、
     * JUnit 4 とは異なり {@code @BeforeEach} や {@code @AfterEach} は含まれない。
     * </p>
     * <p>
     * JUnit 4 時代に作成した独自のサポートクラスを移植する場合は、
     * このメソッドをオーバーライドしてサポートクラスで宣言したルールインスタンスを
     * リストにして返却するように実装する。
     * 基底実装は空のリストを返すため、独自のルールだけを返却すればよい。
     * 以下に実装例を示す。
     * </p>
     * <pre>{@code
     * protected List<TestRule> resolveTestRules() {
     *     List<TestRule> testRules = new ArrayList<>();
     *     // 独自の TestRule を追加する
     *     testRules.add(((YourSupport)support).yourTestRule);
     *     return testRules;
     * }
     * }</pre>
     * @return テストメソッドの実行に適用したい JUnit 4 の {@link TestRule} のリスト
     */
    protected List<TestRule> resolveTestRules() {
        return Collections.emptyList();
    }

    /**
     * テストメソッドの後処理を実行する。
     * @param context コンテキスト
     * @throws Exception 例外がスローされた場合
     */
    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        support.dispatchEventOfAfterTestMethod();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        TestEventDispatcher.dispatchEventOfAfterTestClass();
    }

    /**
     * 指定されたテストインスタンスのクラスに設定されたアノテーションを取得する。
     * @param testInstance テストインスタンス(null不可)
     * @param annotationClass 取得するアノテーションの型
     * @param <A> アノテーションの型
     * @return テストクラスに設定されたアノテーション
     */
    protected <A extends Annotation> A findAnnotation(Object testInstance, Class<A> annotationClass) {
        return testInstance.getClass().getAnnotation(annotationClass);
    }
}
