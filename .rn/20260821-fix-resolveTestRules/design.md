# TestRule 再現機構 — design notes

Not read at runtime — for whoever maintains the design and needs to judge whether a decision is still
right when requirements change.

調査日 2026-08-21 / 基準コミット ea4caf5 / JUnit 5.11.0・junit:junit 4.13.1・Java 17

**ユーザーに判断してもらうのは §5 の判断1だけ。** 判断2（直し方）は選択肢が存在しないため、
決定事項として §4 に記録する。

## 1. Background & Goals

### 1.1 What is the goal?

解説書「JUnit 5用拡張機能 § JUnit 4のTestRuleを再現する」に書かれているとおりに実装すれば
JUnit 4 の `TestRule` が再現される、という状態にする。

現状はそうなっていない。再現されるのは `TestRule` のうち「テスト本体より前に実行される部分」だけで、
後ろの部分はテスト本体より前に実行される。解説書が例に挙げている `Timeout` は、まったく機能しない。

出典: `nablarch/nablarch-document` `origin/main`
`ja/development_tools/testing_framework/guide/development_guide/06_TestFWGuide/JUnit5_Extension.rst:370-421`

**実測1 — ルールがテスト本体を包んでいない**

```
$ mvn -o clean test
[ERROR] TestRuleEmulationIntegrationTest.テストメソッドの実行がTestRuleに包まれていることをテスト:80
Expected: is <[rule-before, test]>
     but: was <[rule-before, rule-after, test]>
[ERROR] Tests run: 32, Failures: 1, Errors: 0, Skipped: 0
```

出典: `src/test/java/nablarch/test/junit5/extension/event/TestRuleEmulationIntegrationTest.java`（本セッションで追加）

**実測2 — 解説書の `Timeout` の例が機能しない**

解説書 rst:378-411 のコード（`Timeout(1000, MILLISECONDS)` を `resolveTestRules()` で追加）を
そのまま写し、2 秒スリープするテストメソッドを実行した。

```
$ mvn -o clean test -Dtest=DocTimeoutExampleSpikeTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.143 s
[INFO] BUILD SUCCESS
```

1 秒のタイムアウトに対して 2.143 秒かかって成功している。タイムアウトは検知されていない。

### 1.2 What goes wrong without this?

**NTF 本体は影響を受けていない。** NTF が使っている `TestRule` は
`TestEventDispatcher#testName`（`org.junit.rules.TestName`）と
`SimpleRestTestSupport#testDescription`（`nablarch.test.core.rule.TestDescription`）の 2 つだけで、
どちらも `Description` からテストメソッド名を控えるだけのもの。テスト本体を必要としない。

**壊れているのは、利用者が自分の `TestRule` を持ち込む経路だけ。** 具体的には次のようなルールが機能しない。

| ルールの種類 | 現状 |
|---|---|
| `TestName` のように前処理だけのもの | 動作する |
| `Timeout` のようにテスト本体の実行を監視するもの | 何も検知しない（実測2） |
| `ExternalResource` / `TemporaryFolder` のように後処理を持つもの | 後処理がテスト本体より前に実行される（実測1） |
| `ErrorCollector` / `ExpectedException` のようにテストの結果を見るもの | テスト本体を見ていないため機能しない |

失敗の仕方が「静かに成功する」であることが最も問題で、利用者は誤った安心を得る。

### 1.3 What does reaching it require?

次の 2 つを同時に満たす必要がある。片方だけなら過去に達成されているが、両立していない。

1. 利用者が `resolveTestRules()` で追加した `TestRule` が、テストメソッドの実行を包むこと
2. NTF の前処理（`dispatchEventOfBeforeTestMethod`、`testName` / `testDescription` の設定）が、
   利用者の `@BeforeEach` より先に実行されること

2 が必要な理由は、JUnit 4 では `TestEventDispatcher#dispatchEventOfBeforeTestMethod` が
親クラスの `@Before` であり、テストクラス側の `@Before` より必ず先に実行されていたため。
また `RestTestExtension#beforeEach` が呼ぶ `setUpDb()` は `testDescription.getMethodName()` を参照する。

