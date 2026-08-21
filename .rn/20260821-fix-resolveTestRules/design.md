# TestRule 再現機構 — design notes

Not read at runtime — for whoever maintains the design and needs to judge whether a decision is still
right when requirements change.

調査日 2026-08-21 / JUnit 5.11.0・junit:junit 4.13.1

本モジュールのコンパイル対象は Java 17（親 pom `nablarch-parent-6-NEXT-SNAPSHOT.pom:46-47` の
`maven.compiler.source` / `maven.compiler.target`）。**§4.2 のプロトタイプを実行したときの JDK は特定できない（未確認）。**
本文書の作成時に実行した最小再現（§4.2 後半）は Temurin 21.0.11 で走らせた（`java -version` の出力）。

## 0. この文書の読み方

**これは決定済みの記録である。** 判断1（`resolveTestRules()` を存続させるか撤退するか）は 2026-08-21 に
**1-A（存続させて直す）** で決定した（§5.1）。判断2（直し方）は、§4.5 (2) の 1 点
（内部ルール用の `protected` メソッドを新設して公開 API を 1 本増やすか）を除き、
**要件を満たす形が 1 つしかない**ため、決定事項として §4 に記録する（§5.2）。
以降のタスク #4〜#6 はすべて 1-A を前提とする。この文書は、その決定の根拠と、決定に伴って受け入れた制約を残すためのもの。

**用語**

| 語 | 意味 |
|---|---|
| **本モジュール** | Maven モジュール `nablarch-testing-junit5`（`pom.xml:8`）。本リポジトリ `nablarch/nablarch-testing-junit5` が生成する唯一のモジュール。リポジトリ運用の話をするときだけ「本リポジトリ」と書く |
| **NTF** | Nablarch Testing Framework。本文では Maven アーティファクト `nablarch-testing` を指す。本モジュールが compile スコープで依存している（`pom.xml:37-41`） |
| **解説書** | `nablarch/nablarch-document` の `development_tools/testing_framework/guide/development_guide/06_TestFWGuide/JUnit5_Extension.rst`。ja / en の 2 系統があり、行番号を挙げるときは ja 版（`ja/development_tools/.../JUnit5_Extension.rst`）を指す。別リポジトリ |
| **`〜Extension` と `〜Support`** | `〜Extension`（`TestEventDispatcherExtension` / `SimpleRestTestExtension` / `RestTestExtension` / `DbAccessTestExtension`）は**本モジュール**側の JUnit 5 Extension。`〜Support`（`TestEventDispatcher` / `SimpleRestTestSupport` / `RestTestSupport` / `DbAccessTestSupport`）は**NTF 側**のサポートクラスで、Extension が `createSupport()` で生成して `support` フィールドに持つ。`XxxExtension` は `XxxSupport` を JUnit 5 で使うための薄い皮、という 1 対 1 の対応になっている |
| **`RestTestSupport` / `SimpleRestTestSupport`** | 上記のうち REST テスト用のもの。Maven アーティファクトは `nablarch-testing-rest`（本モジュールの optional 依存。`pom.xml:43-49`）で、**`nablarch-testing` ではない**。`RestTestSupport` は `SimpleRestTestSupport` を継承し、DB セットアップ機能を足したもの |
| **`@Published`** | Nablarch が後方互換性を維持する公開 API であることを表すアノテーション。クラス宣言に付いている場合は「クラスの全てのAPIを公開APIとする」意味で、「利用者がオーバーライド可能なメソッドも公開APIとする」と定義されている（`nablarch-core` sources `nablarch/core/util/annotation/Published.java:14,16`） |
| **rn** | 本リポジトリで使っている作業進行の仕組み。`.rn/<日付>-<課題名>/` 配下に `steering.md`（作業単位の定義）・`design.md`（本文書）・`checks/`（完了確認）を置く。`/rn:ty <選択肢>` は、ユーザーが提示された選択肢のいずれかを選んで決定を確定させるコマンド |
| **タスク #1 / #4 / #5 / #6** | `steering.md` の Tasks に定義された作業単位。#1 = 不具合を固定するテストを追加する、#4 = TestRule の適用先を分離する（実装）、#5 = Javadoc を実装と一致させる、#6 = 解説書の修正差分案を作成する |
| **プロトタイプ** | 本セッション内で、判断1 の決定より前に §4.1 の差分を実際に `src/main` へ当てて動かし、検証後にワーキングツリーから取り消した実装。コミットしていないため git 履歴には残っていない（§4.2） |

**見出しの言語** — §1〜§3 は、節見出しも小見出しも rn の design テンプレート由来の英語をそのまま使う。
冒頭の英文リード 2 行も同じくテンプレート由来。§4 は節見出しだけがテンプレート由来の英語で、小見出しは日本語。
テンプレートにない §0・§5・§6 は見出しも日本語。混在しているのはこの規則による。

**行番号と出典の基準**

- `src/main` / `pom.xml` は本リポジトリのコミット `b2ecc31` 時点（`git diff b2ecc31 HEAD -- src/main pom.xml` が空であることを確認済み）
- **`src/test` を参照するときは毎回コミットを明示する。** 本セッションのタスク #1 で改訂したため
- 外部の jar は sources jar を `~/.m2/repository` から展開して確認した。sources jar がないものは
  `javap -p -c -l` の LineNumberTable で照合した

**目次**

| 節 | 内容 |
|---|---|
| §1 Background & Goals | 何が壊れているか、どう壊れているか、なぜ 4 年半検知されなかったか |
| §2 Assumptions & Constraints | JUnit 5 / JUnit 4 / NTF について真とみなす事実、解を縛る条件、未確認事項 |
| §3 Design overview | 「ルールの性質で適用先を分ける」という中心の考え |
| §4 Detailed design | 差分・実測・受け入れる制約・実装上の選択・恒久テスト化するもの |
| §5 判断ポイント | 判断1（存続 / 撤退）の決定と根拠、判断2、別課題 |
| §6 解説書の通りになるか | タスク #6 の下ごしらえ |

## 1. Background & Goals

### 1.1 What is the goal?

解説書「JUnit 5用拡張機能 § JUnit 4のTestRuleを再現する」に書かれているとおりに実装すれば
JUnit 4 の `TestRule` が再現される、という状態にする。

現状はそうなっていない。`TestRule` は `apply(Statement base, Description description)` の `base` を呼ぶ位置で
前処理と後処理を書き分けるものだが、本モジュールは `base` に「何もしない `Statement`」を渡したうえで、
その結果を `beforeEach` の中で `evaluate()` している（`TestEventDispatcherExtension.java:44-49` と `:122-136`）。
そのため**ルールの前処理も後処理も、テスト本体が始まる前にまとめて終わってしまう。**
解説書が唯一の例に挙げている `Timeout` は、まったく機能しない。

出典: 解説書は `nablarch/nablarch-document` のコミット `5391d5c`（`origin/main`、2026-08-05）の
`ja/.../JUnit5_Extension.rst:370-421`。

**実測1 — ルールがテスト本体を包んでいない**（タスク #1、コミット `8780eb8`）

```
$ cat target/surefire-reports/nablarch.test.junit5.extension.event.TestRuleEmulationIntegrationTest.txt
Tests run: 3, Failures: 3, Errors: 0, Skipped: 0, Time elapsed: 0.063 s <<< FAILURE! - in nablarch.test.junit5.extension.event.TestRuleEmulationIntegrationTest
テストメソッドの実行がTestRuleに包まれていることをテスト  Time elapsed: 0.012 s  <<< FAILURE!
java.lang.AssertionError:
テストメソッドの実行がTestRuleに包まれていることをテスト() : TestRule の前処理と後処理は、テストメソッドの実行を挟む形で、入れ子を保って実行される
Expected: is <[outer-before, inner-before, test, inner-after, outer-after]>
     but: was <[outer-before, inner-before, inner-after, outer-after, test]>
	at ...TestRuleEmulationIntegrationTest.ルールがテスト本体を包んでいたことを検証する(TestRuleEmulationIntegrationTest.java:170)
```

このクラスの失敗は 3 件で、`@Test` 1 件と `@ParameterizedTest` 2 件（`@ValueSource(ints = {1, 2})`）が
いずれも同じ形で落ちる。2 本のルール（`outer` / `inner`）を入れ子にして渡し、
`@AfterEach` で実行ログを表明する構成である。
**`mvn -o clean test` 全体では `Tests run: 34, Failures: 3, Errors: 0, Skipped: 0`。**

出典: テストは `git show 8780eb8:src/test/java/nablarch/test/junit5/extension/event/TestRuleEmulationIntegrationTest.java`
（表明は `:168-178`、ルールは `:69-92`、Extension は `:129-153`）。上の出力は
`target/surefire-reports/` にある同名の `.txt`（生成時刻 2026-08-21 20:38。`8780eb8` のコミット時刻 20:34 より後）。
全体の件数は同ディレクトリの `TEST-*.xml` の `tests` / `failures` 属性を足し合わせて数えた。
**本文書の作成時には `mvn` を実行していない**（別のビルドと `target/` が衝突するため）。
上に写したのは、そのとき生成されたレポートファイルの中身である。

**実測2 — 解説書の `Timeout` の例が機能しない**（**再現物なし**）

解説書 rst:377-391 / rst:395-414 のコード（`Timeout(1000, MILLISECONDS)` を `resolveTestRules()` で追加）を
そのまま写した `DocTimeoutExampleSpikeTest` を作り、2 秒スリープするテストメソッドが 1 秒のタイムアウトに対して
2.143 秒かかって BUILD SUCCESS になった、とプロトタイプの検証時に記録されている。

**ただしこのクラスはワーキングツリーにも全ブランチの git 履歴にも存在しない**
（`git log --all --diff-filter=A --name-only` と `find` のいずれでもヒットしない）。再実行で確かめられる出典はない。
そこでタスク #4 では、解説書の `Timeout` の例が実際にタイムアウトすることを**恒久的なテストとして追加する**（§4.6）。
それまでは「実測したという記録」であって、読み手が確かめられる事実ではない。

### 1.2 What goes wrong without this?

**壊れているのは「テスト本体の実行を必要とするルール」すべて。**

