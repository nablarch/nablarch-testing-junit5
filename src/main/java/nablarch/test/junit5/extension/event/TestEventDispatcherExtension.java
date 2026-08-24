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
 *
 * <h2 id="testrule-emulation">JUnit 4 の {@link TestRule} の再現について</h2>
 * <p>
 * このクラスは JUnit 4 の {@link TestRule} を 2 つの経路で適用する。
 * 利用者が {@link #resolveTestRules()} で返したルールは、
 * {@link InvocationInterceptor} 経由でテストメソッドの実行を包む形で適用される。
 * NTF が内部で使用するルール({@link #resolveInternalTestRules()})は、
 * {@link #beforeEach(ExtensionContext)} の中で、テストメソッドの実行を包まない形で適用される。
 * </p>
 * <p>
 * JUnit 5 に同等の機能がある場合は、ルールを移植するのではなく JUnit 5 の機能を使用すること。
 * {@link org.junit.rules.Timeout} に対する {@code @Timeout} 、
 * {@link org.junit.rules.ExternalResource} に対する
 * {@link BeforeEachCallback} と {@link AfterEachCallback} の組 、
 * {@link org.junit.rules.TemporaryFolder} に対する {@code @TempDir} 、
 * {@link org.junit.rules.ExpectedException} に対する {@code assertThrows}
 * がこれにあたる。
 * </p>
 * <p>
 * それでもルールを移植する場合、 JUnit 5 は JUnit 4 の {@link TestRule} を
 * ネイティブにはサポートしないため、この再現には以下の制約が付く。
 * なお以降で {@code base} と書いているのは、
 * {@link TestRule#apply(Statement, Description)} の第 1 引数、
 * すなわちルールが包む対象の {@link Statement} である。
 * </p>
 *
 * <h3 id="rule-support-table">どのルールが使えて、何が使えないか</h3>
 * <table class="striped">
 * <caption>JUnit 4 が標準で提供する主な {@link TestRule} を {@link #resolveTestRules()} で返し、
 * {@code @Test} / {@code @ParameterizedTest} / {@code @RepeatedTest} のテストメソッドに適用したときの結果</caption>
 * <thead>
 * <tr><th scope="col">ルール</th><th scope="col">結果</th><th scope="col">備考</th></tr>
 * </thead>
 * <tbody>
 * <tr><td>{@link org.junit.rules.Timeout}</td><td><b>条件つき</b></td>
 *     <td>テストはタイムアウトするが、 {@link nablarch.test.junit5.extension.db.DbAccessTestExtension}
 *         とは併用できない(→ <a href="#limitation-timeout-thread">別スレッド実行</a>)</td></tr>
 * <tr><td>{@link org.junit.rules.ExternalResource}</td><td>動く</td>
 *     <td>→ <a href="#limitation-wrapping">ルールが包む範囲</a></td></tr>
 * <tr><td>{@link org.junit.rules.TemporaryFolder}</td><td>動く</td>
 *     <td>→ <a href="#limitation-wrapping">ルールが包む範囲</a></td></tr>
 * <tr><td>{@link org.junit.rules.ExpectedException}</td><td>動く</td>
 *     <td>-</td></tr>
 * <tr><td>{@link org.junit.rules.ErrorCollector}</td><td>動く</td>
 *     <td>収集したエラーでテストが失敗する。失敗の原因となる例外は、収集したエラーが 1 件のときはその例外そのもの、
 *         2 件以上のときは {@link org.junit.runners.model.MultipleFailureException}</td></tr>
 * <tr><td>{@link org.junit.rules.Verifier}</td><td>動く</td>
 *     <td>{@code verify()} が投げた例外がそのまま伝播してテストが失敗する</td></tr>
 * <tr><td>{@link org.junit.rules.TestWatcher} / {@link org.junit.rules.Stopwatch}</td><td>動く</td>
 *     <td>→ <a href="#limitation-wrapping">ルールが包む範囲</a></td></tr>
 * <tr><td>{@link org.junit.rules.RuleChain}</td><td>動く</td>
 *     <td>指定した入れ子の順序が保たれる</td></tr>
 * <tr><td>{@link org.junit.rules.DisableOnDebug}</td><td><b>条件つき</b></td>
 *     <td>デバッグ実行中は、包んだルールが例外にもならないまま無効化される
 *         (このルール自身の仕様であり、再現機構による制約ではない)</td></tr>
 * <tr><td>{@code base} を呼ばないルール(スキップ系)</td><td><b>使えない</b></td>
 *     <td>テストが例外で失敗する(→ <a href="#limitation-invocation-count">{@code base} の呼び出し回数</a>)</td></tr>
 * <tr><td>{@code base} を 2 回以上呼ぶルール(リトライ系)</td><td><b>使えない</b></td>
 *     <td>テスト本体が 1 回実行されたうえで、テストが例外で失敗する
 *         (→ <a href="#limitation-invocation-count">{@code base} の呼び出し回数</a>)</td></tr>
 * </tbody>
 * </table>
 *
 * <h3 id="emulation-limitations">再現に付く制約</h3>
 * <p>
 * 上の表は、 {@code @Test} / {@code @ParameterizedTest} / {@code @RepeatedTest} の
 * テストメソッドに適用したときの結果である。
 * 表で「動く」「条件つき」としたルールにも、
 * <a href="#limitation-wrapping">ルールが包む範囲</a>、
 * <a href="#limitation-before-each-failure">前処理が失敗したときのルールの後処理</a>、
 * <a href="#limitation-test-factory">{@code @TestFactory}</a>、
 * <a href="#limitation-nested">{@code @Nested}</a> の 4 つは、
 * ルールの種類に関係なく等しくかかる。
 * <a href="#limitation-description">{@code Description} が実行を区別しないこと</a>は、
 * {@code @ParameterizedTest} / {@code @RepeatedTest} のときにかかる。
 * </p>
 * <ol>
 * <li id="limitation-invocation-count">
 *   <b>{@code base} を呼ばないルールと、 2 回以上呼ぶルールは使えない。</b><br>
 *   {@link InvocationInterceptor} は、テストメソッドの呼び出しをちょうど 1 回行うことを実装に要求している。
 *   この再現機構はルールが {@code base.evaluate()} を呼ぶ回数を制御できないため、
 *   呼ばないルールを渡すとテストは
 *   {@link org.junit.platform.commons.JUnitException}({@code never called invocation})で失敗し、
 *   2 回以上呼ぶルールを渡すとテスト本体が 1 回実行されたうえで
 *   {@link org.junit.platform.commons.JUnitException}({@code called invocation multiple times})で失敗する。<br>
 *   ただし、ルールが {@code base} を呼ぶ<b>前</b>に例外を投げた場合は、その例外がそのまま伝播する
 *   ({@link org.junit.platform.commons.JUnitException} にはならない)。
 * </li>
 * <li id="limitation-wrapping">
 *   <b>ルールが包むのはテストメソッドの呼び出しだけであり、
 *   {@code @BeforeEach} / {@code @AfterEach} や NTF の前後処理は含まれない。</b><br>
 *   JUnit 5 には、 {@code @BeforeEach} や Extension の {@link BeforeEachCallback} まで含めて
 *   {@link Statement} で包む手段がない。テストメソッドの呼び出しを引数で受け取れる拡張ポイントは
 *   {@link InvocationInterceptor} だけであり、そこは {@code @BeforeEach} より後に呼ばれる。
 *   そのため、 JUnit 4 ではルールが {@code @Before} / {@code @After} の外側にあったのに対し、
 *   ここではルールの前処理が {@code @BeforeEach} の後、後処理が {@code @AfterEach} の前に実行される。<br>
 *   {@link org.junit.rules.TemporaryFolder} が作った一時ファイルを {@code @AfterEach} から参照するコードや、
 *   {@code @AfterEach} の失敗を {@link org.junit.rules.TestWatcher} で観測するコードは、
 *   <b>例外にならないまま期待どおりに動かなくなる</b>ことに注意すること。
 *   後者は、ルールの後処理が {@code @AfterEach} より前に完了しているため、
 *   {@code @AfterEach} が失敗しても {@code succeeded()} が呼ばれ {@code failed()} は呼ばれない、という形で現れる。<br>
 *   これに対し、 {@code @BeforeEach} から {@link org.junit.rules.TemporaryFolder#getRoot()} を呼んだ場合は、
 *   一時フォルダがまだ作られていないため {@link IllegalStateException} が発生する。
 * </li>
 * <li id="limitation-before-each-failure">
 *   <b>{@code @BeforeEach}(および {@link #beforeEach(ExtensionContext)})が失敗すると、
 *   ルールは前処理も後処理も一切実行されない。</b><br>
 *   <a href="#limitation-wrapping">ルールが包む範囲</a>のとおり
 *   ルールを組み立てられるのはテストメソッドの呼び出し時であり、
 *   JUnit 5 は前処理が失敗するとテストメソッドの呼び出しに到達しないためである。
 *   JUnit 4 では {@code @Before} が失敗してもルールの後処理は実行されていた。<br>
 *   NTF 側の後処理({@link #afterEach(ExtensionContext)})は実行されるため、
 *   実行されないのは利用者のルールの後処理だけである。
 *   リソースの解放をルールに任せていると、前処理が失敗したときにだけ解放漏れが起きる。
 * </li>
 * <li id="limitation-description">
 *   <b>ルールへ渡す {@link Description} は、テストクラス・テストメソッド名・
 *   テストメソッドのアノテーションから構築する。</b><br>
 *   {@link Description#getAnnotation(Class)} で振る舞いを切り替えるルールも動作する。<br>
 *   ただし {@code @ParameterizedTest} や {@code @RepeatedTest} では、
 *   <b>全ての実行に対して内容が等しい {@link Description} が渡される。</b>
 *   この再現機構が {@link Description} に載せるのはテストクラス・メソッド名・メソッドのアノテーションだけで、
 *   何回目の実行かを表す情報がないためである
 *   (JUnit 4 の {@link org.junit.runners.Parameterized} は {@code test[0]} のように区別できた)。
 *   実行そのものは {@link ExtensionContext#getDisplayName()} で区別できるが、
 *   {@link Description} では区別できないため、 {@link Description} で実行を見分けて
 *   状態を切り替えるルールは使用できない
 *   ({@link org.junit.rules.Stopwatch} のように、ルールが実行ごとに状態を持つこと自体は問題なく動作する)。
 * </li>
 * <li id="limitation-timeout-thread">
 *   <b>{@link org.junit.rules.Timeout} と {@link nablarch.test.junit5.extension.db.DbAccessTestExtension} は併用できない。</b><br>
 *   {@link org.junit.rules.Timeout} はテスト本体を {@code "Time-limited test"} という別スレッドで実行するが、
 *   NTF の DB コネクションとトランザクションは {@link #beforeEach(ExtensionContext)} を実行したスレッドの
 *   {@link ThreadLocal} に束縛されるため、テスト本体からはどちらも取得できなくなる。
 *   取得時の例外を捕捉していると、<b>この状態でもテストは成功する。</b><br>
 *   同じ理由で、 {@code @BeforeEach} などで {@link ThreadLocal} に束縛した値も、
 *   テスト本体からは見えなくなる
 *   ({@link nablarch.core.ThreadContext} は {@link InheritableThreadLocal} のため引き継がれるが、
 *   テスト本体が書き込んだ値は元のスレッドへは戻らない)。<br>
 *   JUnit 5 の {@code @Timeout} は既定でテスト本体を別スレッドで実行しないため、この問題は起きない。
 * </li>
 * <li id="limitation-test-factory">
 *   <b>{@code @TestFactory} が生成した {@link org.junit.jupiter.api.DynamicTest} にはルールが適用されず、
 *   例外にもならないまま実行される。</b><br>
 *   このクラスがオーバーライドしているのは
 *   {@link #interceptTestMethod(Invocation, ReflectiveInvocationContext, ExtensionContext)} と
 *   {@link #interceptTestTemplateMethod(Invocation, ReflectiveInvocationContext, ExtensionContext)}
 *   の 2 つだけである。
 *   {@code @TestFactory} メソッドを包んでも、包まれるのは動的テストを生成する
 *   {@link java.util.stream.Stream} の生成だけで、動的テストの実行は包まれないため、対応していない。
 * </li>
 * <li id="limitation-nested">
 *   <b>{@code @Nested} なテストクラスを持つテストクラスでは正しく動作しないため、
 *   このクラスを継承した Extension を適用するテストクラスでは {@code @Nested} を使用しないこと。</b><br>
 *   Extension のインスタンスが外側のクラスと入れ子のクラスとで共有されるため、
 *   {@link #support} フィールドが後から生成されたサポートクラスのインスタンスで上書きされ、
 *   ルールが参照するサポートクラスとテスト本体が参照するものが別になる。
 *   これは {@link TestRule} の再現に固有の問題ではなく、
 *   {@link #support} フィールドを 1 つしか持たないことによるものである。
 * </li>
 * <li id="limitation-exception">
 *   <b>ルールが投げた例外の扱いは、どちらのメソッドで返したかによって変わる。</b><br>
 *   {@link #resolveTestRules()} で返したルールが投げた例外は、包まれずにそのまま伝播する。
 *   {@link #resolveInternalTestRules()} で返したルールが投げた例外は、
 *   {@link RuntimeException} に包まれてスローされる。<br>
 *   前者を包まないのは、 {@link org.junit.rules.ExpectedException} や
 *   {@link org.junit.rules.ErrorCollector} のように、
 *   例外でテストの成否を表現するルールが機能しなくなるためである。
 * </li>
 * </ol>
 *
 * <h3 id="other-interceptor-methods">{@link InvocationInterceptor} の他の default メソッドについて</h3>
 * <p>
 * このクラスが {@link InvocationInterceptor} を実装しているため、
 * {@link #interceptTestMethod(Invocation, ReflectiveInvocationContext, ExtensionContext)} と
 * {@link #interceptTestTemplateMethod(Invocation, ReflectiveInvocationContext, ExtensionContext)}
 * を除く 8 個の default メソッド
 * ({@code interceptTestClassConstructor} 、 {@code interceptBeforeAllMethod} 、
 * {@code interceptBeforeEachMethod} 、 {@code interceptAfterEachMethod} 、
 * {@code interceptAfterAllMethod} 、 {@code interceptTestFactoryMethod} 、
 * {@code interceptDynamicTest} の 2 つのオーバーロード)も、
 * このクラスを継承した Extension からオーバーライドできる状態になっている。
 * これらは {@link TestRule} の再現には関与しないが、
 * オーバーライドすると JUnit 5 によるテストの実行そのものに割り込むことになる。
 * なお {@code interceptDynamicTest} の 2 引数のオーバーロードは
 * {@code @Deprecated} であり、 API の状態も {@code DEPRECATED}(since 5.8)である。
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
     * <p>
     * このメソッドをオーバーライドする場合は、必ず {@code super.beforeEach(context)} を呼び出すこと。
     * 呼び出さないと、 NTF が内部で使用する {@link TestRule} の適用も
     * NTF の前処理({@link TestEventDispatcher#dispatchEventOfBeforeTestMethod()})も実行されない。
     * </p>
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
     * <p>
     * オーバーライドを許すと、{@link #resolveTestRules()}が返した{@link TestRule}が
     * 何のエラーも起こさないまま適用されなくなるため、このメソッドは{@code final}にしている。
     * テストメソッドの実行に独自の処理を挟みたい場合は、
     * {@link InvocationInterceptor}を実装した別の Extension クラスを作成してテストクラスに適用すること。
     * </p>
     * <p>
     * ただし、別の Extension クラスからは{@link #support}フィールドを参照できない。
     * JUnit 5 には他の Extension のインスタンスを取得する API が無く、
     * テストクラスがサポートクラスのインジェクション先フィールドを宣言している場合に限り、
     * {@link ExtensionContext#getRequiredTestInstance()}経由でそのフィールドから取得できるだけである。
     * また、別の Extension クラスを{@code @ExtendWith}でこの Extension より後に登録した場合、
     * その割り込みは{@link #resolveTestRules()}が返した{@link TestRule}の内側で実行される
     * (登録順を逆にすると外側になる)。
     * </p>
     * @param invocation テストメソッドの実行
     * @param invocationContext テストメソッドの実行に関する情報(使用しない)
     * @param extensionContext コンテキスト
     * @throws Throwable テストメソッドまたは{@link TestRule}が例外をスローした場合
     */
    @Override
    public final void interceptTestMethod(Invocation<Void> invocation,
                                          ReflectiveInvocationContext<Method> invocationContext,
                                          ExtensionContext extensionContext) throws Throwable {
        invokeWrappedInTestRules(invocation, extensionContext);
    }

    /**
     * {@link #resolveTestRules()}が返す{@link TestRule}で、テストテンプレートメソッドの実行を包む。
     * <p>
     * {@code @ParameterizedTest}や{@code @RepeatedTest}のように、
     * {@code @TestTemplate}を用いて実装されたテストはこのメソッドを経由して実行される。
     * </p>
     * <p>
     * このメソッドは{@link #interceptTestMethod(Invocation, ReflectiveInvocationContext, ExtensionContext)}
     * と同じ理由で{@code final}にしている。
     * </p>
     * @param invocation テストテンプレートメソッドの実行
     * @param invocationContext テストテンプレートメソッドの実行に関する情報(使用しない)
     * @param extensionContext コンテキスト
     * @throws Throwable テストテンプレートメソッドまたは{@link TestRule}が例外をスローした場合
     */
    @Override
    public final void interceptTestTemplateMethod(Invocation<Void> invocation,
                                                  ReflectiveInvocationContext<Method> invocationContext,
                                                  ExtensionContext extensionContext) throws Throwable {
        invokeWrappedInTestRules(invocation, extensionContext);
    }

    /**
     * {@link #resolveTestRules()}が返す{@link TestRule}で包んだうえで、テストメソッドの実行を行う。
     * <p>
     * テストメソッドとテストテンプレートメソッドで処理は同じであり、
     * どちらも{@link ReflectiveInvocationContext}を必要としない。
     * </p>
     * @param invocation テストメソッドの実行
     * @param extensionContext コンテキスト
     * @throws Throwable テストメソッドまたは{@link TestRule}が例外をスローした場合
     */
    private void invokeWrappedInTestRules(Invocation<Void> invocation, ExtensionContext extensionContext)
            throws Throwable {
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
     * <b>このメソッドは、 NTF が提供する Extension が内部のルールを追加するためのものである。
     * 利用者はオーバーライドしないこと。</b>
     * テストに適用したいルールは {@link #resolveTestRules()} で返却すること。
     * </p>
     * <p>
     * ここで返したルールは、テストメソッドの前処理({@link #beforeEach(ExtensionContext)})の中で、
     * 何も処理を行わない {@link Statement} に対して適用される。
     * すなわち、ルールの前処理と後処理はどちらもテストメソッドが実行される前に完了し、
     * テストメソッドの実行は包まれない。
     * そのため、 {@link Description} からテストメソッド名を控えるだけのルール
     * ({@link org.junit.rules.TestName} など)にしか使用できない。
     * </p>
     * <p>
     * また、ここで返したルールが投げた例外は {@link RuntimeException} に包まれてスローされる。
     * {@link #beforeEach(ExtensionContext)} が {@code throws Exception} しか宣言できないのに対し、
     * {@link Statement#evaluate()} は {@link Throwable} をスローするためである。
     * 同じルールでも {@link #resolveTestRules()} で返した場合は包まれずにそのまま伝播するので、
     * どちらで返すかによって例外の扱いが変わる。
     * </p>
     * <p>
     * リストの先頭にあるルールほど内側になり、末尾にあるルールが最も外側になる
     * (JUnit 4 の {@link org.junit.rules.RunRules} と同じ順序)。
     * </p>
     * <p>
     * {@code null} を返してはならず、リストに {@code null} を含めてもならない。
     * どちらの場合も {@link #beforeEach(ExtensionContext)} の中で
     * {@link NullPointerException} が発生する。
     * 返すルールが無い場合は空のリストを返すこと。
     * </p>
     * @return NTF が内部で使用する JUnit 4 の {@link TestRule} のリスト({@code null}不可)
     */
    protected List<TestRule> resolveInternalTestRules() {
        return Collections.singletonList(support.testName);
    }

    /**
     * テストメソッドの実行に対して適用する JUnit 4 の {@link TestRule} のリストを取得する。
     * <p>
     * ここで返したルールは、テストメソッドの実行を包む形で適用される。
     * すなわち、ルールが {@code base.evaluate()} を呼ぶ前に記述した処理はテストメソッドの前に、
     * 後に記述した処理はテストメソッドの後に実行される。
     * ルールが投げた例外は、包まれずにそのまま伝播する。
     * </p>
     * <p>
     * JUnit 4 時代に作成した独自のサポートクラスを移植する場合は、
     * このメソッドをオーバーライドしてサポートクラスで宣言したルールインスタンスを
     * リストにして返却するように実装する。以下に実装例を示す。
     * </p>
     * <pre>{@code
     * @Override
     * protected List<TestRule> resolveTestRules() {
     *     // 独自の TestRule を返却する
     *     return Collections.singletonList(((YourSupport)support).yourTestRule);
     * }
     * }</pre>
     * <p>
     * NTF が内部で使用するルールは {@link #resolveInternalTestRules()} が返すため、
     * NTF が提供する Extension はこのメソッドをオーバーライドしておらず、基底実装は常に空のリストを返す。
     * </p>
     * <p>
     * 複数のルールを返す場合、リストの先頭にあるルールほど内側になり、末尾にあるルールが最も外側になる
     * (JUnit 4 の {@link org.junit.rules.RunRules} と同じ順序)。
     * </p>
     * <p>
     * {@code null} を返してはならず、リストに {@code null} を含めてもならない。
     * どちらの場合もテストメソッドの実行時に {@link NullPointerException} が発生する。
     * 適用したいルールが無い場合は空のリストを返すこと。
     * </p>
     * <p>
     * <b>ルールの適用には制約がある。</b>
     * どのルールが使えるか、使えるルールに何の制約が付くかは
     * <a href="#testrule-emulation">JUnit 4 の {@code TestRule} の再現について</a>を参照すること。
     * 特に、ルールが包むのはテストメソッドの実行だけであること、
     * {@code @BeforeEach} が失敗するとルールの後処理が実行されず解放漏れが起きること、
     * {@link org.junit.rules.Timeout} が
     * {@link nablarch.test.junit5.extension.db.DbAccessTestExtension} と併用できないこと、
     * {@code @TestFactory} が生成した {@link org.junit.jupiter.api.DynamicTest} には
     * ルールが適用されないことの 4 点は、いずれもそれと分かる例外が出ないまま問題が起きる。
     * また、 JUnit 5 に同等の機能がある場合は、ルールを移植するのではなく JUnit 5 の機能を使用すること。
     * </p>
     * @return テストメソッドの実行に適用したい JUnit 4 の {@link TestRule} のリスト({@code null}不可)
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