出典: `TestEventDispatcher.java:135-140`（nablarch-testing 6-NEXT-SNAPSHOT sources）、
`src/main/java/nablarch/test/junit5/extension/http/RestTestExtension.java:20-23`、
`RestTestSupport.java:85`（nablarch-testing-rest 1.2.1 sources）、
`src/test/java/nablarch/test/junit5/extension/event/TestEventDispatcherExtensionLifecycleMethodTest.java:31-33`

### 1.4 What is out of scope?

- **解説書本体（nablarch/nablarch-document）の修正。** 別リポジトリのため、本セッションでは差分案の作成までとする。
- **nablarch-testing 本体の変更。** §5.3 の別課題にあたる。本リポジトリだけでは完結しない。
- **JUnit 6 への対応。** 本モジュールが JUnit 6 上で動作するかは未検証。§2.1 に未確認事項として記録する。
- **`@Rule` の自動収集。** 利用者が明示的に `resolveTestRules()` へ渡す方式は変えない。

## 2. Assumptions & Constraints

### 2.1 What do we take as true?

**JUnit 5 のライフサイクル順序（JUnit 5.11.0 上で実測）**

```
1. BeforeEachCallback
2. @BeforeEach
3. InvocationInterceptor#interceptTestMethod（proceed 前）
4. テストメソッド
5. InvocationInterceptor#interceptTestMethod（proceed 後）
6. @AfterEach
7. AfterEachCallback
```

**`TestRule` の性質**

`TestRule#apply(Statement base, Description description)` の `base` は「テストメソッドを呼び出す処理」であり、
ルールは `base.evaluate()` を自分で呼ぶことで、自分の処理をテストの前に置くか後に置くかを決める。
JUnit 4 のランナーはこの `Statement` を入れ子に積み上げてテスト 1 件の実行を構成する。

出典: `junit-4.13.1-sources.jar` → `org/junit/runners/model/Statement.java`、
`org/junit/rules/ExternalResource.java:46-66`、`org/junit/runners/BlockJUnit4ClassRunner.java`（`methodBlock`）

**JUnit の方針**

> JUnit Jupiter does not and will not support JUnit 4 rules natively.

> …limited to those rules that are semantically compatible to the JUnit Jupiter extension model,
> i.e. those that do not completely change the overall execution flow of the test.

移行用の `junit-jupiter-migrationsupport` が対応するのは `ExternalResource` / `Verifier` /
`ExpectedException` の 3 種類のみで、`Timeout` は含まれない。同モジュールは JUnit 6.0.0 で非推奨となり、
次のメジャーで削除される。