| ルールの種類 | 現状 |
|---|---|
| `TestName` のように前処理だけのもの | 動作する |
| `Timeout` のようにテスト本体の実行を監視するもの | 何も検知しない（実測2） |
| `ExternalResource` / `TemporaryFolder` のように後処理を持つもの | 後処理がテスト本体より前に実行される（実測1） |
| `ErrorCollector` / `ExpectedException` のようにテストの結果を見るもの | テスト本体を見ていないため機能しない |

**NTF が内部で使う 2 つのルールは壊れない。** どちらも `Description` からテストメソッド名を控えるだけで、
テスト本体を必要としないため。

- `TestEventDispatcher#testName` — `org.junit.rules.TestName`。`nablarch-testing` 6-NEXT-SNAPSHOT sources
  `nablarch/test/event/TestEventDispatcher.java:92-94`（`@Rule public final TestName testName`）
- `SimpleRestTestSupport#testDescription` — `nablarch.test.core.rule.TestDescription`。**所属アーティファクトは
  `nablarch-testing` ではなく `nablarch-testing-rest`。** 6-NEXT-SNAPSHOT sources
  `nablarch/test/core/rule/TestDescription.java`（`TestWatcher` を継承し、`starting(Description)`（`:15-19`）で
  `clazz` と `methodName` を控えるだけ）。フィールドは同 sources
  `nablarch/test/core/http/SimpleRestTestSupport.java:77-79`

**しかし「壊れるのは利用者の自作ルールだけ」ではない。** NTF 自身が `TestRule` を公開している。
`nablarch-testing` sources の `nablarch/test/SystemPropertyResource.java:23-24` は
`@Published(tag = "architect") public class SystemPropertyResource extends ExternalResource` であり、
`after()`（`:36-39`）でシステムプロパティをテスト実行前の状態に戻す。JUnit 5 の利用者がこれを
`resolveTestRules()` に渡すと、この復元処理がテスト本体より前に走る。しかも例外にはならず、テストは通る。
**NTF が提供している公開 API のルールが、NTF の JUnit 5 拡張の上で黙って壊れる。**

失敗の仕方が「静かに成功する」であることが最も問題で、利用者は誤った安心を得る。

### 1.3 What does reaching it require?

次の 2 つを同時に満たす必要がある。片方だけなら過去に達成されているが、両立していない。

1. 利用者が `resolveTestRules()` で追加した `TestRule` が、テストメソッドの実行を包むこと
2. NTF の前処理（`dispatchEventOfBeforeTestMethod`、`testName` / `testDescription` の設定）が、
   利用者の `@BeforeEach` より先に実行されること

2 が必要な理由は、JUnit 4 では `TestEventDispatcher#dispatchEventOfBeforeTestMethod` が
親クラスの `@Before` であり、テストクラス側の `@Before` より必ず先に実行されていたため
（`TestEventDispatcher.java:134-140`）。また `RestTestExtension#beforeEach` が呼ぶ `setUpDb()` は
`testDescription.getMethodName()` を参照する（`nablarch-testing-rest` 6-NEXT-SNAPSHOT sources
`nablarch/test/core/http/RestTestSupport.java:79-82`。参照は `:81`）。

出典: `src/main/java/nablarch/test/junit5/extension/http/RestTestExtension.java:19-23`、
`git show 8780eb8:src/test/java/nablarch/test/junit5/extension/event/TestEventDispatcherExtensionLifecycleMethodTest.java`
の 29-34 行（`@BeforeEach` の中で `support.testName.getMethodName()` が `"test"` になっていることを表明している）

### 1.4 What is out of scope?

- **解説書本体（nablarch/nablarch-document）の修正。** 別リポジトリのため、本セッションでは差分案の作成までとする（タスク #6）
- **NTF 本体（nablarch-testing）の変更。** §5.3 の別課題にあたる。本リポジトリだけでは完結しない
- **JUnit 6 への対応。** 本モジュールが JUnit 6 上で動作するかは未検証。§2.3 に未確認事項として記録する
- **`@Rule` の自動収集。** 利用者が明示的に `resolveTestRules()` へ渡す方式は変えない（内部ルールの収集に
  反射を使う案は §4.5 (2) で比較している）

### 1.5 How did it get this way?

**解説書が書かれた時点では、記述は事実だった。5 日後に実装だけが変わった。**

| 日付 | できごと |
|---|---|
| 2022-01-21 | 本モジュール初版 `ad2410b`（PR #2「JUnit5対応」）。`TestRule` を `InvocationInterceptor#interceptTestMethod` に適用しており、ルールはテスト本体を包んでいた |
| 2022-01-26 | 解説書に当該節が追加される（`nablarch/nablarch-document` のコミット `c35a1b1`「JUnit5拡張機能の説明を追加 (#409)」。`JUnit5_Extension.rst` を 456 行追加している） |
| 2022-01-31 | 本モジュール `148db9a`（PR #3「fix: TestRuleを再現するタイミングをExtensionのbeforeEachの前に変更」）で適用先が `beforeEach` へ移り、ルールがテスト本体を包まなくなる。解説書は追随していない |

出典: `git show -s --format='%H %ad' ad2410b 148db9a`（それぞれ 2022-01-21 13:29 / 2022-01-31 16:57）、
`nablarch-document` 側は `git log -1 c35a1b1` と `git show --stat c35a1b1`。

**4 年 6 か月検知されなかった理由は 2 つ。**（2022-01-31 → 2026-08-21 は 4 年 6 か月 21 日）

1. NTF 自身が使うルールが 2 つとも前処理だけのもので、適用先が移っても壊れなかった（§1.2）
2. PR #3 が追加した検証が `testName.getMethodName()` の設定だけを見ており、ルールがテスト本体を包むかは
   対象外だった。出典: `git show 8780eb8:src/test/java/nablarch/test/junit5/extension/event/TestEventDispatcherExtensionTest.java`
   の 110-120 行（`beforeEachを実行すると_TestRuleのエミュレートが行われることをテスト`）

## 2. Assumptions & Constraints

### 2.1 What do we take as true?

**JUnit 5 のライフサイクル順序**

`junit-jupiter-engine` 5.11.0 の `TestMethodTestDescriptor#execute` が、1 件のテストメソッドについて次の順で呼ぶ。

```
1. invokeBeforeEachCallbacks       BeforeEachCallback              (:132)
2. invokeBeforeEachMethods         @BeforeEach                     (:134)
3. invokeBeforeTestExecutionCallbacks  BeforeTestExecutionCallback (:136)
4. invokeTestMethod                テストメソッド                    (:138)
     └ InterceptingExecutableInvoker 経由で InvocationInterceptor#interceptTestMethod が呼ばれる (:217)
5. invokeAfterTestExecutionCallbacks   AfterTestExecutionCallback  (:140)
6. invokeAfterEachMethods          @AfterEach                      (:142)
7. invokeAfterEachCallbacks        AfterEachCallback               (:144)
```

出典: `junit-jupiter-engine-5.11.0.jar` の
`org/junit/jupiter/engine/descriptor/TestMethodTestDescriptor.class` を
`javap -p -c -l` で逆アセンブルし、`execute` の LineNumberTable（`TestMethodTestDescriptor.java:129-147`）と
呼び出し順を突き合わせた。`:217` は `lambda$invokeTestMethod$8` が
`InterceptingExecutableInvoker.invoke(...)` を呼んでいる行。

**`TestRule` の性質**

`TestRule#apply(Statement base, Description description)` の `base` は「テストメソッドを呼び出す処理」であり、
ルールは `base.evaluate()` を自分で呼ぶことで、自分の処理をテストの前に置くか後に置くかを決める。
JUnit 4 のランナーはこの `Statement` を入れ子に積み上げてテスト 1 件の実行を構成する。
`BlockJUnit4ClassRunner#methodBlock` は、`withRules` を `withBefores` / `withAfters` の**外側**に積む。
つまり JUnit 4 ではルールが `@Before` / `@After` ごとテストを包んでおり、
**`ExternalResource#after()` は `@After` の後に実行されていた。**

出典: `junit-4.13.1-sources.jar` → `org/junit/runners/model/Statement.java`、
`org/junit/rules/ExternalResource.java:42-67`、
`org/junit/runners/BlockJUnit4ClassRunner.java:303-322`（`:316` methodInvoker → `:319` withBefores →
`:320` withAfters → `:321` withRules の順に包む）

**`InvocationInterceptor` の契約**

> Each method in this class must call {@link Invocation#proceed()} or {@link
> Invocation#skip()} exactly once on the supplied invocation. Otherwise, the
> enclosing test or container will be reported as failed.

出典: [InvocationInterceptor.java:35-37](https://github.com/junit-team/junit5/blob/r5.11.0/junit-jupiter-api/src/main/java/org/junit/jupiter/api/extension/InvocationInterceptor.java)（タグ r5.11.0）。
`Invocation#skip()` は `:236-245` に「This allows to bypass the check that `proceed()` must be called at least once」と
書かれた `default` メソッドとして定義されている。

実装側でもこの契約が確認できる。`junit-jupiter-engine-5.11.0.jar` の
`InvocationInterceptorChain$ValidatingInvocation` は `AtomicBoolean invokedOrSkipped` を持ち、
`proceed()`（`InvocationInterceptorChain.java:130-131`）と `skip()`（`:136-139`）の**どちらも**
`markInvokedOrSkipped()`（`:142-145`）を通す。この検査は 2 方向にある。

- **どちらも呼ばれないまま終わった場合** — `verifyInvokedAtLeastOnce()`（`:148-151`）が
  `"Chain of InvocationInterceptors never called invocation"` で失敗させる
- **2 回以上呼ばれた場合** — `markInvokedOrSkipped()` の `compareAndSet(false, true)` が 2 回目に失敗し、
  `"Chain of InvocationInterceptors called invocation multiple times instead of just once"` で失敗させる（`:142-143`）

いずれも `fail(String)`（`:154-156`）が `org.junit.platform.commons.JUnitException` を投げる。メッセージは
上の文字列に `": "` とインターセプタのクラス名を連結したものになる。
（`javap -p -c -l 'org/junit/jupiter/engine/execution/InvocationInterceptorChain$ValidatingInvocation.class'` で確認。
2 つのメッセージ文字列はいずれも定数プールに実在する）

**検査が置かれている位置。** `chainAndInvoke` は `verifyInvokedAtLeastOnce()` を `finally` ではなく
`proceed()` の**後**に置いている（`InvocationInterceptorChain.java:45-46`。`javap -p -c -l` の
LineNumberTable で確認。当該メソッドに例外テーブルはない）。したがって
**ルールが `base` を呼ばずに例外を投げた場合は、その例外がそのまま伝播し、`JUnitException` にはならない。**

**`InvocationInterceptor` が持つ intercept メソッド**

`javap -p org/junit/jupiter/api/extension/InvocationInterceptor.class`（`junit-jupiter-api-5.11.0.jar`）の出力より。

| メソッド | 戻り値 | 対象 |
|---|---|---|
| `interceptTestMethod` | `void` | `@Test` |
| `interceptTestTemplateMethod` | `void` | `@TestTemplate`（`@ParameterizedTest` / `@RepeatedTest` を含む） |
| `interceptTestFactoryMethod` | **`T`** | `@TestFactory` |
| `interceptDynamicTest`（2 オーバーロード） | `void` | `DynamicTest` |
| `interceptTestClassConstructor` / `interceptBeforeAllMethod` / `interceptBeforeEachMethod` / `interceptAfterEachMethod` / `interceptAfterAllMethod` | — | 本設計の対象外 |

**JUnit の方針**

> As stated above, JUnit Jupiter does not and will not support JUnit 4 rules natively.

> This support is based on adapters and is limited to those rules that are semantically compatible to
> the JUnit Jupiter extension model, i.e. those that do not completely change the overall execution
> flow of the test.

移行用の `junit-jupiter-migrationsupport` が対応するのは `org.junit.rules.ExternalResource`（`TemporaryFolder` を含む）/
`org.junit.rules.Verifier`（`ErrorCollector` を含む）/ `org.junit.rules.ExpectedException` の 3 種類のみで、
`Timeout` は含まれない。

出典: [migrating-from-junit4.adoc](https://github.com/junit-team/junit-framework/blob/r6.1.3/documentation/modules/ROOT/pages/migrating-from-junit4.adoc)（タグ r6.1.3）
の `:270`、`:274-277`、`:279-284`。同 `:267-268` には
「_JUnit 4 rule support_ is deprecated for removal since version 6.0.0.」ともある。

**`junit-jupiter-migrationsupport` は次のメジャーバージョンで削除される。**

> The `junit-jupiter-migrationsupport` module and its contained classes are now
> deprecated and will be removed in the next major version.

出典: [release-notes-6.0.0.adoc:195-196](https://github.com/junit-team/junit-framework/blob/r6.0.0/documentation/src/docs/asciidoc/release-notes/release-notes-6.0.0.adoc)（タグ r6.0.0）。
JUnit 6.0.0 のリリース日は同ファイル `:4` に「September 30, 2025」とある。

**JUnit のバージョンと保守状況**

| 対象 | 最新 | 状況 |
|---|---|---|
| JUnit 4 | 4.13.2 (2021-02-13) | 5 年半リリースなし。方針は下の引用のとおり |
| JUnit 5 | 5.14.4 (2026-04-26) | 6.x と並行してリリース継続中。セキュリティポリシー上のサポート対象 |
| JUnit 6 | 6.1.3 (2026-08-07) | 現行メジャー。Java 17 ベースライン。セキュリティポリシー上のサポート対象 |

JUnit 4 の方針について、JUnit チームの marcphilipp は 2021-01-19 に次のように書いている（原文ママ）。

> Even though new features are only added to JUnit Platform and Jupiter (currently known as JUnit 5),
> given the widespread use of JUnit 4, we will continue to fix critical bugs, in particular
> security-related ones, for the time being.

出典: [junit4#1695 のコメント](https://github.com/junit-team/junit4/issues/1695#issuecomment-762838552)。

リリース日は次のコマンドで取得した。

```
$ curl -s "https://api.github.com/repos/junit-team/junit-framework/releases?per_page=100" \
    | grep -E '"tag_name"|"published_at"'
$ curl -s "https://api.github.com/repos/junit-team/junit4/releases?per_page=5" \
    | grep -E '"tag_name"|"published_at"'
```

公表された EOL 日程は見つからなかった。セキュリティポリシー上のサポート対象は `6.1.x` と `5.14.x` に限られている
（[SECURITY.md](https://github.com/junit-team/junit-framework/blob/a15778ab46efeef03e2689e2fea0306b17a720cf/SECURITY.md)。
このファイルを最後に更新したコミット `a15778a`、2026-05-19 で固定したリンク）。
JUnit 6 の Java 17 ベースラインは `release-notes-6.0.0.adoc:8`（タグ r6.0.0）の "Java 17 and Kotlin 2.2 baseline" による。

**本モジュールの JUnit 依存**

- `junit:junit:4.13.1` を **compile スコープ**で依存（利用者へ推移的に伝播する）。出典: `pom.xml:57-62`
- 参照している JUnit 4 の型は `org.junit.rules.TestRule` / `org.junit.runners.model.Statement` /
  `org.junit.runner.Description` の 3 つ。出典: `grep -rn "org.junit" src/main --include=*.java`
- **`junit-jupiter-migrationsupport` は使っていない。** JUnit 6 で削除予定なのは同モジュールであり、本モジュールではない
- `org.junit.platform.commons.util.ReflectionUtils` を使用している。これは
  `@API(status = INTERNAL, since = "1.0")` が付いた内部 API で、JUnit 側の都合で変更・削除されうる。
  出典: `src/main/java/nablarch/test/junit5/extension/event/TestEventDispatcherExtension.java:11,65-67` と
  [ReflectionUtils.java:79](https://github.com/junit-team/junit5/blob/r5.11.0/junit-platform-commons/src/main/java/org/junit/platform/commons/util/ReflectionUtils.java)（タグ r5.11.0）

**NTF 本体の JUnit 4 結合度**

`nablarch-testing` 6-NEXT-SNAPSHOT sources jar の 185 java ファイル中、`org.junit` を import しているのは **9 ファイル**。
`unzip` して `grep -rl "^import.*org\.junit" --include=*.java .` と `find . -name '*.java' | wc -l` で数えた。

| ファイル | 参照している JUnit 4 API | 性質 |
|---|---|---|
| `nablarch/test/event/TestEventDispatcher` | `@Rule TestName`、`@BeforeClass` / `@Before` / `@After` / `@AfterClass`（`:5-10`） | ライフサイクル注釈・ルール |
| `nablarch/test/core/db/DbAccessTestSupport` | `@Before` / `@After`（`:15-16`） | ライフサイクル注釈 |
| `nablarch/test/core/integration/IntegrationTestSupport` | `@Before`（`:9`） | ライフサイクル注釈 |
| `nablarch/test/SystemPropertyResource` | `extends org.junit.rules.ExternalResource`（`:4`） | ルール |
| `nablarch/test/Assertion` | `org.junit.Assert` / `org.junit.ComparisonFailure`（`:13-14`。**静的 import ではない**） | 表明 |
| `nablarch/test/core/db/EntityTestSupport` | `import static org.junit.Assert.assertArrayEquals` / `assertEquals` | 表明の静的 import のみ |
| `nablarch/test/core/entity/SingleValidationTester` | `import static org.junit.Assert.assertEquals` / `assertFalse` / `assertTrue` | 表明の静的 import のみ |
| `nablarch/test/core/http/ServletForwardVerifier` | `import static org.junit.Assert.assertEquals` | 表明の静的 import のみ |
| `nablarch/test/core/messaging/MessagingRequestTestSupport` | `import static org.junit.Assert.assertEquals` | 表明の静的 import のみ |

内訳は **ライフサイクル注釈・ルールが 4 ファイル、表明が 5 ファイル（うち静的 import のみが 4）**。

**NTF 周辺のスレッド束縛状態（全数調査）**

§4.4 (3) で「テスト本体だけ別スレッドになると何が壊れるか」を論じるための前提。
`ThreadLocal` の宣言を sources jar 全体に対して `grep -rn "ThreadLocal" --include=*.java` で数えた。

| アーティファクト | `ThreadLocal` の宣言 | 種別 |
|---|---|---|
| `nablarch-testing` 6-NEXT-SNAPSHOT sources | **0 件** | — |
| `nablarch-testing-rest` 6-NEXT-SNAPSHOT sources | **0 件** | — |
| `nablarch-core` 6-NEXT-SNAPSHOT sources | 1 件 `nablarch/core/ThreadContext.java:45-57` | **`InheritableThreadLocal`**。`childValue` が親のマップを `new HashMap<>(parentValue)` で複製する |
| `nablarch-core-jdbc` 2.2.0 sources | 1 件 `nablarch/core/db/connection/DbConnectionContext.java:26-32` | 素の `ThreadLocal` |
| `nablarch-core-transaction` 2.1.0 sources | 1 件 `nablarch/core/transaction/TransactionContext.java:23-28` | 素の `ThreadLocal` |

`ThreadContext` が `InheritableThreadLocal` であることは、子スレッドへ値が引き継がれるという意味で
§4.4 (3) にとって**有利な事実**（`"Time-limited test"` スレッドでも壊れない）。
その裏返しとして、**子スレッドで書いた値は親スレッドへ戻らない**（`childValue` は複製であり共有ではない）。

`nablarch-core-jdbc` / `nablarch-core-transaction` は本モジュールが実際に解決する 6-NEXT-SNAPSHOT 版の
sources jar がローカルリポジトリに存在しないため、上の 2 件は 2.2.0 / 2.1.0 の sources で確認した。

### 2.2 What binds the solution?

**JUnit 5 には、§1.3 の 2 条件を同時に満たす拡張ポイントが存在しない。**

| | テストメソッドを呼び出す処理を引数で受け取れるか | 呼ばれる順番 |
|---|---|---|
| `BeforeEachCallback#beforeEach(ExtensionContext)` | 受け取れない | `@BeforeEach` より前 |
| `InvocationInterceptor#interceptTestMethod(Invocation, …)` | 受け取れる（`invocation`） | `@BeforeEach` より後 |

`ExtensionContext` には、これから行われるテストメソッドの呼び出しを表すものがない。
`javap -p org/junit/jupiter/api/extension/ExtensionContext.class`（`junit-jupiter-api-5.11.0.jar`）が返す
メソッドは 24 個で、テストメソッドに関するものは `getTestMethod()` / `getRequiredTestMethod()` の 2 つだけ。
`getTestMethod()` の戻り値は **`java.util.Optional<java.lang.reflect.Method>`**、
`getRequiredTestMethod()` は `java.lang.reflect.Method` である。いずれにせよ得られるのは
リフレクションの `Method` にすぎず、JUnit 5 側の呼び出しは `TestMethodTestDescriptor#execute` が
`invokeTestMethod` を通じて行う（§2.1 の `:138`）ので、
拡張が自分で `invoke` してもそれを**置き換えることはできない**。

これが §1.3 の 2 条件が両立しなかった構造的な理由であり、判断2 に選択肢がほぼない理由でもある（§5.2）。

**その他の制約**

- `TestEventDispatcher#testName` は NTF 側の `public final` フィールド（`TestEventDispatcher.java:92-94`）であり、
  本モジュールから値を直接設定できない。`TestName` の内部フィールドは
  `private volatile String name`（`junit-4.13.1-sources` `org/junit/rules/TestName.java:28`）で、
  `starting(Description)`（`:31-33`）経由でしか設定されない。つまり `apply()` を通すしかない
- `TestEventDispatcherExtension` はクラス宣言に `@Published(tag = "architect")` が付いている
  （`TestEventDispatcherExtension.java:33`）。`Published` の定義上、
  **このクラスの `protected` メソッドはすべて後方互換を保証する公開 API**（§0 の用語表）。
  `resolveTestRules()` のシグネチャ変更は避ける
- 既存テスト、特に `TestEventDispatcherExtensionLifecycleMethodTest` を壊さない

### 2.3 未確認事項

**確認済みの事実と混ざらないよう、独立した項として置く。** ここに挙げたものは実物で確かめていない。
関連する事実に出典を添えてある場合も、その出典が未確認の部分まで裏づけているわけではない。

- 本モジュールが JUnit 6 上で動作するか（内部 API 使用のため無検証では判断できない）
- JUnit 6 における `junit-vintage-engine` の削除方針の有無。非推奨であることは確認済み
  （migrating-from-junit4.adoc の `:21-23` "The JUnit Vintage engine is deprecated and should only be used
  temporarily while migrating tests to JUnit Jupiter or another testing framework with native JUnit Platform
  support."）だが、削除時期の記載は見つからなかった
- `resolveTestRules()` を実際に利用しているプロジェクトの有無と規模
- 「`getRequiredTestMethod()` が返す `Method` を拡張が自分で `invoke` するとテストが 2 回走る」ことの実測。
  JUnit 5 側の呼び出しが止まらないことは §2.2 のとおり出典があるが、2 回走る様子そのものは観測していない
- `@RepeatedTest` / `@TestTemplate` でルールが正しく適用されるか。プロトタイプで実測したのは
  `@ParameterizedTest` だけ。`@RepeatedTest` は `@TestTemplate` の一種なので
  `interceptTestTemplateMethod` を通るはずだが、実行して確かめてはいない。タスク #4 で検証する
- `Timeout` と `DbAccessTestExtension` の併用が実際に壊れること（§4.4 (3)）。スレッドが変わることと、
  素の `ThreadLocal` がテスト本体スレッドから見えないことは実測済みだが、
  **NTF の DB コネクションを使った組み合わせそのものは実行していない**

**`@Nested` については実測できたので、未確認から外した。**
JUnit 5.11.0 単体の最小再現（§4.2 後半と同じ手順）で確認したところ、
`@Nested` クラスを足すと **Extension のインスタンスが外側クラスと入れ子クラスで共有される**
（`identityHashCode` が一致する）。`postProcessTestInstance` は外側インスタンス・入れ子インスタンスの
両方に対して呼ばれるため、`support` フィールド（`TestEventDispatcherExtension.java:58`、代入は `:62`）が
後勝ちで上書きされ、`beforeEach` から見えるのは最後に生成されたものになる。
結果、ルールが記録するサポートインスタンスとテスト本体が参照するものが別になる。
（タスク #1 の QA レビューも同じ結論に達している。）

**これは TestRule 再現機構の問題ではなく、`support` フィールドを 1 枠しか持たない設計に起因する。**
よって**タスク #4 の対象外とする。** タスク #4 は「TestRule の適用先を分離する」ことに閉じ、
`support` の持ち方（`ExtensionContext.Store` へ移すなど）は別課題として立てる。
`8780eb8` の `TestRuleEmulationIntegrationTest` は、この理由でクラス Javadoc（`:51-55`）に
「`@Nested` は追加できない」と明記してある。

## 3. Design overview

### 3.1 What is the core idea, and why does it solve the problem?

**「TestRule をどこに適用するか」を 1 か所に決めるのをやめ、ルールの性質で適用先を分ける。**

過去の 2 つの実装（§1.5）は、いずれも全部を 1 か所に置いたために §2.2 のトレードオフをそのまま被った。

| | TestRule がテスト本体を包むか | NTF 前処理が `@BeforeEach` より先か |
|---|---|---|
| 初版 `ad2410b`（全部を `interceptTestMethod` へ） | ○ | × |
| PR #3 `148db9a` 以降の現行（全部を `beforeEach` へ） | × | ○ |
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
| `resolveInternalTestRules()`（新設、**`protected`**） | `TestName` / `TestDescription` を返す。`SimpleRestTestExtension`（別パッケージ）が override して `testDescription` を追加する。`TestEventDispatcherExtension` がクラス単位で `@Published(tag = "architect")` なので、**これは後方互換を保証する公開 API が 1 本増えることを意味する**（§2.2、§4.5 (2)、§5.1） |
| `interceptTestMethod` / `interceptTestTemplateMethod`（`InvocationInterceptor`、新設） | `resolveTestRules()` が返すルールで `invocation.proceed()` を包む |
| `resolveTestRules()`（既存。クラスの `@Published` により公開 API） | 利用者がテスト本体を包みたいルールを返す。基底実装は空リストを返すよう変更する（§4.5 (1)） |

### 3.3 How does work move?

```
1. BeforeEachCallback#beforeEach
     resolveInternalTestRules() を NOOP に対して適用 → テストメソッド名が確定
     support.dispatchEventOfBeforeTestMethod()
     （RestTestExtension はここで setUpDb()、DbAccessTestExtension は beginTransactions() を呼ぶ）
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

**この章は、本セッション内で判断1 の決定より前に作って検証したプロトタイプ（§0 の用語表）に基づく。**
プロトタイプはワーキングツリーから取り消してあり（変更を破棄して元の実装に戻してある）、コミットもしていない。
実装はタスク #4 で改めて行う。

### 4.1 変更差分（`src/main` のみ。**+68 / -15 行**）

**この行数はプロトタイプ実測であり、再現物はない。** 差分は git 履歴のどこにも残っていないため、
読み手が同じ数を取り直すことはできない。以下、この数字が出てくる箇所（§5.1 の比較表と推奨理由 2）にも同じ但し書きが付く。
**概算として読むこと。**

**下に載せるのは差分の全文ではない。** 変更点を読むための**要約 diff** で、`@@` にハンク範囲が入っていないため
`git apply` はできない。またこの差分には、`TestEventDispatcherExtension.java:149-168` の既存 Javadoc
（「親クラスが返したリストに追加する形でルールを追加すること」というコード例を含む）が入っていない。
そのまま当てると実装と食い違う Javadoc が `src/main` に残るため、**タスク #5 で Javadoc を直すことを前提とする。**

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

### 4.2 実測結果

**まず断っておく。下の表は再実行では確かめられない。** プロトタイプの差分はワーキングツリーから取り消してあり、
git 履歴にも残っていない。`DocTimeoutExampleSpikeTest` も存在しない（§1.1 実測2）。
**タスク #4 の実装時に、この表の各行を取り直す。**

以下はすべて JUnit 5.11.0 上で実行したと記録されている（JDK は特定できない。§0 冒頭）。
全体行は `mvn -o clean test`、個別行は `mvn -o clean test -Dtest=<クラス名>`。

| 確認項目 | 修正前 | 修正後 |
|---|---|---|
| `TestRuleEmulationIntegrationTest`（当時は実行順が `[rule-before, test, rule-after]` の 1 ケース。**現行の `8780eb8` は入れ子 2 本・5 要素・3 ケースで、当時のテストとは別物**） | FAIL | **PASS** |
| 解説書 rst:377-391 / rst:395-414 の `Timeout` の例（1 秒に対し 2 秒スリープ） | 2.143 s で BUILD SUCCESS | **`TestTimedOutException: test timed out after 1000 milliseconds`（0.976 s）** |
| `@ParameterizedTest` でルールが適用されるか | 未確認 | **PASS**（`interceptTestTemplateMethod` 経由） |
| `TestEventDispatcherExtensionLifecycleMethodTest`（NTF 前処理が `@BeforeEach` より先） | PASS | **PASS** |
| `RestTestExtensionTest` / `SimpleRestTestExtensionTest`（`testDescription` の設定） | PASS | **PASS** |
| `mvn -o clean test` 全体 | 32 件中 1 件失敗 | **32 件中 1 件失敗（§4.3 のとおり別の 1 件。仕様変更そのもの）** |

**本セッションで実測した最小再現（NTF 非依存）**

上の表が再現できない分を補うため、本文書の作成時に **JUnit 5.11.0 単体**で最小再現を作って走らせた。
§4.1 と同じ形の Extension —— `interceptTestMethod` の中で、渡されたルールを `invocation.proceed()` に巻いて
`evaluate()` するだけのもの —— を用意し、ルールと条件を差し替えて実行している。NTF には依存していない。

```
$ CP=<junit-jupiter-api/engine 5.11.0, junit-platform-{commons,engine,launcher} 1.11.0,
      opentest4j 1.3.0, apiguardian-api 1.1.2, junit 4.13.1, hamcrest-core 1.3 の jar>
$ javac -encoding UTF-8 -cp "$CP" -d out Spike.java
$ java -Dfile.encoding=UTF-8 -cp "$CP:out" <LauncherFactory で 1 クラスずつ実行するランナー>
```

| 渡したルール / 条件 | 実測結果 |
|---|---|
| `base.evaluate()` を 2 回呼ぶ（retry 相当） | **テスト本体が 1 回走ったのち** `org.junit.platform.commons.JUnitException: Chain of InvocationInterceptors called invocation multiple times instead of just once: org.junit.jupiter.engine.extension.TimeoutExtension, RetryExt` |
| `base.evaluate()` を呼ばない | `JUnitException: Chain of InvocationInterceptors never called invocation: ...`。テスト本体は走らない |
| `base.evaluate()` の前に `IllegalStateException` を投げる | **`IllegalStateException` がそのまま伝播。`JUnitException` にはならない** |
| `ExternalResource` を渡す | 実行順は `beforeEach → @BeforeEach → before() → テスト本体 → after() → @AfterEach → afterEach`。**`after()` が `@AfterEach` より前** |
| `Timeout(1000ms)` × 2 秒スリープ | `org.junit.runners.model.TestTimedOutException: test timed out after 1000 milliseconds`。テスト本体は `"Time-limited test"` スレッドで走り、**`afterEach` は本体の終了を待たずに実行された**（本体の終了ログが記録されないまま `afterEach` のログが出る） |
| `Timeout(1000ms)` × `ThreadLocal` | `@BeforeEach`（`main`）で設定した**素の `ThreadLocal` はテスト本体（`"Time-limited test"`）から `null`**。`InheritableThreadLocal` は引き継がれる。テスト本体が書いた値は `@AfterEach`（`main`）からは見えない |
| `@Nested` を持つクラス | Extension インスタンスが外側と入れ子で同一。`postProcessTestInstance` は両方に対して呼ばれ、後に生成されたものが残る（§2.3） |
| `@TestFactory`（`interceptTestFactoryMethod` / `interceptDynamicTest` を両方 override） | ファクトリ側のログは `factory-before → ファクトリ本体 → factory-after` で閉じ、**動的テストの本体はそのあとに走る**。`proceed()` の戻り値は遅延評価の `Stream`（`ReferencePipeline$Head`）。`interceptDynamicTest` の `ExtensionContext` では `getTestMethod()` が `Optional.empty`、`getRequiredTestMethod()` は `PreconditionViolationException` |

**この最小再現もリポジトリには残していない。** §4.2 前半と同じ弱点をそのまま持つので、
§4.6 で恒久的なテストとして起こす。

### 4.3 既存テストで 1 件だけ落ちる。それは仕様変更そのもの

```
$ mvn -o clean test
[ERROR] TestEventDispatcherExtensionTest.TestRuleエミュレート時に例外が発生した場合は
        _発生した例外を原因として持つ実行時例外がスローされること:135
        expected java.lang.RuntimeException to be thrown, but nothing was thrown
```

（この出力はプロトタイプ実行時に記録されたもので、**本文書の作成時には再取得していない。**
§4.2 前半の表の最終行と同じ実行に対応する。）

このテストは「`resolveTestRules()` が返したルールが `beforeEach` の中で評価され、
そこで起きた例外が `RuntimeException` に包まれる」ことを検証している
（出典: `git show 8780eb8:src/test/java/nablarch/test/junit5/extension/event/TestEventDispatcherExtensionTest.java`
の 122-139 行）。本設計はこの前提そのものを変えるので、テストは `interceptTestMethod` を対象に書き換える。
**落ちるのはこの 1 件だけ**であり、それは仕様変更そのものである。

例外の扱いがどう変わるか、なぜそうするかは §4.5 (4) に集約した。

### 4.4 再現できない範囲

タスク #5（Javadoc）とタスク #6（解説書）で、次の 6 点をすべて明記する。

**(1) `base` を呼ばないルール、`base` を 2 回以上呼ぶルールは使えない。**

`InvocationInterceptor` の契約は「`Invocation#proceed()` **または** `Invocation#skip()` をちょうど 1 回呼ぶ」
（§2.1）。§4.1 の実装は `proceed()` しか呼ばないので、両端でこの契約を外れる。

- **`base.evaluate()` を呼ばないルール**（テストを飛ばすもの）を渡すと `proceed()` も `skip()` も呼ばれず、
  `verifyInvokedAtLeastOnce()` が `JUnitException: Chain of InvocationInterceptors never called invocation: ...` を投げる。
  `skip()` を使えば飛ばすルールも表現しうるが、本設計では採らない。理由は §4.5 (3)
- **`base.evaluate()` を 2 回以上呼ぶルール**（retry / repeat 系）を渡すと、2 回目の `proceed()` で
  `markInvokedOrSkipped()` の `compareAndSet` が失敗し、
  `JUnitException: Chain of InvocationInterceptors called invocation multiple times instead of just once: ...` になる。
  **しかもテスト本体は 1 回走ってから失敗する**ので、副作用は残ったままになる。
  JUnit 4 の retry rule は広く使われるイディオムなので、`Timeout` と並ぶ代表例として解説書に書く

一方、**ルールが `base` を呼ぶ前に例外を投げた場合は、その例外がそのまま伝播する**（`JUnitException` にはならない）。
`verifyInvokedAtLeastOnce()` が `finally` ではなく `proceed()` の後に置かれているため（§2.1）。
「`base` を呼ばなければ必ず `JUnitException`」ではない、という書き分けが要る。

いずれも §4.2 後半で実測済み。

**(2) ルールが包むのはテストメソッドの呼び出しのみ。しかも後処理の順序が JUnit 4 と反転する。**

`@BeforeEach` / `@AfterEach` および NTF の前後処理は含まれない。JUnit 5 に、自身の
`BeforeEachCallback` を `Statement` で包む手段がないため（§2.2）。

帰結として、**`ExternalResource#after()` は `@AfterEach` より前に走る。**
JUnit 4 のランナーはルールを `@Before` / `@After` の外側に積んでいた（§2.1 の
`BlockJUnit4ClassRunner.java:319-321`）ので、`after()` は `@After` の**後**だった。
つまり修正後は、`@AfterEach` の後片付けより先にリソースが解放される。
**この順序の反転は例外にならず黙って起きる**ため、`TemporaryFolder` が作った一時ファイルを
`@AfterEach` から触っている、といったコードは静かに壊れる。§6 にも明記する。

実測: §4.2 後半の `ExternalResource` の行（`before() → テスト本体 → after() → @AfterEach`）。

**(3) `Timeout` と `DbAccessTestExtension` は併用できない（NTF との組み合わせは未実測）。**

これは §4.4 のなかで最も影響が大きい。**解説書が挙げている唯一の例が `Timeout` である**ため、
タスク #6 の解説書修正案にも必ず入れる（§6）。

**先に断る。この項の結論は出典から導いた推論であり、NTF の DB コネクションを使った組み合わせそのものは
実行して確かめていない（§2.3）。** 下の (a) は sources と実測の両方、(b) は実測、(c) は sources の読解、
(d) が (a)(b)(c) から導いた推論である。

(a) **`Timeout` はテスト本体を別スレッドで走らせ、素の `ThreadLocal` はそのスレッドから見えない。**
JUnit 4 の `Timeout` は `apply()` で `FailOnTimeout` を返し
（`junit-4.13.1-sources` `org/junit/rules/Timeout.java:153-155` → `:145-151`）、
`FailOnTimeout#evaluate()` は `"Time-limited test"` という名前の**新しいスレッド**を起こして
そこで `base.evaluate()` を走らせる（`org/junit/internal/runners/statements/FailOnTimeout.java:120-132`。
スレッド生成は `:123-124`、`start()` は `:127`）。

(b) §4.2 後半で、`@BeforeEach`（`main`）で設定した素の `ThreadLocal` がテスト本体
（`"Time-limited test"`）から `null` になることを実測した。`InheritableThreadLocal` は引き継がれる。

(c) **NTF の DB コネクションは素の `ThreadLocal` に置かれる。** `DbAccessTestExtension.java:19-23` は
`beforeEach` で `beginTransactions()` を呼び、それが
`nablarch/test/core/db/DbAccessTestSupport.java:95-117`（`:115` が `manager.beginTransaction()`）→
`nablarch/core/db/transaction/SimpleDbTransactionManager.java:35-52` を通る。
このメソッドは **2 か所**にスレッド束縛の状態を置く —— `:48` の
`DbConnectionContext.setConnection(dbTransactionName, dbConnection)` と、
`:52` の `TransactionContext.setTransaction(dbTransactionName, tran)`。
どちらの受け皿も素の `ThreadLocal` である（§2.1 の全数調査）。
（6-NEXT-SNAPSHOT の class を `javap -p -l` で逆アセンブルして LineNumberTable を照合したところ、
`beginTransaction` の `:47` `:48` `:50` `:51` `:52` は 2.2.0 sources と同じ行に対応していた。）

(d) **したがって、`DbAccessTestExtension` と `Timeout` を併用するとテスト本体から
DB コネクションもトランザクションも取れない**と考えられる。JUnit 4 のランナーではルールが
`@Before` / `@After` ごと包む（§2.1）ため、トランザクション開始もテスト本体も同じ
`"Time-limited test"` スレッドで起きて整合していた。本設計では `beforeEach` はメインスレッド、
テスト本体だけが別スレッドになる。

さらにタイムアウト成立時、`FailOnTimeout` はタイムアウト例外を作ったあと `finally` で
`thread.join(1)`（1 ミリ秒）しか待たずに抜ける（`FailOnTimeout.java:133-138`、待ち時間の判定は
`getResult` の `:153-168`）。つまり**テスト本体が走ったまま `@AfterEach` → `afterEach` →
`endTransactions()`（`DbAccessTestExtension.java:25-29`）が並行実行される競合が残る。**
§4.2 後半で、`afterEach` がテスト本体の終了を待たずに実行されることは実測した。

**他にスレッド束縛の状態を残す経路がないことは、§2.1 の全数調査で確かめた。**（「`beginTransactions()` を呼ぶ
Extension が `DbAccessTestExtension` だけ」というだけでは、これの証明にならない。）
`ThreadContext` が `InheritableThreadLocal` であることは、この絞り込みを支える有利な事実である一方、
**テスト本体が `ThreadContext` に書いた値は親スレッド（`@AfterEach` / `afterEach` 側）に戻らない**
という新しい制約を生む（§4.2 後半で実測済み）。

なお、`TestEventListener` の実装で `beforeTestMethod()` を override しているものは **NTF 内には存在しない**
（`TestEventListener.java:49` の `Template` の空実装のみ。`grep -rn "beforeTestMethod"` のヒットは
インタフェース宣言 `:22`・空実装 `:49`・`TestEventDispatcher.java:138` の呼び出しの 3 件だけ）。
ただし利用者は `SystemRepository` に独自リスナを登録できるため、「NTF 内には」という限定が要る。

**`RestTestExtension` / `SimpleRestTestExtension` はこの問題を持たない。**
`RestTestExtension#beforeEach`（`RestTestExtension.java:19-23`）は `super.beforeEach(context)`（`:21`）と
`setUpDb()`（`:22`）を呼ぶ。`super` は `SimpleRestTestExtension#beforeEach`（`SimpleRestTestExtension.java:24-28`）で、
そこから `SimpleRestTestSupport#setUp()` も呼ばれる（`:27`）。
その `setUp()`（`nablarch-testing-rest` sources `nablarch/test/core/http/SimpleRestTestSupport.java:85-90`）が
行うのは `setupDefaultProcessor()` と `initializeIfNotYet(config)` だけで、**DB には触れない。**
`setUpDb()` の側も `assertTableEquals` 系も
`TransactionTemplate#execute`（`nablarch-testing` sources `nablarch/test/core/db/TransactionTemplate.java:69-90`）
の中で 1 回の呼び出しごとにトランザクションを開始・終了する（`:71` `beginTransaction` → `:88`
`endTransactionQuietly`）。`beforeEach` をまたいで `ThreadLocal` にコネクションを残さないため、
スレッドが変わっても壊れない。

**(4) `@TestFactory` / `DynamicTest` には適用されない。タスク #4 の対象外とする。**

§4.1 の差分が override するのは `interceptTestMethod` と `interceptTestTemplateMethod` の 2 つだけ。
`InvocationInterceptor` にはこのほかに `interceptTestFactoryMethod` と `interceptDynamicTest` がある（§2.1 の表）。
override していないと既定実装が単に `invocation.proceed()` を呼ぶので、
**`@TestFactory` のテストでは利用者のルールが一切適用されず、しかも例外にならず黙って通る。**
§1.2 が「最も問題」とした「静かに成功する」が、この経路にはそのまま残る。

**対象外とする理由は、実装の難しさではなく意味論の不一致である。**
`interceptTestFactoryMethod` の戻り値が `T` であることは障壁にならない
（`AtomicReference<T>` や `Object[]` に受ければ `Statement` の `void` に収まる）。
そうではなく、**`@TestFactory` メソッドを包んでも、包まれるのは `Stream<DynamicNode>` の「生成」だけで、
動的テストの「実行」は包まれない。**

出典: `junit-jupiter-engine-5.11.0.jar` の
`org/junit/jupiter/engine/descriptor/TestFactoryTestDescriptor.class` を `javap -p -c -l` で逆アセンブルすると、
`invokeTestMethod` の実体（`lambda$invokeTestMethod$1`）は
`TestFactoryTestDescriptor.java:97-98` で `InterceptingExecutableInvoker.invoke(...)` を呼んでファクトリメソッドの
戻り値を受け取り（**インターセプタが挟まるのはここだけ**）、そのあと `:101-111` で
`toDynamicNodeStream` → `createDynamicDescriptor` → `dynamicTestExecutor.execute(...)` のループを回し、
`:115` で `awaitFinished()` する。**動的テストの実行はインターセプトの外側にある。**

§4.2 後半の最小再現でも実測した。`interceptTestFactoryMethod` のログは
`factory-before → ファクトリ本体 → factory-after` で閉じ、動的テストの本体はそのあとに走る。
`proceed()` が返すのは遅延評価の `Stream`（`java.util.stream.ReferencePipeline$Head`）で、
この時点では動的テストは 1 件も実行されていない。
したがって `Timeout` を `@TestFactory` に適用しても「`Stream` を作るのに 1 秒かかったか」しか測れず、
`ExternalResource` を適用すれば動的テストが 1 件も走らないうちに `after()` が呼ばれる。

`interceptDynamicTest` を override すれば動的テスト 1 件ずつを包むことはできる（各 `DynamicTest` を
テスト 1 件とみなすのは自然な対応づけである）。ただし**そのまま §4.1 の実装を流用することはできない。**
`interceptDynamicTest` に渡される `ExtensionContext` では `getTestMethod()` が `Optional.empty` を返し、
`getRequiredTestMethod()` は `PreconditionViolationException` を投げる（§4.2 後半で実測）。
`TestEventDispatcherExtension#convert`（`TestEventDispatcherExtension.java:143-147`）は
`getRequiredTestMethod()` を使って `Description` を作るので、動的テスト用に
`Description` の作り方を別に決める必要がある。加えて、ルールのインスタンスは 1 個しかないため、
N 件の動的テストに同じインスタンスを N 回 `apply` することになり、`ErrorCollector` のように
状態を溜めるルールの意味が変わる。

**「後から追加すればよい」では済まない**のはこの点で、追加のしかたを決めるには
「`TestRule` を `@TestFactory` / `DynamicTest` にどう対応づけるか」という仕様
（`Description` の作り方とルールインスタンスの寿命）を先に決める必要がある。
本設計はそこまで踏み込まないので、**明示的にタスク #4 の対象外とし、
「適用されない」ことを Javadoc（#5）と解説書（#6）に書く**という扱いにする。

**(5) `@Nested` を持つテストクラスでは正しく動かない（別課題）。** §2.3 のとおり、
`support` フィールドが後勝ちで上書きされる。TestRule 再現機構ではなく `support` の持ち方の問題なので、
タスク #4 の対象外とし、別課題として立てる。

**(6) 例外の扱いが、渡すリストによって変わる。** §4.5 (4) を参照。

### 4.5 記録しておく実装上の選択

**(1) `resolveTestRules()` の基底実装を `singletonList(support.testName)` から `emptyList()` に変える。**

代案は「基底実装はそのまま `testName` を返し、`interceptTestMethod` でも `beforeEach` でも
同じリストを適用する」だが、`TestName` が 2 回適用される。値は同じなので実害はないものの、
`resolveTestRules()` の意味が「利用者がテスト本体を包みたいルール」に定まらない。空リストを採る。

副作用として、解説書 rst:420-421 の「必ず親クラスの `resolveTestRules()` が返すリストをベースにすること。
そうしない場合、親クラスで登録している `TestRule` が再現されなくなる」が事実でなくなる。
rst:407-408 のコード例（`super.resolveTestRules()` を呼んでベースにする）も同様に成り立たなくなる（§6）。

**(2) `protected resolveInternalTestRules()` を新設する。公開 API が 1 本増えることを受け入れる。**

`TestEventDispatcherExtension` はクラス単位で `@Published(tag = "architect")` なので、
`protected` メソッドを 1 つ足すことは後方互換を保証する公開 API を 1 つ足すことに等しい（§2.2）。
**判断2 のなかで唯一、選択の余地がある点はここである。**

**このメソッドの位置づけを先に決めておく。** `resolveInternalTestRules()` は
**NTF 内部専用（`SimpleRestTestExtension` のような NTF 側の Extension が override するためのもの）であり、
利用者向け API ではない。** ただし `@Published(tag = "architect")` のクラスの `protected` メソッドである以上、
利用者が override することを止める手段はない。そこへルールを置くと
「テスト本体を包まない」＋「例外が `RuntimeException` に包まれる」という別の意味論になるため、
**タスク #5 の Javadoc に「利用者はこのメソッドを override しないこと。利用者のルールは
`resolveTestRules()` へ渡すこと」を明記する。**

**代替案は 2 つある。どちらも公開 API を 1 本も増やさない。**

**代替 A — 内部ルールの適用先を基底の `beforeEach` に固定する。**
基底の `applyInternalTestRules` は `Collections.singletonList(support.testName)` を直接使い、
`SimpleRestTestExtension` は自分の `beforeEach`（`SimpleRestTestExtension.java:24-28`。既に override 済み）の中で
`testDescription` を適用する。

- 順序の問題は起きない。`super.beforeEach(context)` の**前**に `testDescription` を適用すればよい
- ただし基底の `convert(ExtensionContext)`（`TestEventDispatcherExtension.java:143-147`）は `private` なので、
  `SimpleRestTestExtension` 側で `Description.createTestDescription(context.getRequiredTestClass(),
  context.getRequiredTestMethod().getName())` を書き直す必要がある。数行だが、**`Description` の作り方が
  基底と派生の 2 か所に散る**
- **§4.1 の差分は基底側も変わる。** `resolveInternalTestRules()` が丸ごと消え、
  `applyInternalTestRules` が固定リストを使う形になる。`SimpleRestTestExtension` 側だけの変更では済まない

**代替 B — `support` の `@Rule` 付きフィールドを反射で収集する。**
JUnit 4 の `BlockJUnit4ClassRunner#getTestRules`（`junit-4.13.1-sources` `:434-439`）が
`TestClass#collectAnnotatedFieldValues` / `getAnnotatedFieldValues`（`org/junit/runners/model/TestClass.java:226-236`）で
やっているのと同じことを、`support` のクラスに対して行う。

- 公開 API を 1 本も増やさず、NTF 側にルールが追加されても追随でき、
  「次に触る人が `resolveTestRules()` に内部ルールを戻す」事故も起きない
- **しかし、利用者の独自サポートクラスが宣言した `@Rule` フィールドまで拾ってしまう。**
  解説書の例（rst:377-391）の `CustomTestSupport` は `@Rule public Timeout timeout` を宣言しており、
  これが内部ルール側に落ちると**まさに直そうとしている不具合が再発する**（しかも利用者は同じ `timeout` を
  `resolveTestRules()` にも渡すので二重適用になる）。パッケージ名などで絞り込むことはできるが、
  利用者のサポートクラスがどこに置かれるかを前提にした heuristic になる
- 加えて、収集の順序が仕様で決まらない。`Class#getDeclaredFields` の Javadoc は
  「The elements in the returned array are not sorted and are not in any particular order.」と定めている
  （Temurin 21.0.11 の `lib/src.zip` → `java.base/java/lang/Class.java:2507-2508`）。
  ルールの適用順が入れ子の内外を決める以上、順序が決まらないのは望ましくない

**それでも新設（`resolveInternalTestRules()`）を採る。** 理由は 3 つ。

- 代替 B は不具合を再発させうる（上記）。絞り込みを入れても、その正しさが利用者のパッケージ構成に依存する
- 代替 A は `Description` の構築が 2 か所に散り、NTF 側にルールが増えるたびに派生 Extension の
  `beforeEach` を触ることになる
- 「内部で使うルール」という概念が名前で表れないと、次に触る人が
  `resolveTestRules()` に内部ルールを戻してしまう。§1.5 と同じ事故の再発を招く

**逆に言えば、公開 API を 1 本も増やしたくないという方針が優先されるなら、代替 A に切り替えてよい。**
その場合 §4.1 の差分は基底側・`SimpleRestTestExtension` 側の**両方**が変わる。

**(3) `skip()` は使わない。**

`InvocationInterceptor` の契約は `proceed()` **または** `skip()` を 1 回（§2.1）なので、
`skip()` を呼べば「テストを飛ばすルール」も表現しうる。しかし `TestRule` の側には
「`base` を呼ばなかった」ことを外から知る手段がない。`apply()` が返した `Statement` を `evaluate()` するだけでは、
その中で `base.evaluate()` が呼ばれたかどうかは分からない。呼ばれなかった場合にだけ `skip()` を呼ぶ、という
実装ができないため、本設計では採らない。結果として §4.4 (1) の制約が残る。

**(4) 例外の扱いが、同じ `TestRule` 型でもリストによって変わる。**

`applyInternalTestRules`（`beforeEach` 側）は例外を `RuntimeException` で包む。
`interceptTestMethod` 側は包まず、ルールが投げた例外をそのまま伝播させる。
**この非対称は意図的だが、対称ではない。**

- **包まない理由** — `ExpectedException` / `ErrorCollector` は例外でテストの成否を表現するため、
  包むと機能しなくなる。§4.3 の既存テスト 1 件が落ちるのはこの変更による
- **包む側を残す理由** — `beforeEach` は `throws Exception` しか宣言できず、`Statement#evaluate()` は
  `Throwable` を投げるため、そのままでは伝播させられない

利用者から見ると「同じ `TestRule` を `resolveTestRules()` に渡すか `resolveInternalTestRules()` に渡すかで
例外の扱いが変わる」ことになる。タスク #5 の Javadoc で両方に明記する。
解説書に入れるかどうかは §6 で判断する。

### 4.6 タスク #4 で恒久的なテストとして残すもの

§1.1 実測2 と §4.2 が「再現物なし」になった原因は、確認をスパイク（使い捨てのテストクラス）で行い、
その場で捨てたこと。同じ主張が二度と出典なしにならないよう、次をタスク #4 で**リポジトリに残るテストとして**追加する。
**新規は 7 件、既存の流用が 1 件。**

| # | 内容 | 対応する記述 | 新規か |
|---|---|---|---|
| 1 | 解説書 rst:377-391 / rst:395-414 と同じ形で `Timeout` を `resolveTestRules()` に渡し、タイムアウトが実際に発生すること | §1.1 実測2 | 新規 |
| 2 | `base` を呼ばないルールを渡すと `JUnitException`（`never called invocation`）になること | §4.4 (1) | 新規 |
| 3 | `base` を 2 回呼ぶルールを渡すと `JUnitException`（`multiple times`）になり、テスト本体は 1 回走っていること | §4.4 (1) | 新規 |
| 4 | ルールが `base` の前に投げた例外は、`JUnitException` に置き換わらずそのまま伝播すること | §4.4 (1)、§4.5 (4) | 新規 |
| 5 | `ExternalResource#after()` が `@AfterEach` より前に実行されること | §4.4 (2) | 新規 |
| 6 | 素の `ThreadLocal` に `beforeEach` で置いた値が、`Timeout` 配下のテスト本体から見えないこと | §4.4 (3) | 新規 |
| 7 | `@RepeatedTest` でルールが適用されること | §2.3 の未確認事項の解消 | 新規 |
| 8 | `@ParameterizedTest` でルールが適用されること | §4.2 | **既存**（`git show 8780eb8:src/test/java/nablarch/test/junit5/extension/event/TestRuleEmulationIntegrationTest.java` の `:162-166`） |

2〜4 は `InvocationInterceptor` の契約に触れる 3 つの形なので、1 クラスにまとめるのが自然。

**`Timeout` × `DbAccessTestExtension` の併用不可（§4.4 (3)）は、恒久テストに追加しない。**
理由は 2 つ。(a) DB コネクションを実際に張る必要があり、統合テスト環境に依存する。
(b) 失敗の現れ方（コネクションが取れない）は環境設定の誤りと区別しにくく、
テストが何を守っているのかが読み取れなくなる。代わりに、原因の機構そのものを上の表の 6 番で固定する。
**その結果、§4.4 (3) の結論は「未実測の推論」のまま残る**（§2.3）ので、
解説書の警告文（§6）にもその旨のラベルを引き継ぐ。

## 5. 判断ポイント

### 5.1 判断1 — `resolveTestRules()` を存続させるか（**決定済み**）

> **決定: 1-A（存続させて直す）。** 2026-08-21、`/rn:ty 1-a` によりユーザーが決定した。
> 以降のタスク #4〜#6 は 1-A を前提とする。以下は、その決定の根拠と、決定に伴って受け入れた制約の記録である。

**背景。** JUnit は JUnit 4 の Rule をネイティブサポートしないと明言し、移行用モジュール
`junit-jupiter-migrationsupport` は JUnit 6.0.0 で非推奨になり、**次のメジャーバージョンで削除される**と
公表されている（§2.1）。本モジュールの `resolveTestRules()` はその流れの外にある。だから
「直す」の前に「続けるか」を決める必要があった。

**選択肢**

| | 内容 |
|---|---|
| **1-A 存続して直す** | §4 の差分を入れる。解説書のコード例がそのまま動くようになる |
| **1-B 非推奨化して撤退** | `resolveTestRules()` に `@Deprecated` を付け、解説書から「TestRule を再現できる」という記述を撤回し、JUnit 5 の同等機能（`Timeout` → `@Timeout`、`ExternalResource` / `TemporaryFolder` → `BeforeEachCallback` / `@TempDir`、`ExpectedException` → `assertThrows`）への置き換えを案内する。実装は現状のままとし、`TestName` / `TestDescription` を設定する内部経路だけを残す |
| **1-C doc-only** | 実装も非推奨マークも変えず、「ルールはテスト本体を包まない」という制約を Javadoc と解説書に明記するだけ |
| **1-D 1-A + `@Deprecated` 併走** | §4 の差分を入れて直したうえで、同時に `resolveTestRules()` に `@Deprecated` を付け、JUnit 5 の機能への移行を促す |

**比較**

| 判断軸 | 1-A 存続 | 1-B 撤退 | 1-C doc-only | 1-D 併走 |
|---|---|---|---|---|
| **§1.2 の不具合が直るか**（ルールがテスト本体を包まない。解説書の `Timeout` の例・`ExternalResource` の後処理順・NTF 公開 API の `SystemPropertyResource` は、いずれもこの 1 つの不具合の現れ方） | 直る（§4.2。ただし再現物なし） | 直らない。「動かない」と明示し、記述を撤回する | 直らない。「動かない」と書くだけ | 直る |
| **JUnit の公表方針との整合**（§2.1。JUnit 4 rule support は 6.0.0 で deprecated for removal） | **逆行する。** JUnit が捨てる方向の機構を、動くように整備する | **沿う。** 移行先を示して縮小する | 中立。現状を説明するだけ | **逆行する。** 直したうえで非推奨にするので、方向としては 1-A と同じ |
| **公開 API への影響** | ルールの実行位置が変わる（下記の非互換）。`protected resolveInternalTestRules()` が 1 本増える（§4.5 (2)） | `@Deprecated` が付く。**加えて `resolveInternalTestRules()` 相当の分離が結局必要**（下記） | なし | 1-A と同じ + 非推奨マーク |
| **保守コスト** | **恒久的な制約 4 件（§4.4 (1)(2)(3)(4)）・非対称な例外ポリシー（§4.5 (4)）・後方互換を保証する公開 API 1 本**を抱え続ける | 縮小方向。移行が済めば JUnit 4 の Rule に関する説明は不要になる | 制約は残るが、動かすための機構は増えない | 1-A と同じ。加えて非推奨警告の運用 |

**1-B でも `resolveInternalTestRules()` 相当の分離は必要になる。** 1-B は実装を変えないが、
内部経路（`testName` / `testDescription` の設定）は `resolveTestRules()` を通ったままで、
`SimpleRestTestExtension` がそれを override している（`SimpleRestTestExtension.java:30-35`）。
そこへ `@Deprecated` を付けると、**NTF 自身のビルドが自分の非推奨 API で警告を出す。**
警告を消すには内部経路を別メソッドへ分離するしかない。

**変更範囲**

- **1-A** — `src/main` +68/-15 行（§4.1。**プロトタイプ実測。再現物なし・概算**）+ 既存テスト 1 件の書き換え
  + 新規テスト 7 件（§4.6）+ Javadoc（#5）+ 解説書 4 か所（#6。うち 1 か所は新規追加）
- **1-B** — `@Deprecated` 1 行 + 内部経路の分離（上記）+ Javadoc（#5）+ 解説書の当該節の書き直し（#6）
  + `TestRuleEmulationIntegrationTest` の処遇（下記）
- **1-C** — Javadoc（#5）+ 解説書 2 か所（#6）+ `TestRuleEmulationIntegrationTest` の処遇（下記）
- **1-D** — 1-A と同じ + `@Deprecated` 1 行

**既存利用者の移行コスト**

- **1-A** — **ゼロではない。** 実行位置が `beforeEach` からテストメソッド直前へ移る／
  `ExternalResource#after()` が `@AfterEach` より前に走るようになる（§4.4 (2)）／
  例外が `RuntimeException` に包まれなくなる（§4.5 (4)）／`base` を 2 回呼ぶルールが使えなくなる（§4.4 (1)）／
  `Timeout` と `DbAccessTestExtension` の併用が壊れる（§4.4 (3)）／`@TestFactory` では適用されない（§4.4 (4)）
- **1-B** — ルールごとに JUnit 5 の機能へ書き換え
- **1-C** — なし（動きは変わらない）
- **1-D** — 1-A と同じ。加えて非推奨警告への対応

**1-B / 1-C を採った場合、`TestRuleEmulationIntegrationTest` をどうするか。**
本セッションのタスク #1 で追加したこのテスト（`8780eb8`）は、現行実装に対して FAIL する（§1.1 実測1）。
1-B / 1-C では実装が変わらないので、このテストは削除するか、「ルールはテスト本体を包まない」ことを
固定する期待値へ反転させるかのどちらかになる。反転させる場合、**現在の壊れた挙動が仕様として固定される**
ことになるので、`@Deprecated` を付ける 1-B と組み合わせるのが自然。

**「JUnit 4 から離れる」ことにはどれもならない、という点が判断の要。**
NTF 本体の `TestEventDispatcher#testName` が JUnit 4 の `@Rule TestName` であり、
`DbAccessTestSupport` などが `@Before` / `@After` を使っている（§2.1 の表）。本モジュールの
`junit:junit:4.13.1` は compile スコープで、`resolveTestRules()` の存廃とは無関係に残る。
JUnit の流れに沿わせる本丸は §5.3 の別課題であって、判断1 ではない。
上の比較表の「JUnit の公表方針との整合」は、あくまで `resolveTestRules()` という 1 つの機構の向きの話である。

**推奨は 1-A だった。理由は 3 つ。**

1. **1-B / 1-C は不具合を残す。** `ExternalResource` の後処理がテスト本体より前に走る状態は、
   非推奨マークを付けても文書に書いても消えない。移行が済むまで誤った実行順で動き続ける。
   §1.2 のとおり、NTF 自身が公開している `SystemPropertyResource` も対象に含まれる
2. **コード変更は本モジュールに閉じる。** `src/main` は +68/-15 行で、既存テストは 1 件しか落ちず、
   それは仕様変更そのもの（§4.3）。**ただしこの行数と件数はプロトタイプ実測であり、再現物がない**
   （§4.1、§4.2）。**またドキュメント変更は閉じない。** タスク #6 は別リポジトリ
   `nablarch-document` の 4 か所の修正を必要とする（§6）。これはどの選択肢でも同じ
3. **1-A のあとで 1-B へ進みやすい。** `resolveTestRules()` の意味が
   「利用者がテスト本体を包みたいルール」に定まるので、後から非推奨にする判断がしやすくなる。
   （**逆向きが不可能なわけではない。** 1-B を選んでも `@Deprecated` を外して §4 の差分を入れる道は塞がらない。
   「1-B → 1-A は不可能」とは言えない）

**1-D を採らなかった理由。** 直したうえで同時に非推奨にすると、利用者に対して
「動くようにしたので使ってよい／使うのをやめてほしい」という 2 つのメッセージを同時に出すことになる。
JUnit 4 依存の解消は §5.3 の別課題であり、その方針が決まる前に非推奨マークだけ先に付けると、
移行先を示せないまま警告だけが出る。**非推奨化は §5.3 の結論とセットで判断する**のが筋。

**1-A を選んだことで受け入れた非互換。** §4.4 の (1)〜(6) がそのまま非互換の一覧になる。加えて、
`resolveTestRules()` に**前処理だけのルール**（`TestName` / `TestDescription` 相当）を渡していた場合、
その実行位置が `beforeEach`（NTF 前処理と同時）からテストメソッド直前（`@BeforeEach` の後）へ移る。
**移行先は `resolveInternalTestRules()` である**（§6 で解説書にも書く）。
また、ルールが投げた例外が `RuntimeException` に包まれなくなる（§4.5 (4)）。
`resolveTestRules()` を実際に使っているプロジェクトの有無は未確認（§2.3）。

### 5.2 判断2 — 直し方（**§4.5 (2) の 1 点を除き、要件を満たす形が 1 つしかない**）

「テスト本体を包む」ためには `invocation` を引数で受け取る必要があり、それができる拡張ポイントは
`InvocationInterceptor` しかない（§2.2）。そして `InvocationInterceptor` は `@BeforeEach` より後に
呼ばれる（§2.1）ため、NTF の内部ルールをそちらへ移すと `TestEventDispatcherExtensionLifecycleMethodTest` が壊れる。
したがって「利用者のルールを `InvocationInterceptor` へ、内部ルールを `beforeEach` に残す」以外に形がない。

**選択肢が思いつかないのではなく、要件を満たすものが 1 つしかない、という意味である。**
たとえば型ごとのアダプタを書く道（JUnit 公式の `junit-jupiter-migrationsupport` 方式）は存在するが、
対応できるのが 3 種類だけで `Timeout` を含まず（§2.1）、解説書が唯一の例に挙げている `Timeout` を救えない。
そのうえ同モジュールは次のメジャーで削除される。実装量も §4 の差分より大きい。要件を満たさないので採れない。

**唯一残る選択の余地は §4.5 (2) の 1 点** —— 内部ルール用の `protected` メソッドを新設して
公開 API を 1 本増やすか、増やさない代替（代替 A / 代替 B）を採るか。
そこには 3 案の比較と推奨、および方針が変わった場合の切り替え先を書いてある。
そのほかの実装上の細部（(1)(3)(4)）は §4.5 に記録した。

### 5.3 判断1 の外にある別課題 — NTF 本体から JUnit 依存を分離する

**結論から言うと、これは本件とは別に立てるべき課題である。** NTF 本体の変更を伴うため本リポジトリだけでは完結せず、
既存の JUnit 4 利用者への後方互換の検討も必要になる。本件の不具合修正と同じ土俵で決めるべきではない（§1.4）。

**何をする課題か。** NTF のロジックを JUnit のライフサイクル注釈から切り離し、JUnit 4 用・JUnit 5 用の
薄いアダプタを両側に置く。`TestEventDispatcher#testName` を素のフィールドにできれば、
TestRule 再現機構は NTF 内部からは不要になり、利用者向けに残すかどうかを純粋に方針として決められる。
JUnit の流れに沿うのはこれ。§5.1 で「非推奨化は §5.3 の結論とセット」と書いたのはこの意味。

**規模感。** §2.1 のとおり、`org.junit` を import しているのは 185 ファイル中 9 ファイル。
内訳は**ライフサイクル注釈・ルールを使っているものが 4 ファイル**
（`TestEventDispatcher` / `DbAccessTestSupport` / `IntegrationTestSupport` / `SystemPropertyResource`）、
**表明に `org.junit.Assert` を使っているものが 5 ファイル**（うち 4 ファイルは静的 import のみ。
`Assertion.java:13-14` だけは `org.junit.Assert` / `org.junit.ComparisonFailure` を通常の import で参照している）。

**技術的な障壁は、内訳を直したあとでも低いと言える。** 表明側の 5 ファイルは
`org.junit.Assert` の呼び出しを別の手段（Hamcrest 直呼びなど）に置き換えれば済み、
`Assertion.java` が `ComparisonFailure` を投げている点だけは代替の型を決める必要がある。
残る 4 ファイルが本題だが、`SystemPropertyResource` は `ExternalResource` を捨てて素のクラスにできるし、
`IntegrationTestSupport` は `@Before` が 1 つだけである。**ただしこの規模感は import の分布から見た印象であり、
実際の分離作業は行っていない（未確認）。**

## 6. 解説書の通りになるか（タスク #6 の下ごしらえ）

**1-A（存続させて直す）を選んだ前提での話。コード例はほぼそのまま動くようになるが、4 か所直す必要がある**
（既存 3 か所の修正 + 新規 1 か所の追加）。

出典はいずれも `nablarch/nablarch-document` のコミット `5391d5c`（`origin/main`、2026-08-05）の
`ja/development_tools/testing_framework/guide/development_guide/06_TestFWGuide/JUnit5_Extension.rst`。

| 解説書の箇所 | 修正後の状態 |
|---|---|
| rst:377-391 `CustomTestSupport`（`@Rule Timeout` を持つ独自サポートクラス）の例 | そのまま。変更不要 |
| rst:395-414 `CustomTestSupportExtension`（`resolveTestRules()` のオーバーライド）の例 | **要修正。** `:407-408` に `// 2. 親クラスの resolveTestRules() の結果をベースにしてリストを生成する` と `List<TestRule> rules = new ArrayList<>(super.resolveTestRules());` があり、基底実装が空リストを返すようになる（§4.5 (1)）ため成り立たない。`new ArrayList<>()` から始める形に書き換え、コメント 2 を削る |
| rst:416-418「これにより、JUnit 5のテスト上でもJUnit 4の `TestRule` を再現できるようになる」 | **要修正。** §4.4 の (1)(2)(4) を追記する。すなわち、包む範囲がテストメソッドのみで `@BeforeEach` / `@AfterEach` を含まず **`ExternalResource#after()` は `@AfterEach` より前に実行される**こと、`base` を呼ばないルール・2 回以上呼ぶルール（retry 系）は使えないこと、`@TestFactory` / `DynamicTest` には適用されないこと |
| rst:420-421「必ず親クラスの `resolveTestRules()` が返すリストをベースにすること。そうしない場合、親クラスで登録している `TestRule` が再現されなくなる」 | **要修正。** 基底実装が空リストを返すようになるため、この理由づけは成り立たなくなる（§4.5 (1)）。あわせて**移行手順**を書く: 「前処理だけのルール（`TestName` / `TestDescription` 相当）をこれまで `resolveTestRules()` に渡していた場合は、`resolveInternalTestRules()` へ移すこと」（§5.1 の非互換） |
| （新規）rst:395-414 の直後 | **要追加。** §4.4 (3) の警告。**この節の唯一の例が `Timeout` である以上、これは必須。** `Timeout` はテスト本体を別スレッドで実行するため、`DbAccessTestExtension`（`@DbAccessTest`）と併用するとテスト本体から DB コネクションが取れない。またタイムアウト成立時にテスト本体と後処理が並行実行される。**この警告は出典から導いた推論であり、NTF との組み合わせそのものは未実測**（§4.4 (3)、§2.3）なので、差分案を出すときにその旨をレビュー依頼に添える |

**例外の扱いの変更（`RuntimeException` に包まれなくなる。§4.5 (4)）は、解説書には入れない。**
当該節（rst:370-421）に例外の扱いを説明した記述がなく、書かれていない前提が変わっただけだからである。
Javadoc（タスク #5）には両方のメソッドに明記する。**判断したことを残すためにここに書いておく。**

**利用者が書くコードはほとんど変わらない。** 解説書の手順どおりに `resolveTestRules()` をオーバーライドすれば、
`Timeout` は実際にタイムアウトし、`ExternalResource` の後処理はテスト本体の後に実行される。
変わるのは `super.resolveTestRules()` を呼ぶ 1 行と、§4.4 の制約の範囲内で、である。

これはタスク #6 で ja / en 両方の差分案として起こす。en 側の対応箇所の行番号は**未確認**。