出典: [migrating-from-junit4.adoc](https://github.com/junit-team/junit-framework/blob/main/documentation/modules/ROOT/pages/migrating-from-junit4.adoc) § Limited JUnit 4 Rule Support

**JUnit のバージョンと保守状況**

| 対象 | 最新 | 状況 |
|---|---|---|
| JUnit 4 | 4.13.2 (2021-02-13) | 5 年半リリースなし。「新機能は追加しない。重大なバグ、特にセキュリティ関連の修正は当面続ける」（[junit4#1695](https://github.com/junit-team/junit4/issues/1695) marcphilipp, 2021-01-19） |
| JUnit 5 | 5.14.4 (2026-04-26) | 6.x と並行してリリース継続中。セキュリティポリシー上のサポート対象 |
| JUnit 6 | 6.1.3 (2026-08-07) | 現行メジャー。Java 17 ベースライン。セキュリティポリシー上のサポート対象 |

公表された EOL 日程は見つからなかった。サポート対象は [SECURITY.md](https://github.com/junit-team/junit-framework/blob/main/SECURITY.md) で
`6.1.x` と `5.14.x` に限られている。リリース日は GitHub Releases API で取得。

**本モジュールの JUnit 依存**

- `junit:junit:4.13.1` を **compile スコープ**で依存（利用者へ推移的に伝播する）。出典: `pom.xml:60-65`
- 参照している JUnit 4 の型は `org.junit.rules.TestRule` / `org.junit.runners.model.Statement` /
  `org.junit.runner.Description` の 3 つ
- **`junit-jupiter-migrationsupport` は使っていない。** JUnit 6 で削除予定なのは同モジュールであり、本モジュールではない
- `org.junit.platform.commons.util.ReflectionUtils` を使用している。これは `@API(status = INTERNAL)` が
  付いた内部 API で、JUnit 側の都合で変更・削除されうる。出典: `TestEventDispatcherExtension.java:11,65-67`

**nablarch-testing 本体の JUnit 4 結合度**

185 クラス中、`org.junit` を import しているのは 5 クラスのみ。

| クラス | 参照している JUnit 4 API |
|---|---|
| `nablarch/test/event/TestEventDispatcher` | `@Rule TestName`、`@BeforeClass` / `@Before` / `@After` / `@AfterClass` |
| `nablarch/test/core/db/DbAccessTestSupport` | `@Before` / `@After` |
| `nablarch/test/core/integration/IntegrationTestSupport` | `@Before` |
| `nablarch/test/Assertion` | `org.junit.Assert` / `org.junit.ComparisonFailure` |
| `nablarch/test/SystemPropertyResource` | `extends org.junit.rules.ExternalResource` |

**未確認事項**

- 本モジュールが JUnit 6 上で動作するか（内部 API 使用のため無検証では判断できない）
- JUnit 6 における `junit-vintage-engine` の削除方針の有無（非推奨になったことは確認済み）
- `resolveTestRules()` を実際に利用しているプロジェクトの有無と規模

### 2.2 What binds the solution?

**JUnit 5 には、§1.3 の 2 条件を同時に満たす拡張ポイントが存在しない。**

| | テストメソッドを呼び出す処理を引数で受け取れるか | 呼ばれる順番 |
|---|---|---|
| `BeforeEachCallback#beforeEach(ExtensionContext)` | 受け取れない | `@BeforeEach` より前 |
| `InvocationInterceptor#interceptTestMethod(Invocation, …)` | 受け取れる（`invocation`） | `@BeforeEach` より後 |

`ExtensionContext` にテストメソッドの呼び出しを表すものはない。`getRequiredTestMethod()` が返すのは
`java.lang.reflect.Method` で、これを自分で `invoke` すると JUnit 5 も改めて呼ぶためテストが 2 回走る。

これが §1.3 の 2 条件が両立しなかった構造的な理由であり、判断2 に選択肢がない理由でもある（§5.2）。

**その他の制約**

- `TestEventDispatcher#testName` は `nablarch-testing` 側の `public final` フィールドであり、本モジュールから
  値を直接設定できない。`TestName` の内部フィールドは private で、`apply()` 経由でしか設定されない
- `@Published(tag = "architect")` が付いた公開 API のため、`resolveTestRules()` のシグネチャ変更は避ける
- 既存テスト、特に `TestEventDispatcherExtensionLifecycleMethodTest` を壊さない

## 3. Design overview

### 3.1 What is the core idea, and why does it solve the problem?

**「TestRule をどこに適用するか」を 1 か所に決めるのをやめ、ルールの性質で適用先を分ける。**

過去の 2 つの実装は、いずれも全部を 1 か所に置いたために §2.2 のトレードオフをそのまま被った。

| | TestRule がテスト本体を包むか | NTF 前処理が `@BeforeEach` より先か |
|---|---|---|
| 初版 `ad2410b`（全部を `interceptTestMethod` へ） | ○ | × |
| PR #3 以降の現行（全部を `beforeEach` へ） | × | ○ |
| 本設計（性質で分ける） | ○ | ○ |

分ける基準は「そのルールがテスト本体の実行を必要とするか」。

- **必要としないもの** — `TestName` / `TestDescription`。`Description` からテストメソッド名を控えるだけ。
  現行どおり `beforeEach` で空の `Statement` に対して適用すれば足りる
- **必要とするもの** — 利用者が `resolveTestRules()` で追加するルール。`interceptTestMethod` へ移す

NTF の前処理より後に実行されて困るのは NTF 自身のルールだけなので、この分割で両方が成立する。

### 3.2 What are the pieces, and what is each responsible for?

| 要素 | 責務 |
|---|---|
| `beforeEach`（`BeforeEachCallback`、既存） | 内部ルールの適用と NTF 前処理の実行。実行位置は現行から変えない |
| `resolveInternalTestRules()`（新設、非公開） | `TestName` / `TestDescription` を返す。`SimpleRestTestExtension` が `testDescription` を追加する |
| `interceptTestMethod` / `interceptTestTemplateMethod`（`InvocationInterceptor`、新設） | `resolveTestRules()` が返すルールで `invocation.proceed()` を包む |
| `resolveTestRules()`（既存、`@Published`） | 利用者がテスト本体を包みたいルールを返す。基底実装は空リストを返す |

### 3.3 How does work move?

```
1. BeforeEachCallback#beforeEach
     resolveInternalTestRules() を NOOP に対して適用 → テストメソッド名が確定
     support.dispatchEventOfBeforeTestMethod()
     （RestTestExtension はここで setUpDb() を呼ぶ）
2. @BeforeEach              ← testName / testDescription を参照できる
3. interceptTestMethod
     resolveTestRules() のルールで invocation.proceed() を包み、evaluate()
4.   └ テストメソッド        ← 利用者のルールが包む範囲
5. proceed から復帰
6. @AfterEach
7. AfterEachCallback#afterEach
     support.dispatchEventOfAfterTestMethod()
```

## 4. Detailed design

**この章は実装済みのプロトタイプで検証した結果である。** 差分は下に全文を載せる。
プロトタイプはワーキングツリーから戻してあり、実装は判断1の決定後（タスク #4）に行う。

### 4.1 変更差分（`src/main` のみ。検証済み・68 行）

`src/main/java/nablarch/test/junit5/extension/event/TestEventDispatcherExtension.java`

```diff
 import org.junit.jupiter.api.extension.ExtensionContext;
+import org.junit.jupiter.api.extension.InvocationInterceptor;
+import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
 import org.junit.jupiter.api.extension.TestInstancePostProcessor;
@@
 import java.lang.reflect.Field;
+import java.lang.reflect.Method;
@@
         BeforeEachCallback,
-        AfterEachCallback {
+        AfterEachCallback,
+        InvocationInterceptor {
@@
     public void beforeEach(ExtensionContext context) throws Exception {
-        emulateTestRules(context);
+        applyInternalTestRules(context);
         support.dispatchEventOfBeforeTestMethod();
     }
 
     /**
-     * JUnit4の{@link TestRule}を再現する。
+     * NTF が内部で使用する{@link TestRule}を適用する。
      * @param context コンテキスト
      */
-    private void emulateTestRules(ExtensionContext context) {
-        Description description = convert(context);
-
-        List<TestRule> testRules = resolveTestRules();
-        Statement statement = NOOP_STATEMENT;
-        for (TestRule testRule : testRules) {
-            statement = testRule.apply(statement, description);
-        }
-
+    private void applyInternalTestRules(ExtensionContext context) {
+        Statement statement = applyTestRules(resolveInternalTestRules(), NOOP_STATEMENT, context);
         try {
             statement.evaluate();
         } catch (Throwable e) {
             throw new RuntimeException(e);
         }
     }
 
+    @Override
+    public void interceptTestMethod(Invocation<Void> invocation,
+                                    ReflectiveInvocationContext<Method> invocationContext,
+                                    ExtensionContext extensionContext) throws Throwable {
+        applyTestRules(resolveTestRules(), toStatement(invocation), extensionContext).evaluate();
+    }
+
+    @Override
+    public void interceptTestTemplateMethod(Invocation<Void> invocation,
+                                            ReflectiveInvocationContext<Method> invocationContext,
+                                            ExtensionContext extensionContext) throws Throwable {
+        applyTestRules(resolveTestRules(), toStatement(invocation), extensionContext).evaluate();
+    }
+
+    /**
+     * テストメソッドの呼び出しを表す{@link Statement}を生成する。
+     * @param invocation テストメソッドの呼び出し
+     * @return {@code invocation} を実行する{@link Statement}
+     */
+    private Statement toStatement(Invocation<Void> invocation) {
+        return new Statement() {
+            @Override
+            public void evaluate() throws Throwable {
+                invocation.proceed();
+            }
+        };
+    }
+
+    /**
+     * {@code base} を{@code testRules}で包んだ{@link Statement}を構築する。
+     * @param testRules 適用する{@link TestRule}のリスト
+     * @param base ベースとなる{@link Statement}
+     * @param context コンテキスト
+     * @return 構築された{@link Statement}
+     */
+    private Statement applyTestRules(List<TestRule> testRules, Statement base, ExtensionContext context) {
+        Description description = convert(context);
+        Statement statement = base;
+        for (TestRule testRule : testRules) {
+            statement = testRule.apply(statement, description);
+        }
+        return statement;
+    }
+
+    /**
+     * NTF が内部で使用する{@link TestRule}のリストを取得する。
+     * <p>
+     * ここで返した{@link TestRule}は、テストメソッドの実行を包まない。
+     * {@link Description}から情報を取得するだけのルールに限って使用すること。
+     * </p>
+     * @return NTF が内部で使用する{@link TestRule}のリスト
+     */
+    protected List<TestRule> resolveInternalTestRules() {
+        return Collections.singletonList(support.testName);
+    }
+
@@
     protected List<TestRule> resolveTestRules() {
-        return Collections.singletonList(support.testName);
+        return Collections.emptyList();
     }
```

`src/main/java/nablarch/test/junit5/extension/http/SimpleRestTestExtension.java`

```diff
     @Override
-    protected List<TestRule> resolveTestRules() {
-        List<TestRule> testRules = new ArrayList<>(super.resolveTestRules());
+    protected List<TestRule> resolveInternalTestRules() {
+        List<TestRule> testRules = new ArrayList<>(super.resolveInternalTestRules());
         testRules.add(((SimpleRestTestSupport) support).testDescription);
         return testRules;
     }
```

### 4.2 プロトタイプの実測結果

すべて JUnit 5.11.0・Java 17 上で実行。

| 確認項目 | 修正前 | 修正後 |
|---|---|---|
| `TestRuleEmulationIntegrationTest`（実行順が `[rule-before, test, rule-after]`） | FAIL | **PASS** |
| 解説書 rst:378-411 の `Timeout` の例（1 秒に対し 2 秒スリープ） | 2.143 s で BUILD SUCCESS | **`TestTimedOutException: test timed out after 1000 milliseconds`（0.976 s）** |
| `@ParameterizedTest` でルールが適用されるか | 未確認 | **PASS**（`interceptTestTemplateMethod` 経由） |
| `TestEventDispatcherExtensionLifecycleMethodTest`（NTF 前処理が `@BeforeEach` より先） | PASS | **PASS** |
| `RestTestExtensionTest` / `SimpleRestTestExtensionTest`（`testDescription` の設定） | PASS | **PASS** |
| `mvn -o clean test` 全体 | 32 件中 1 件失敗 | **32 件中 1 件失敗（別の 1 件）** |

### 4.3 既存テストで 1 件だけ落ちる。それは仕様変更そのもの

```
[ERROR] TestEventDispatcherExtensionTest.TestRuleエミュレート時に例外が発生した場合は
        _発生した例外を原因として持つ実行時例外がスローされること:135
        expected java.lang.RuntimeException to be thrown, but nothing was thrown
```

このテストは「`resolveTestRules()` が返したルールが `beforeEach` の中で評価され、
そこで起きた例外が `RuntimeException` に包まれる」ことを検証している
（出典: `src/test/java/nablarch/test/junit5/extension/event/TestEventDispatcherExtensionTest.java:121-137`）。
本設計はこの前提そのものを変えるので、テストは `interceptTestMethod` を対象に書き換える。

あわせて **例外の扱いが変わる。** 現行はルールが投げた例外を `RuntimeException` で包むが、
`interceptTestMethod` 側は包まずそのまま伝播させる。JUnit 4 では `ExpectedException` や
`ErrorCollector` がテストの成否を例外で表現するため、包むと機能しなくなる。
包まないのは意図した変更であり、タスク #4 の完了条件に含める。

### 4.4 再現できない範囲

`InvocationInterceptor` の契約上、**`invocation.proceed()` を必ず 1 回呼ばなければならない。**
`base.evaluate()` を呼ばずにテストを飛ばすルールを渡すと、テストは実測で次のように落ちる。

```
org.junit.platform.commons.JUnitException: Chain of InvocationInterceptors never called invocation:
  org.junit.jupiter.engine.extension.TimeoutExtension, ...
```

またルールが包むのは**テストメソッドの呼び出しのみ**で、`@BeforeEach` / `@AfterEach` および
NTF の前後処理は含まれない。JUnit 5 に、自身の `BeforeEachCallback` を `Statement` で包む手段がないため。

この 2 つは Javadoc（タスク #5）と解説書（タスク #6）に明記する。

### 4.5 記録しておく実装上の選択

`resolveTestRules()` の基底実装を `singletonList(support.testName)` から `emptyList()` に変える。

代案は「基底実装はそのまま `testName` を返し、`interceptTestMethod` でも `beforeEach` でも
同じリストを適用する」だが、`TestName` が 2 回適用される。値は同じなので実害はないものの、
`resolveTestRules()` の意味が「利用者がテスト本体を包みたいルール」に定まらない。空リストを採る。

副作用として、解説書 rst:420-421 の「必ず親クラスの `resolveTestRules()` が返すリストをベースにすること。
そうしない場合、親クラスで登録している `TestRule` が再現されなくなる」が事実でなくなる（§6 参照）。

## 5. 判断ポイント

### 5.1 判断1 — `resolveTestRules()` を存続させるか（**ユーザー判断**）

> **決定: 1-A（存続させて直す）。** 2026-08-21、`/rn:ty 1-a` によりユーザーが決定。
> 以降のタスク #4〜#6 は 1-A を前提とする。

JUnit は JUnit 4 の Rule をネイティブサポートしないと明言し、移行用モジュールも JUnit 6 で非推奨にした（§2.1）。
本モジュールの `resolveTestRules()` はその流れの外にある。直す前に、続けるかどうかを決める。

**選択肢 1-A — 存続させて直す**

§4 の差分を入れる。解説書のコード例がそのまま動くようになる。

**選択肢 1-B — 非推奨化して撤退する**

`resolveTestRules()` に `@Deprecated` を付け、解説書から「TestRule を再現できる」という記述を撤回し、
JUnit 5 の同等機能（`Timeout` → `@Timeout`、`ExternalResource`/`TemporaryFolder` →
`BeforeEachCallback`/`@TempDir`、`ExpectedException` → `assertThrows`）への置き換えを案内する。
実装は現状のままとし、`TestName` / `TestDescription` を設定する内部経路だけを残す。

**判断に必要な事実**

| | 1-A 存続 | 1-B 撤退 |
|---|---|---|
| 解説書のコード例が動くか | 動く（実測、§4.2） | 動かない。記述を撤回する |
| `ExternalResource` 系の後処理の順序 | 直る | 誤ったまま残る |
| 変更範囲 | 本モジュール `src/main` 68 行 + テスト | Javadoc・解説書のみ |
| 公開 API への影響 | 実行位置が変わる（後述） | 非推奨マークが付く |
| 既存利用者の移行コスト | なし（コードはそのまま） | ルールごとに JUnit 5 の機能へ書き換え |
| JUnit 4 依存の解消 | **しない** | **しない** |

**「JUnit 4 から離れる」ことにはどちらもならない、という点が判断の要。**
`nablarch-testing` 本体の `TestEventDispatcher#testName` が JUnit 4 の `@Rule TestName` であり、
`DbAccessTestSupport` などが `@Before` / `@After` を使っている（§2.1）。本モジュールの
`junit:junit:4.13.1` は compile スコープで、`resolveTestRules()` の存廃とは無関係に残る。
JUnit の流れに沿わせる本丸は §5.3 の別課題であって、判断1 ではない。

**推奨は 1-A。** 理由は 3 つ。

1. **1-B は不具合を残す。** `ExternalResource` の後処理がテスト本体より前に走る状態は、
   非推奨マークを付けても消えない。移行が済むまで誤った実行順で動き続ける
2. **1-A のコストが小さい。** `src/main` 68 行で、変更は本モジュール内に閉じる。
   実測で既存テストは 1 件しか落ちず、それは仕様変更そのもの（§4.3）
3. **1-A を選んでも 1-B へ進める。** `resolveTestRules()` の意味が
   「利用者がテスト本体を包みたいルール」に定まるので、後から非推奨にする判断がしやすくなる。逆は成り立たない

**1-A を選んだ場合に受け入れる非互換**（§5.2 の帰結）。
`resolveTestRules()` に前処理だけのルールを渡していた場合、その実行位置が
`beforeEach`（NTF 前処理と同時）からテストメソッド直前（`@BeforeEach` の後）へ移る。
また、ルールが投げた例外が `RuntimeException` に包まれなくなる（§4.3）。
`resolveTestRules()` を実際に使っているプロジェクトの有無は未確認（§2.1）。

### 5.2 判断2 — 直し方（**判断不要。決定事項として §4 に記録**）

「テスト本体を包む」ためには `invocation` を引数で受け取る必要があり、それができる拡張ポイントは
`InvocationInterceptor` しかない（§2.2）。そして `InvocationInterceptor` は `@BeforeEach` より後に
呼ばれるため、NTF の内部ルールをそちらへ移すと `TestEventDispatcherExtensionLifecycleMethodTest` が壊れる。
したがって「利用者のルールを `InvocationInterceptor` へ、内部ルールを `beforeEach` に残す」以外に形がない。

型ごとのアダプタを書く道（JUnit 公式の `junit-jupiter-migrationsupport` 方式）もあるが、
対応できるのが 3 種類だけで `Timeout` を含まず（§2.1）、実装量は §4 の差分より大きい。選ぶ理由がない。

実装上の細部（基底実装が空リストを返すか）は §4.5 に記録した。

### 5.3 判断1 の外にある別課題 — nablarch-testing 本体から JUnit 依存を分離する

NTF のロジックを JUnit のライフサイクル注釈から切り離し、JUnit 4 用・JUnit 5 用の薄いアダプタを両側に置く。
`TestEventDispatcher#testName` を素のフィールドにできれば、TestRule 再現機構は NTF 内部からは不要になり、
利用者向けに残すかどうかを純粋に方針として決められる。JUnit の流れに沿うのはこれ。

§2.1 のとおり結合は 5 クラスに限られており技術的な障壁は低い。しかし nablarch-testing 本体の変更を伴うため
本リポジトリだけでは完結せず、既存の JUnit 4 利用者への後方互換の検討も必要になる。
本件の不具合修正と同じ土俵で決めるべきではないため、別課題とする（§1.4）。

## 6. 解説書の通りになるか

**コード例はそのまま動くようになる。文章は 2 か所直す必要がある。** 1-A を選んだ場合の話。

出典はいずれも `nablarch/nablarch-document` `origin/main`
`ja/development_tools/testing_framework/guide/development_guide/06_TestFWGuide/JUnit5_Extension.rst`。

| 解説書の箇所 | 修正後の状態 |
|---|---|
| rst:378-391 `CustomTestSupport`（`@Rule Timeout`）の例 | そのまま。変更不要 |
| rst:395-414 `CustomTestSupportExtension`（`resolveTestRules()` のオーバーライド）の例 | そのまま。変更不要 |
| rst:416-418「これにより、JUnit 5のテスト上でもJUnit 4の `TestRule` を再現できるようになる」 | **要修正。** 包む範囲がテストメソッドのみで `@BeforeEach` / `@AfterEach` を含まないこと、`base` を呼ばないルールは使えないこと（§4.4）を追記する |
| rst:420-421「必ず親クラスの `resolveTestRules()` が返すリストをベースにすること。そうしない場合、親クラスで登録している `TestRule` が再現されなくなる」 | **要修正。** 基底実装が空リストを返すようになるため、この理由づけは成り立たなくなる（§4.5） |

**利用者が書くコードは変わらない。** 解説書の手順どおりに `resolveTestRules()` をオーバーライドすれば、
`Timeout` は実際にタイムアウトし（§4.2 実測）、`ExternalResource` の後処理はテスト本体の後に実行される。
これはタスク #6 で ja / en 両方の差分案として起こす。
