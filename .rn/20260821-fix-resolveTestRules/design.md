# TestRule 再現機構 — design notes

Not read at runtime — for whoever maintains the design and needs to judge whether a decision is still
right when requirements change.

調査日 2026-08-21 / JUnit 5.11.0・junit:junit 4.13.1

本モジュールのコンパイル対象は Java 17（親 pom `nablarch-parent-6-NEXT-SNAPSHOT.pom:46-47` の
`maven.compiler.source` / `maven.compiler.target`）。**§4.2 のプロトタイプは同一セッション内で実行したが、
そのときの JDK を記録し損ねた（未確認）。** 本文書の作成時に実行した最小再現（§4.2 後半・§4.4）は
Temurin 21.0.11 で走らせた（`java -version` の出力）。

## 0. この文書の読み方

**これは決定済みの記録である。** 判断1（`resolveTestRules()` を存続させるか撤退するか）は 2026-08-21 に
**1-A（存続させて直す）** で決定した（§5.1）。判断2（直し方）は**要件を満たす形が 1 つしかない**ため、
決定事項として §4 に記録する（§5.2）。判断2 のなかで唯一選択の余地があった §4.5 (2)
（内部ルール用の `protected` メソッドを新設するか）も、
**新設で決定。ただし「公開 API を増やさない」が優先方針になった場合の切り替え先として代替 A を残す。**
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
| **タスク #1 / #4 / #5 / #6** | `steering.md` の Tasks に定義された作業単位。#1 = 不具合を固定するテストを追加する、#4 = TestRule の適用先を分離する（実装）、#5 = Javadoc を実装と一致させる、#6 = 解説書の修正差分案を作成する |
| **プロトタイプ** | 本セッション内で、判断1 の決定より前に `src/main` を実際に書き換えて動かし、検証後に取り消した実装。コミットしていないため git 履歴に残っていない（§4.2）。タスク #4 の実装（`0c2047f`）とは別物 |

**行番号と出典の基準**

- `src/main` / `pom.xml` の行番号は、断りのない限り本リポジトリのコミット `b2ecc31` 時点（タスク #4 の実装より前）
- **タスク #4 の実装後の `src/main` を指すときは、コミット `231eaa9` を明示する**（§4.1）
- **`src/test` を参照するときは毎回コミットを明示する。** 本セッションのタスク #1 と #4 で改訂したため
- 外部の jar は sources jar を `~/.m2/repository` から展開して確認した。sources jar がないものは
  `javap -p -c -l` の LineNumberTable で照合した

**要約**

- **何が壊れているか** — 利用者が `resolveTestRules()` に渡した `TestRule` が、テスト本体を包まずに
  `beforeEach` の中で前処理・後処理をまとめて済ませてしまう。解説書が唯一の例に挙げる `Timeout` は何も検知せず、
  `ExternalResource` の後処理はテスト本体より前に走り、`ErrorCollector` は黙って成功する（§1.1、§1.2）
- **どう直すか** — 利用者のルールだけを `InvocationInterceptor#interceptTestMethod` へ移し、NTF が内部で使う
  ルール（`TestName` / `TestDescription`）は `beforeEach` に残す。内部用に
  `protected resolveInternalTestRules()` を新設する（§3、§4.1）。**公開 API は 3 本＋interface 1 つ増える**（§4.5 (2)）
- **受け入れた制約** — ルールが包むのはテストメソッドの呼び出しだけで、`@BeforeEach` / `@AfterEach` は含まれない。
  前処理・後処理の位置が JUnit 4 とずれ、`@BeforeEach` が失敗するとルールが一切走らない。`base` を呼ばない／
  2 回呼ぶルール（skip 系・retry 系）は使えず、`Timeout` と `DbAccessTestExtension` は併用できない。
  例外が `RuntimeException` に包まれなくなる（§4.4、§5.1）。intercept メソッドを `final` にするため、
  自分で override していた利用者はコンパイルエラーになる。**割り込みは別の Extension クラスへ切り出せるが、
  そこからは基底の `protected support` フィールドに届かない**（§4.5 (5)）
- **直せなかったもの** — `@TestFactory` / `DynamicTest` にはルールが適用されない。`@Nested` は `support`
  フィールドの持ち方に起因する別課題。どちらもタスク #4 の対象外（§4.4 (6)(7)）

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

実行順が `[outer-before, inner-before, inner-after, outer-after, test]` になり、期待する
`[outer-before, inner-before, test, inner-after, outer-after]` と食い違って FAIL する。このクラスの失敗は 3 件で、
`@Test` 1 件と `@ParameterizedTest` 2 件（`@ValueSource(ints = {1, 2})`）がいずれも同じ形で落ちる。
2 本のルール（`outer` / `inner`）を入れ子にして渡し、`@AfterEach` で実行ログを表明する構成である。
`mvn -o clean test` 全体では `Tests run: 34, Failures: 3, Errors: 0, Skipped: 0` だった。

出典: テストは `git show 8780eb8:src/test/java/nablarch/test/junit5/extension/event/TestRuleEmulationIntegrationTest.java`
（表明は `:168-178`、ルールは `:69-92`、Extension は `:129-153`）。失敗の内容と件数は、当時
`target/surefire-reports/` にあった同名の `.txt`（生成時刻 2026-08-21 20:38。`8780eb8` のコミット時刻 20:34 より後）と
`TEST-*.xml` の `tests` / `failures` 属性から読んだ。**`target/` はビルドのたびに作り直されるため、この出力は
もう開けない。** 同じ主張は §4.6 の `TestRuleEmulationIntegrationTest` で恒久テストにしてあり、そちらが正である。

**実測2 — 解説書の `Timeout` の例が機能しない**（**再現物なし**）

解説書 rst:377-391 / rst:395-414 のコード（`Timeout(1000, MILLISECONDS)` を `resolveTestRules()` で追加）を
そのまま写した `DocTimeoutExampleSpikeTest` を同一セッション内で作って実行し、2 秒スリープするテストメソッドが
1 秒のタイムアウトに対して 2.143 秒かかって BUILD SUCCESS になるのを確かめた。
**ただしこのクラスはワーキングツリーにも全ブランチの git 履歴にも存在せず**
（`git log --all --diff-filter=A --name-only` と `find` のいずれでもヒットしない）、再実行で確かめられる出典はない。
そこでタスク #4 で、解説書の `Timeout` の例が実際にタイムアウトすることを**恒久的なテストにした**
（`git show 231eaa9:src/test/java/nablarch/test/junit5/extension/event/TimeoutRuleIntegrationTest.java` の `:77-84`。§4.6）。
この段落自体は「実測したという記録」であって、読み手が確かめられる事実ではない。

### 1.2 What goes wrong without this?

**壊れているのは「テスト本体の実行を必要とするルール」すべて。**

下の表は、現行実装と同じ形の Extension —— `beforeEach` の中で空の `Statement` にルールを適用して
`evaluate()` し、例外を `RuntimeException` で包むもの —— を **JUnit 5.11.0 単体**で組んで実測した結果である
（手順は §4.2 後半と同じ。NTF には依存していない）。

| ルールの種類 | 現状（実測） |
|---|---|
| `TestName` のように前処理だけのもの | 動作する（テスト本体から `getMethodName()` が `"t"` を返す） |
| `Timeout` のようにテスト本体の実行を監視するもの | 何も検知しない。1 秒のタイムアウトに対し 2 秒スリープするテストが**成功する**（実測2 と同じ結果） |
| `ExternalResource` / `TemporaryFolder` のように後処理を持つもの | 前処理も後処理もテスト本体より前に実行される（実行順 `res-before → res-after → @BeforeEach → test → @AfterEach`。実測1 と同じ形） |
| `ErrorCollector` のようにテスト本体から結果を集めるもの | **黙って成功する。** `verify()` が `beforeEach` の中で終わっているため、テスト本体が `addError()` したエラーは誰も検証しない |
| `ExpectedException` のようにテストの結果を見るもの | 期待を設定しても適用されず、テストは投げた例外そのもの（`IllegalArgumentException`）で失敗する |

**NTF が内部で使う 2 つのルールは壊れない。** どちらも `Description` からテストメソッド名を控えるだけで、
テスト本体を必要としないため。

- `TestEventDispatcher#testName` — `org.junit.rules.TestName`。`nablarch-testing` 6-NEXT-SNAPSHOT sources
  `nablarch/test/event/TestEventDispatcher.java:92-94`（`@Rule public final TestName testName`）
- `SimpleRestTestSupport#testDescription` — `nablarch.test.core.rule.TestDescription`。**所属アーティファクトは
  `nablarch-testing` ではなく `nablarch-testing-rest`。** 6-NEXT-SNAPSHOT sources
  `nablarch/test/core/rule/TestDescription.java`（`TestWatcher` を継承し、`starting(Description)`（`:15-19`）で
  `clazz` と `methodName` を控えるだけ）。フィールドは同 sources
  `nablarch/test/core/http/SimpleRestTestSupport.java:77-79`

**しかし「壊れるのは利用者の自作ルールだけ」ではない。** `nablarch-testing` sources の
`nablarch/test/SystemPropertyResource.java:23-24` は
`@Published(tag = "architect") public class SystemPropertyResource extends ExternalResource` であり、
`after()`（`:36-39`）でシステムプロパティをテスト実行前の状態に戻す。JUnit 5 の利用者がこれを
`resolveTestRules()` に渡すと、この復元処理がテスト本体より前に走り、しかも例外にならずテストは通る。
**NTF が提供している公開 API のルールが、NTF の JUnit 5 拡張の上で黙って壊れる。失敗の仕方が
「静かに成功する」であることが最も問題で、利用者は誤った安心を得る。**

### 1.3 What does reaching it require?

次の 2 つを同時に満たす必要がある。片方だけなら過去に達成されているが、両立していない。

1. 利用者が `resolveTestRules()` で追加した `TestRule` が、テストメソッドの実行を包むこと
2. NTF の前処理（`dispatchEventOfBeforeTestMethod`、`testName` / `testDescription` の設定）が、
   利用者の `@BeforeEach` より先に実行されること

2 が必要な理由は、JUnit 4 では `TestEventDispatcher#dispatchEventOfBeforeTestMethod` が親クラスの `@Before` であり、
テストクラス側の `@Before` より必ず先に実行されていたため（`TestEventDispatcher.java:134-140`）。また
`RestTestExtension#beforeEach` が呼ぶ `setUpDb()` は `testDescription.getMethodName()` を参照する
（`nablarch-testing-rest` 6-NEXT-SNAPSHOT sources `nablarch/test/core/http/RestTestSupport.java:79-82`。参照は `:81`）。

出典: `src/main/java/nablarch/test/junit5/extension/http/RestTestExtension.java:19-23`、
`git show 8780eb8:src/test/java/nablarch/test/junit5/extension/event/TestEventDispatcherExtensionLifecycleMethodTest.java`
の 29-34 行（`@BeforeEach` の中で `support.testName.getMethodName()` が `"test"` になっていることを表明している）

### 1.4 What is out of scope?

- **解説書本体（nablarch/nablarch-document）の修正。** 別リポジトリのため、本セッションでは差分案の作成までとする（タスク #6）
- **NTF 本体（nablarch-testing）の変更。** §5.3 の別課題にあたる。本リポジトリだけでは完結しない
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
(1) NTF 自身が使うルールが 2 つとも前処理だけのもので、適用先が移っても壊れなかった（§1.2）。
(2) PR #3 が追加した検証が `testName.getMethodName()` の設定だけを見ており、ルールがテスト本体を包むかは
対象外だった。出典: `git show 8780eb8:src/test/java/nablarch/test/junit5/extension/event/TestEventDispatcherExtensionTest.java`
の 110-120 行（`beforeEachを実行すると_TestRuleのエミュレートが行われることをテスト`）。

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

出典: `junit-jupiter-engine-5.11.0.jar` の `TestMethodTestDescriptor.class` を `javap -p -c -l` で逆アセンブルし、
`execute` の LineNumberTable（`TestMethodTestDescriptor.java:129-147`）と呼び出し順を突き合わせた。
`:217` は `lambda$invokeTestMethod$8` が `InterceptingExecutableInvoker.invoke(...)` を呼んでいる行。

**`TestRule` の性質**

`TestRule#apply(Statement base, Description description)` の `base` は「テストメソッドを呼び出す処理」であり、
ルールは `base.evaluate()` を自分で呼ぶことで、自分の処理をテストの前に置くか後に置くかを決める。
JUnit 4 のランナーはこの `Statement` を入れ子に積み上げてテスト 1 件の実行を構成し、
`BlockJUnit4ClassRunner#methodBlock` は `withRules` を `withBefores` / `withAfters` の**外側**に積む。
つまり JUnit 4 ではルールが `@Before` / `@After` ごとテストを包んでおり、**`ExternalResource#before()` は
`@Before` の前、`after()` は `@After` の後に実行されていた。** 出典: `junit-4.13.1-sources.jar` →
`org/junit/rules/ExternalResource.java:42-67`、`org/junit/runners/BlockJUnit4ClassRunner.java:303-324`
（`:316` methodInvoker → `:319` withBefores → `:320` withAfters → `:321` withRules の順に包む）

**`InvocationInterceptor` の契約**

> Each method in this class must call {@link Invocation#proceed()} or {@link
> Invocation#skip()} exactly once on the supplied invocation. Otherwise, the
> enclosing test or container will be reported as failed.

出典: [InvocationInterceptor.java:35-37](https://github.com/junit-team/junit5/blob/r5.11.0/junit-jupiter-api/src/main/java/org/junit/jupiter/api/extension/InvocationInterceptor.java)（タグ r5.11.0）。
`Invocation#skip()` は `:236-245` の `default` メソッドで、「This allows to bypass the check that `proceed()`
must be called at least once」と書かれている。実装側でもこの契約が確認できる。`junit-jupiter-engine-5.11.0.jar` の
`InvocationInterceptorChain$ValidatingInvocation` には**2 方向の検査**がある。どちらも呼ばれないまま終わると
`verifyInvokedAtLeastOnce()`（`InvocationInterceptorChain.java:148-151`）が
`"Chain of InvocationInterceptors never called invocation"` で、2 回以上呼ばれると
`markInvokedOrSkipped()`（`:142-145`）が
`"Chain of InvocationInterceptors called invocation multiple times instead of just once"` で失敗させる。
いずれも `fail(String)`（`:154-156`）が投げる `org.junit.platform.commons.JUnitException` である。

**ただし `chainAndInvoke` は `verifyInvokedAtLeastOnce()` を `finally` ではなく `proceed()` の後に置いている**
（`:45-46`）ため、**ルールが `base` を呼ばずに例外を投げた場合はその例外がそのまま伝播し、
`JUnitException` にはならない。**（以上は `javap -p -c -l` の LineNumberTable で確認。
`chainAndInvoke` に例外テーブルはない。）

**`InvocationInterceptor` が持つ intercept メソッド**

`javap -p org/junit/jupiter/api/extension/InvocationInterceptor.class`（`junit-jupiter-api-5.11.0.jar`）の出力より。

| メソッド | 戻り値 | 対象 |
|---|---|---|
| `interceptTestMethod` | `void` | `@Test` |
| `interceptTestTemplateMethod` | `void` | `@TestTemplate`（`@ParameterizedTest` / `@RepeatedTest` を含む） |
| `interceptTestFactoryMethod` | **`T`** | `@TestFactory` |
| `interceptDynamicTest`（2 オーバーロード） | `void` | `DynamicTest` |
| `interceptTestClassConstructor` / `interceptBeforeAllMethod` / `interceptBeforeEachMethod` / `interceptAfterEachMethod` / `interceptAfterAllMethod` | — | 本設計の対象外 |

**JUnit 4 の Rule について JUnit が公表している方針**

> As stated above, JUnit Jupiter does not and will not support JUnit 4 rules natively.

移行用の `junit-jupiter-migrationsupport` が対応するのは `ExternalResource`（`TemporaryFolder` を含む）/
`Verifier`（`ErrorCollector` を含む）/ `ExpectedException` の 3 種類のみで、`Timeout` は含まれない。
そのうえ **JUnit 4 rule support は 6.0.0 で deprecated for removal** とされ、同モジュール自体も
**「次のメジャーバージョンで削除される」**と公表されている。
出典: [migrating-from-junit4.adoc](https://github.com/junit-team/junit-framework/blob/r6.1.3/documentation/modules/ROOT/pages/migrating-from-junit4.adoc)（タグ r6.1.3）
の `:267-268`・`:270`・`:274-277`・`:279-284`、および
[release-notes-6.0.0.adoc:195-196](https://github.com/junit-team/junit-framework/blob/r6.0.0/documentation/src/docs/asciidoc/release-notes/release-notes-6.0.0.adoc)
（タグ r6.0.0。JUnit 6.0.0 のリリース日は同ファイル `:4` に「September 30, 2025」）。

JUnit 4 本体については「新機能は追加しないが、重大なバグ（特にセキュリティ関連）は当面修正し続ける」と
表明されている（[junit4#1695 のコメント](https://github.com/junit-team/junit4/issues/1695#issuecomment-762838552)、
marcphilipp、2021-01-19）。最終リリースは 4.13.2（2021-02-13）。**公表された EOL 日程は見つからなかった。**

**本モジュールの JUnit 依存**

- `junit:junit:4.13.1` を **compile スコープ**で依存（利用者へ推移的に伝播する）。出典: `pom.xml:57-62`
- `junit-jupiter-api` を **compile スコープ**（`pom.xml:51-55`）、`junit-jupiter` を test スコープ
  （`:64-68`）で依存。いずれも版は `dependencyManagement` が import する `junit-bom` 5.11.0 から（`:26-32`）
- **`junit-platform-launcher` を test スコープで依存している**（`pom.xml:71-75`。版は同じく junit-bom 由来で
  1.11.0）。`JupiterEngineRunner` がテストクラスを直接実行するために `c06e4da` で追加したもので（§4.6）、
  **この 1 つだけは `b2ecc31` に存在しない。行番号は HEAD `6716f98` 時点である**
- 参照している JUnit 4 の型は `org.junit.rules.TestRule` / `org.junit.runners.model.Statement` /
  `org.junit.runner.Description` の 3 つ。出典: `grep -rn "org.junit" src/main --include=*.java`
- **`junit-jupiter-migrationsupport` は使っていない。** JUnit 6 で削除予定なのは同モジュールであり、本モジュールではない
- `org.junit.platform.commons.util.ReflectionUtils`（`@API(status = INTERNAL, since = "1.0")` が付いた内部 API。
  JUnit 側の都合で変更・削除されうる）を使用している。出典:
  `src/main/java/nablarch/test/junit5/extension/event/TestEventDispatcherExtension.java:11,65-67` と
  [ReflectionUtils.java:79](https://github.com/junit-team/junit5/blob/r5.11.0/junit-platform-commons/src/main/java/org/junit/platform/commons/util/ReflectionUtils.java)（タグ r5.11.0）

**`beforeEach` の実行経路上にあるスレッド束縛状態**

§4.4 (5) の機構の説明。**調査したのは `DbAccessTestExtension#beforeEach` → `beginTransactions()` →
`SimpleDbTransactionManager#beginTransaction` という実行経路が通る 5 アーティファクトだけ**で、
各 sources jar の `grep -rn "ThreadLocal" --include=*.java` から、コメントを除いた**宣言**を数えた。
`nablarch-testing` と `nablarch-testing-rest`（ともに 6-NEXT-SNAPSHOT sources）は **0 件**。残る 3 件は次のとおり。

| 宣言 | 種別 |
|---|---|
| `nablarch-core` 6-NEXT-SNAPSHOT sources `nablarch/core/ThreadContext.java:45-57` | **`InheritableThreadLocal`**。`childValue` が親のマップを `new HashMap<>(parentValue)` で複製する |
| `nablarch-core-jdbc` 2.2.0 sources `nablarch/core/db/connection/DbConnectionContext.java:26-32` | 素の `ThreadLocal` |
| `nablarch-core-transaction` 2.1.0 sources `nablarch/core/transaction/TransactionContext.java:23-28` | 素の `ThreadLocal` |

下 2 件は 6-NEXT-SNAPSHOT の sources jar がローカルリポジトリにないため 2.2.0 / 2.1.0 で確認した。
`ThreadContext` が `InheritableThreadLocal` であることは `"Time-limited test"` スレッドでも壊れないという意味だが、
その裏返しとして**子スレッドで書いた値は親スレッドへ戻らない**（`childValue` は複製であり共有ではない）。
**この 5 つで足りると考える理由は上記の実行経路に現れる型がこの範囲に収まるためだが、
`nablarch-testing` が依存する十数個のアーティファクトも、`ThreadLocal` 以外の束縛手段も調べていない。**

### 2.2 What binds the solution?

**JUnit 5 には、§1.3 の 2 条件を同時に満たす拡張ポイントが存在しない。**

| | テストメソッドを呼び出す処理を引数で受け取れるか | 呼ばれる順番 |
|---|---|---|
| `BeforeEachCallback#beforeEach(ExtensionContext)` | 受け取れない | `@BeforeEach` より前 |
| `InvocationInterceptor#interceptTestMethod(Invocation, …)` | 受け取れる（`invocation`） | `@BeforeEach` より後 |

`ExtensionContext` には、これから行われるテストメソッドの呼び出しを表すものがない。
`javap -p org/junit/jupiter/api/extension/ExtensionContext.class`（`junit-jupiter-api-5.11.0.jar`）が返す
メソッドは 24 個で、テストメソッドに関するものは `getTestMethod()`（`Optional<Method>`）と
`getRequiredTestMethod()`（`Method`）の 2 つだけ。いずれも得られるのはリフレクションの `Method` にすぎず、
JUnit 5 側の呼び出しは `TestMethodTestDescriptor#execute` が `invokeTestMethod` を通じて行う（§2.1 の `:138`）ので、
拡張が自分で `invoke` してもそれを**置き換えることはできない**。

これが §1.3 の 2 条件が両立しなかった構造的な理由であり、判断2 に選択肢がほぼない理由でもある（§5.2）。

**その他の制約**

- `TestEventDispatcher#testName` は NTF 側の `public final` フィールド（`TestEventDispatcher.java:92-94`）で、
  本モジュールから値を直接設定できない。`TestName` の内部は `private volatile String name`
  （`junit-4.13.1-sources` `org/junit/rules/TestName.java:28`）で `starting(Description)`（`:31-33`）経由でしか
  設定されないため、`apply()` を通すしかない
- `TestEventDispatcherExtension` はクラス宣言に `@Published(tag = "architect")` が付いている
  （`TestEventDispatcherExtension.java:33`）。`Published` の定義上、**このクラスの `public` / `protected` メソッドは
  すべて後方互換を保証する公開 API**（§0 の用語表、§4.5 (2)）。`resolveTestRules()` のシグネチャ変更は避ける
- 既存テスト、特に `TestEventDispatcherExtensionLifecycleMethodTest` を壊さない

### 2.3 未確認事項

**確認済みの事実と混ざらないよう、独立した項として置く。** ここに挙げたものは実物で確かめていない。
関連する事実に出典を添えてある場合も、その出典が未確認の部分まで裏づけているわけではない。

- `resolveTestRules()` を実際に利用しているプロジェクトの有無と規模
- 「`getRequiredTestMethod()` が返す `Method` を拡張が自分で `invoke` するとテストが 2 回走る」ことの実測。
  JUnit 5 側の呼び出しが止まらないことは §2.2 のとおり出典があるが、2 回走る様子そのものは観測していない

**閉じた未確認事項** — `@RepeatedTest` / `@TestTemplate` でルールが適用されるか
（`git show 231eaa9:src/test/java/nablarch/test/junit5/extension/event/TestRuleEmulationIntegrationTest.java`
の `:163-166` で固定した）、`Timeout` と `DbAccessTestExtension` の併用が壊れること（§4.4 (5) で実測し §4.6 で恒久化）、`Timeout` 成立時にテスト本体と後処理が並行実行されるか（§4.4 (5) で最小再現により実測。割り込みに反応しない本体の場合に限り並行になる）。
`@Nested` は最小再現で実測でき、加えて `git show 8780eb8:.../TestRuleEmulationIntegrationTest.java` の
クラス Javadoc（`:51-55`）という一次情報もあるので、未確認から外して §4.4 (7) に置いた。

## 3. Design overview

### 3.1 What is the core idea, and why does it solve the problem?

**「TestRule をどこに適用するか」を 1 か所に決めるのをやめ、ルールの性質で適用先を分ける。**

過去の 2 つの実装（§1.5）は、いずれも全部を 1 か所に置いたために §2.2 のトレードオフをそのまま被った。

| | TestRule がテスト本体を包むか | NTF 前処理が `@BeforeEach` より先か |
|---|---|---|
| 初版 `ad2410b`（全部を `interceptTestMethod` へ） | ○ | × |
| PR #3 `148db9a` 以降の現行（全部を `beforeEach` へ） | × | ○ |
| 本設計（性質で分ける） | ○ | ○ |

分ける基準は「そのルールがテスト本体の実行を必要とするか」。**必要としないもの**（`TestName` /
`TestDescription`。`Description` からテストメソッド名を控えるだけ）は現行どおり `beforeEach` で空の `Statement` に
対して適用し、**必要とするもの**（利用者が `resolveTestRules()` で追加するルール）は `interceptTestMethod` へ移す。
NTF の前処理より後に実行されて困るのは NTF 自身のルールだけなので、この分割で両方が成立する。

### 3.2 What are the pieces, and what is each responsible for?

| 要素 | 責務 |
|---|---|
| `beforeEach`（`BeforeEachCallback`、既存） | 内部ルールの適用と NTF 前処理の実行。実行位置は現行から変えない |
| `resolveInternalTestRules()`（新設、**`protected`**） | `TestName` / `TestDescription` を返す。`SimpleRestTestExtension`（別パッケージ）が override して `testDescription` を追加する |
| `interceptTestMethod` / `interceptTestTemplateMethod`（`InvocationInterceptor`、新設） | `resolveTestRules()` が返すルールで `invocation.proceed()` を包む。**`final`**（§4.5 (5)） |
| `resolveTestRules()`（既存。クラスの `@Published` により公開 API） | 利用者がテスト本体を包みたいルールを返す。基底実装は空リストを返すよう変更する（§4.5 (1)） |

`TestEventDispatcherExtension` はクラス単位で `@Published(tag = "architect")` なので、
**この分割は後方互換を保証する公開 API を 3 本＋interface 1 つ増やす**ことを意味する（§2.2、§4.5 (2)、§5.1）。

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

**この章の骨格は、判断1 の決定より前に作って検証したプロトタイプ（§0 の用語表）に由来する。**
プロトタイプ自体は取り消してありコミットもしていないが、**実装はタスク #4 で完了しており（`0c2047f` / `5b47d2c` /
`231eaa9`）、§4.1 と §4.6 は実物のコミットを指している。** §4.2 前半だけがプロトタイプ時代の記録である。

### 4.1 変更差分（`src/main` の 2 ファイル）

**実装はタスク #4 で完了している。** コミット `0c2047f`「fix: 利用者のTestRuleをテストメソッドの実行に適用する」で
`src/main` の 2 ファイルが変わった。**行数は基準コミットを添えないと意味を持たない。**
`git diff --numstat --ignore-cr-at-eol <基準> -- src/main` で数えると、`8780eb8 231eaa9` が **+117 / -24**
（`TestEventDispatcherExtension.java` +115/-22、`SimpleRestTestExtension.java` +2/-2）、
`8780eb8 6716f98`（`49f73f6` の `final` 化と `6716f98` の Javadoc 補足まで含む現在の HEAD）が **+153 / -23**。
`--ignore-cr-at-eol` を付けているのは、`231eaa9`「style: 追加・変更したJavaソースの改行コードをCRLFに統一する」で
改行コードを揃えており、付けないとファイル全体の置き換えとして数えられるため。いずれも Javadoc の書き換えを
含む実数である。**タスク #4 の修正は続いているので、この数字を引くときは基準コミットごと引くこと。**
**差分の実物は git で読める**ので、ここには要点だけを残す。

`TestEventDispatcherExtension`（行番号は `231eaa9` 時点）

| 変更 | 場所 |
|---|---|
| `implements` に `InvocationInterceptor` を追加 | `:43` |
| `beforeEach` の呼び先を `emulateTestRules` から `applyInternalTestRules` へ | `:117-120` |
| `applyInternalTestRules` — `resolveInternalTestRules()` を `NOOP_STATEMENT` に適用して `evaluate()` し、例外を `RuntimeException` で包む（従来の `emulateTestRules` から利用者ルールの適用を外したもの） | `:130-138` |
| **新設** `interceptTestMethod` — `applyTestRules(resolveTestRules(), toStatement(invocation), extensionContext).evaluate()` の 1 行 | `:148-152` |
| **新設** `interceptTestTemplateMethod` — 本体は `interceptTestMethod` と同一（`@ParameterizedTest` / `@RepeatedTest` 用） | `:166-170` |
| **新設** `toStatement(Invocation<Void>)` — `invocation.proceed()` を呼ぶだけの `Statement` | `:177-184` |
| **新設** `applyTestRules(List<TestRule>, Statement, ExtensionContext)` — リストの先頭から順に `apply` する（末尾が最も外側。JUnit 4 の `RunRules` と同じ順序） | `:197-205` |
| `convert()` が `createTestDescription(Class, String, Annotation...)` を使うよう変更（§4.4 (4)） | `:212-216` |
| **新設** `protected resolveInternalTestRules()` — `singletonList(support.testName)` を返す（§4.5 (2)） | `:232-234` |
| `resolveTestRules()` の基底実装を `emptyList()` へ変更（§4.5 (1)） | `:262-264` |

`SimpleRestTestExtension` は override 先を `resolveTestRules()` から `resolveInternalTestRules()` へ変えただけ
（`231eaa9` の `:30-34`）。

**この 2 つの intercept メソッドは、その後 `49f73f6`「fix: TestRuleを適用するinterceptメソッドをfinalにする」で
`final` になった**（`git archive HEAD src/main/java` を展開して `javac` し、`javap -p` に掛けると
`public final void interceptTestMethod(...)`。`target/classes` はビルドのたびに作り直されるので、
出典にはソースからのコンパイルを使う）。理由と実測は §4.5 (5)。

### 4.2 実測結果

**前半 — プロトタイプ（§0 の用語表）で「直ればどうなるか」を実測したが、再現物がない。** 差分は取り消して
あり git 履歴にも残っていない。確かめたのはルールがテスト本体を包むこと・解説書の `Timeout` の例が実際に
タイムアウトすること・`@ParameterizedTest` でも適用されること・既存テストの失敗が §4.3 の 1 件だけになること
の 4 点で、**いずれもタスク #4 で恒久テストに起こしたので（§4.6）、正はそちらである。**

**後半 — 本セッションで走らせた最小再現（NTF 非依存）。** **JUnit 5.11.0 単体**で、実装と同じ形の Extension
（`interceptTestMethod` の中で渡されたルールを `invocation.proceed()` に巻いて `evaluate()` するだけのもの）に、
`base` を呼ばない／2 回呼ぶ／`base` の前に例外を投げるルール、`ExternalResource`・`TemporaryFolder`・
`TestWatcher`・`Stopwatch`・`ExpectedException`・`ErrorCollector`・`Verifier`・`RuleChain`・`DisableOnDebug`・
`Timeout`、`Timeout` × `ThreadLocal`、`@BeforeEach` / `@AfterEach` が例外を投げる場合、アノテーション付き
テストメソッド、`@Nested` を持つクラス、`@TestFactory` を順に掛けた。比較用に JUnit 4 側（`JUnitCore`）でも
同じ形のテストを走らせている。手順は `~/.m2/repository` の jar を `-cp` に並べて `javac` し、`LauncherFactory`
で 1 クラスずつ実行するもので、`mvn` は使っていない。**結果は §1.2 の表（現行実装の側）と §4.4 の一覧・各項目
（修正後の側）に実測値のまま載せてある。これもリポジトリには残していないが、§4.6 の恒久テストで置き換えた。
`@Nested`（§4.4 (7)）だけはタスク #4 の対象外のため再現物なしのまま残る。**

### 4.3 既存テストで 1 件だけ落ちる。それは仕様変更そのもの

落ちるのは `TestEventDispatcherExtensionTest` の
`TestRuleエミュレート時に例外が発生した場合は_発生した例外を原因として持つ実行時例外がスローされること`
で、`expected java.lang.RuntimeException to be thrown, but nothing was thrown` になる（プロトタイプ実行時に
写した出力。**本文書の作成時には再取得していない**）。このテストは「`resolveTestRules()` が返したルールが
`beforeEach` の中で評価され、そこで起きた例外が `RuntimeException` に包まれる」ことを検証している（出典:
`git show 8780eb8:src/test/java/nablarch/test/junit5/extension/event/TestEventDispatcherExtensionTest.java`
の 122-139 行）。本設計はこの前提そのものを変えるので、テストは `interceptTestMethod` を対象に書き換える。
**落ちるのはこの 1 件だけ**であり、それは仕様変更そのものである。
例外の扱いがどう変わるか、なぜそうするかは §4.5 (4) に集約した。

### 4.4 1-A で受け入れる制約

**Javadoc（タスク #5）には下の (1)〜(8) をすべて書く。解説書（タスク #6）に書くのは (1)(2)(3)(5)(6) の 5 点。**
(4) はタスク #4 で塞ぐので書かない。(7) は 1-A 以前からある別課題。(8) は §6 のとおり解説書には入れない
（Javadoc には書く）。

**まず、何が使えて何が使えないか。** JUnit 4 が標準で提供する代表的な `TestRule` を §4.1 の実装と同じ形の
Extension に渡し、JUnit 5.11.0 単体で実行した結果（手順は §4.2 後半）。**この表は §4.6 の
`StandardTestRuleIntegrationTest`（8 件）で恒久テストにした。**

| ルール | 結果 | 備考 |
|---|---|---|
| `Timeout` | **動く** | `TestTimedOutException` で FAIL する。ただしテスト本体が別スレッドになる副作用があり、`DbAccessTestExtension` と併用できない → (5) |
| `ExternalResource` | **動く** | `before()` が `@BeforeEach` の**後**、`after()` が `@AfterEach` の**前** → (2) |
| `TemporaryFolder` | **動く** | 同上。`@BeforeEach` から `getRoot()` を呼ぶと `IllegalStateException` → (2) |
| `ExpectedException` | **動く** | 期待どおり成功する |
| `ErrorCollector` | **動く** | 収集したエラーが 2 件以上のとき、FAIL の例外は `MultipleFailureException` になる（1 件なら収集した例外がそのまま伝播する） |
| `Verifier` | **動く** | `verify()` が投げた例外がそのまま伝播して FAIL する |
| `TestWatcher` / `Stopwatch` | **動く** | ただし `@AfterEach` の**内側**で完了するため、`@AfterEach` の失敗を `failed()` で観測できない → (2) |
| `RuleChain` | **動く** | 入れ子の順序が保たれる |
| `DisableOnDebug` | **動く** | — |
| `base` を 2 回以上呼ぶ自作ルール（retry / repeat 系） | **使えない** | `JUnitException` になる → (1) |
| `base` を呼ばない自作ルール（skip 系） | **使えない** | `JUnitException` になる → (1) |

**「動く」ものにも (2)(3) は等しくかかる。**（(4) も同じく全部にかかるが、これはタスク #4 で塞ぐ。）
以下がその内訳である。

**(1) `base` を呼ばないルール、`base` を 2 回以上呼ぶルールは使えない。**

`InvocationInterceptor` の契約は「`Invocation#proceed()` **または** `Invocation#skip()` をちょうど 1 回呼ぶ」
（§2.1）。§4.1 の実装は `proceed()` しか呼ばないので、両端でこの契約を外れる。飛ばすルールは
`JUnitException: ... never called invocation: ...`、2 回以上呼ぶルール（retry / repeat 系）は
`JUnitException: ... called invocation multiple times instead of just once: ...` になる。
後者は**テスト本体が 1 回走ってから失敗する**ので、副作用が残る。JUnit 4 の retry rule は広く使われる
イディオムなので、`Timeout` と並ぶ代表例として解説書に書く。`skip()` を使えば飛ばすルールも表現しうるが、
本設計では採らない（理由は §4.5 (3)）。

一方、**ルールが `base` を呼ぶ前に例外を投げた場合は、その例外がそのまま伝播する**（`JUnitException` にはならない）。
`verifyInvokedAtLeastOnce()` が `finally` ではなく `proceed()` の後に置かれているため（§2.1）。
「`base` を呼ばなければ必ず `JUnitException`」ではない、という書き分けが要る。いずれも §4.2 後半で実測済み。

**(2) ルールが包むのはテストメソッドの呼び出しのみ。前処理側・後処理側の両方が JUnit 4 とずれる。**

`@BeforeEach` / `@AfterEach` および NTF の前後処理は含まれない。JUnit 5 に、自身の
`BeforeEachCallback` を `Statement` で包む手段がないため（§2.2）。JUnit 4 のランナーはルールを
`@Before` / `@After` の**外側**に積んでいた（§2.1 の `BlockJUnit4ClassRunner.java:319-321`）ので、
**ルールの前処理は `@Before` より前、後処理は `@After` より後**だった。修正後はどちらも内側に入る。実測:

- **後処理側** — `ExternalResource#after()` が `@AfterEach` の**前**に走る
  （`[ext-beforeEach, @BeforeEach, res-before, test, res-after, @AfterEach, ext-afterEach]`）。
  `TemporaryFolder` が作った一時ファイルを `@AfterEach` から触っているコードは静かに壊れる
- **前処理側** — `ExternalResource#before()` が `@BeforeEach` の**後**に走る。`TemporaryFolder` を
  `resolveTestRules()` に渡して `@BeforeEach` から `getRoot()` を呼ぶと
  `IllegalStateException: the temporary folder has not yet been created` になる。JUnit 4 では `@Before` から使えた
  （同じ最小再現で JUnit 4 側も `JUnitCore` で走らせて確認した）
- **`TestWatcher` / `Stopwatch`** — `@AfterEach` の**内側**で完了するため、`@AfterEach` が例外を投げてテストが
  FAIL しても `succeeded()` を報告し、`failed()` は呼ばれない
  （`[ext-beforeEach, test, watch-succeeded, watch-finished, @AfterEach-throws, ext-afterEach]`、集計は fail=1）

**`TemporaryFolder` の例を除き、いずれも例外にならず黙って起きる。** §6 にも明記する。

**(3) `@BeforeEach`（および `beforeEach`）が失敗すると、利用者のルールは前処理も後処理も一切走らない。**

ルールは `interceptTestMethod` の中でしか組み立てられず、`@BeforeEach` が例外を投げると JUnit 5 は
テストメソッドの呼び出し（§2.1 の `4.`）に到達しない。`beforeEach`（`BeforeEachCallback`）の失敗も
`2.` より前で止まるので同じ結果になる（こちらは未実測）。実測: `@BeforeEach` で例外を投げると
`[ext-beforeEach, @BeforeEach-throws, @AfterEach, ext-afterEach]` となり、
`ExternalResource#before()` も `after()` も**一度も走らない**。
JUnit 4 では `withRules` が `withBefores` の外側にあった（§2.1 の `BlockJUnit4ClassRunner.java:319-321`）ため、
`@Before` が落ちても `after()` は走っていた（同じ最小再現で JUnit 4 側も走らせ、
`[rule-before, @Before-throws, @After, rule-after]` を確認した）。

**非対称なのは、NTF 側の後処理は走ることである。** `afterEach`（`AfterEachCallback`）は JUnit 5 が必ず呼ぶので、
`DbAccessTestExtension#endTransactions()`（`DbAccessTestExtension.java:25-29`）は実行される。
**利用者のルールの後処理だけが走らない。** リソースの解放をルールに任せていると、
`@BeforeEach` が落ちたときにだけ解放漏れが起きることになる。

**(4) `Description` にアノテーションが 1 つも載らない。これは受け入れず、タスク #4 で塞ぐ。**

`convert()`（`TestEventDispatcherExtension.java:143-147`）が使っているのは
`Description.createTestDescription(Class, String)`（`junit-4.13.1-sources` の
`org/junit/runner/Description.java:98-100`）で、アノテーションの可変長引数を渡さない。そのため
`Description#getAnnotation(...)`（`:270-277`）は常に `null`、`getAnnotations()` は空になる。
JUnit 4 では `FrameworkMethod` 由来のメソッドアノテーションが載っていた。
実測: `@Marked` を付けたテストメソッドに対しルールが観測した値は `anno=false, count=0` で、
**テストは成功したまま**（JUnit 4 で同じテストを走らせると `anno=true, count=2`）。
`description.getAnnotation(...)` で挙動を切り替える自作ルール（`ConditionalIgnore` / `@Repeat` 系）は、
§1.2 が「最も問題」とした「静かに成功する」形で無効化される。

**判断: 直す。** `createTestDescription(Class, String, Annotation...)` のオーバーロード（同 `:85-87`）に
`context.getRequiredTestMethod().getAnnotations()` を渡すだけでよい。同じ最小再現でこの 1 行を入れると
`anno=true, count=2` になることを確認した。**タスク #4 の対象に含め（`231eaa9` の
`TestEventDispatcherExtension.java:212-216`）、§4.6 の恒久テストにも足した。**

**この差し替えに副作用はない。確認した。** `Description` の `equals`（`junit-4.13.1-sources`
`org/junit/runner/Description.java:238-244`）/ `hashCode`（`:233-235`）/ `getDisplayName()`（`:183-184`）は
いずれも `fUniqueId` か `displayName` しか見ておらず、アノテーションは関与しない。**アノテーションが 0 個なら
2 引数版と 3 引数版は同一の `Description` になる**（この 3 つが一致することを Temurin 21.0.11 で実行して確認）。
既存の内部ルールへの影響もない。`TestName#starting` は `getMethodName()` しか使わず
（`org/junit/rules/TestName.java:31-33`）、`nablarch-testing-rest` 6-NEXT-SNAPSHOT sources
`nablarch/test/core/rule/TestDescription.java:15-19` の `starting` も `getTestClass()` と `getMethodName()` だけである。

**ただし塞がらないものが 1 つ残る。`@ParameterizedTest` では全 invocation の `Description` が同一になる。**
`convert()`（`231eaa9` の `:212-216`）が使うのはテストクラスとメソッド名だけで、invocation の番号を持たないため。
`@ValueSource(ints = {1, 2})` のテストにルールを掛けると、ルールが受け取る `Description` は 2 回とも
`test(spike.Fprobe$T)` で、`getMethodName()` も 2 回とも `test` だった。JUnit 4 の `Parameterized` は
同じテストで `test[0](spike.J4Param)` / `test[1](spike.J4Param)` を返す（どちらも本文書の作成時に
Temurin 21.0.11 で実行して確認した）。**invocation ごとに状態を持つルールは、JUnit 5 側では区別できない。**
なお invocation 自体は `ExtensionContext#getDisplayName()` では区別できる（同じ最小再現で `[1] 1` / `[2] 2` を
観測した）。区別できないのは `Description` に載る情報のほうである。

**この 1 点だけは恒久テストにせず、Javadoc（タスク #5）に書くに留める。** 理由は、これが
「1-A で直ったこと」でも「1-A で受け入れた制約」でもなく、**タスク #4 が塞ぐと決めた (4) の外側に残る
JUnit 5 側の性質**だからである（§4.6 が恒久テストに起こしたのは前 2 者）。申し送りは既に `steering.md` の
タスク #5 に入っている。**再現物は残らないが、上の最小再現は手順を書いてあるので読み手が組み直せる。**

**(5) `Timeout` と `DbAccessTestExtension` は併用できない（実測済み）。**

これは §4.4 のなかで最も影響が大きい。**解説書が挙げている唯一の例が `Timeout` である**ため、
タスク #6 の解説書修正案にも必ず入れる（§6）。

**実測結果。** `DbAccessTestExtension` を適用したテストと、`resolveTestRules()` で
`new Timeout(5000, MILLISECONDS)` を返すよう override したものとを、テスト本体から
`DbConnectionContext.getConnection()` / `TransactionContext.getTransaction()` を呼んで比べた。

```
DbAccessTestExtension 単体      -> thread=main  conn=true  tx=true
DbAccessTestExtension + Timeout -> thread=Time-limited test
   DbConnectionContext.getConnection() -> IllegalArgumentException:
       specified database connection name is not register in thread local. connection name = [transaction]
   TransactionContext.getTransaction() -> IllegalArgumentException:
       specified transaction name is not register in thread local. transaction name = [transaction]
```

**どちらのテストも「成功」する。** コネクションが取れないことは例外を握らなければ表に出ないので、
§1.2 が「最も問題」とした「静かに成功する」形になる。

**タイムアウト値は本質ではない。** この現象はタイムアウトが成立するかどうかではなく、`Timeout` が
`"Time-limited test"` という別スレッドを必ず起こすことだけに依る（下の (a)）。上の実測は
`new Timeout(5000, MILLISECONDS)`、§4.6 の恒久テスト `TimeoutDbAccessIntegrationTest` は
`Timeout.seconds(30)`（`231eaa9` より後の `8885ac9` の `:132`）で値が違うが、どちらもタイムアウトを
成立させない前提で置いた余裕のある値であり、観測される結果は同じである。

手順: `231eaa9` の `src/main` を Temurin 21.0.11 の `javac` でコンパイルし、`~/.m2/repository` の jar を
`-cp` に直接並べて `LauncherFactory` で実行した（`mvn` は使っていない）。`connectionFactory` /
`transactionFactory` には既存の `MockConnectionFactory` / `MockTransactionFactory` を使っており、実 DB は要らない
（§4.6）。**この実測は §4.6 の `TimeoutDbAccessIntegrationTest` で恒久テストにしてあり、そちらが正である。**

下の (a)(b)(c) はその機構の説明、(d) は上の実測そのものである。

(a) **`Timeout` はテスト本体を別スレッドで走らせる。** `apply()` が返す `FailOnTimeout`
（`junit-4.13.1-sources` `org/junit/rules/Timeout.java:153-155` → `:145-151`）の `evaluate()` が
`"Time-limited test"` という名前の**新しいスレッド**を起こしてそこで `base.evaluate()` を走らせる
（`org/junit/internal/runners/statements/FailOnTimeout.java:120-132`。生成は `:123-124`、`start()` は `:127`）。

(b) §4.2 後半で、`@BeforeEach`（`main`）で設定した素の `ThreadLocal` がテスト本体
（`"Time-limited test"`）から `null` になることを実測した。`InheritableThreadLocal` は引き継がれる。

(c) **NTF の DB コネクションは素の `ThreadLocal` に置かれる。** `DbAccessTestExtension.java:19-23` の
`beforeEach` → `beginTransactions()`（`nablarch/test/core/db/DbAccessTestSupport.java:95-117`。`:115` が
`manager.beginTransaction()`）→ `nablarch/core/db/transaction/SimpleDbTransactionManager.java:35-52` という経路で、
最後のメソッドが **2 か所**にスレッド束縛の状態を置く —— `:48` の `DbConnectionContext.setConnection(...)` と
`:52` の `TransactionContext.setTransaction(...)`。どちらの受け皿も素の `ThreadLocal`（§2.1）。
（6-NEXT-SNAPSHOT の class を `javap -p -l` で照合すると、`beginTransaction` の `:47` `:48` `:50` `:51` `:52` は
2.2.0 sources と同じ行に対応していた。）

(d) **その結果が上の実測である。** `DbAccessTestExtension` と `Timeout` を併用すると、テスト本体からは
DB コネクションもトランザクションも取れない。JUnit 4 のランナーではルールが `@Before` / `@After` ごと包む（§2.1）ため、
トランザクション開始もテスト本体も同じ `"Time-limited test"` スレッドで起きて整合していた。

**タイムアウト成立時は、条件つきでテスト本体と後処理が並行実行される（実測した）。** `FailOnTimeout` は
`createTimeoutException()` で **`thread.interrupt()` を呼んだうえで**（`FailOnTimeout.java:176`、呼び出しは `getResult` の `:166`）、
`finally` の `thread.join(1)`（1 ミリ秒）だけ待って抜ける（`:133-138`）。最小再現（`~/.m2` の junit 4.13.1 と junit-jupiter 5.11.0 を
`javac`/`java` に直接渡し、`Timeout(300ms)` を `interceptTestMethod` で巻いて素の `@AfterEach` を置いた）のログは、`Thread.sleep` の本体が
`[body-start, body-INTERRUPTED, afterEach]`、割り込みを無視するビジーループの本体が `[body-start, afterEach, body-end interrupted=true]`。
**並行実行になるのは、割り込みに反応しない処理を本体が実行している場合に限る**（NTF ではこの後処理が `endTransactions()`（`DbAccessTestExtension.java:25-29`）にあたるが、再現には組み込んでいない）。恒久テスト化の判断は §4.6。

**この絞り込みが及ぶ範囲。** §2.1 で調べたのは `beforeEach` の実行経路上にある 5 アーティファクトだけで、
そこには上の 2 つ以外に `ThreadLocal` の宣言がない。**壊れること自体は実測済みだが、
「他にスレッド束縛の状態を残す経路がない」とまでは言えない**（`nablarch-testing` が依存する
`nablarch-fw-standalone` などは見ておらず、`ThreadLocal` 以外の手段も調べていない）。
また、**テスト本体が `ThreadContext`（`InheritableThreadLocal`）に書いた値は親スレッドに戻らない**
という別の制約も生じる（§4.2 後半で実測済み）。なお `TestEventListener` の実装で `beforeTestMethod()` を
override しているものは NTF 内には存在しない（`grep -rn "beforeTestMethod"` のヒットはインタフェース宣言
`TestEventListener.java:22`・空実装 `:49`・`TestEventDispatcher.java:138` の 3 件だけ）が、
利用者は `SystemRepository` に独自リスナを登録できる。

**`RestTestExtension` / `SimpleRestTestExtension` はこの問題を持たない。** `setUpDb()` も `assertTableEquals` 系も
`TransactionTemplate#execute`（`nablarch-testing` sources `nablarch/test/core/db/TransactionTemplate.java:69-90`。
`:71` `beginTransaction` → `:88` `endTransactionQuietly`）の中で 1 回の呼び出しごとにトランザクションを
開始・終了し、`beforeEach` をまたいで `ThreadLocal` にコネクションを残さないため。

**(6) `@TestFactory` / `DynamicTest` には適用されない。タスク #4 の対象外とする。**

実装が override するのは `interceptTestMethod` と `interceptTestTemplateMethod` の 2 つだけで、
`interceptTestFactoryMethod` / `interceptDynamicTest`（§2.1 の表）は既定実装のまま `invocation.proceed()` を呼ぶ。
**`@TestFactory` のテストでは利用者のルールが一切適用されず、しかも例外にならず黙って通る。**
§1.2 が「最も問題」とした「静かに成功する」が、この経路にはそのまま残る。
**この現状は §4.6 の特性テストで固定する**（「対応する」のではなく「対応していない」ことを記録するテスト）。

**対象外とする理由は実装の難しさではなく意味論の不一致である。`@TestFactory` メソッドを包んでも、
包まれるのは `Stream<DynamicNode>` の「生成」だけで、動的テストの「実行」は包まれない。**
出典: `junit-jupiter-engine-5.11.0.jar` の `TestFactoryTestDescriptor` を `javap -p -c -l` で逆アセンブルすると、
インターセプタが挟まるのは `TestFactoryTestDescriptor.java:97-98` のファクトリメソッド呼び出しだけで、
動的テストの実行（`:101-111` のループ）はその外側にある。§4.2 後半でも、`proceed()` が返すのが遅延評価の
`Stream`（`ReferencePipeline$Head`）であることを実測した。

**後から追加するには、先に決めるべきことが 2 つある。** `interceptDynamicTest` の `ExtensionContext` では
`getRequiredTestMethod()` が `PreconditionViolationException` を投げる（§4.2 後半で実測）ので `Description` の
作り方を別に決めること、およびルールのインスタンスが 1 個しかなく N 件の動的テストに N 回 `apply` される以上、
状態を溜めるルールの寿命を決めること。本設計はそこまで踏み込まない。

**(7) `@Nested` を持つテストクラスでは正しく動かない（1-A 以前からある別課題）。**

`@Nested` クラスを足すと **Extension のインスタンスが外側クラスと入れ子クラスで共有される**
（JUnit 5.11.0 単体の最小再現で `identityHashCode` の一致を実測）。`postProcessTestInstance`
（`TestEventDispatcherExtension.java:60-62`）は両方のインスタンスに対して呼ばれるため、`support` フィールド
（`:58`、代入は `:62`）が後勝ちで上書きされ、ルールが記録するサポートインスタンスとテスト本体が参照するものが別になる。
**これは TestRule 再現機構の問題ではなく、`support` フィールドを 1 枠しか持たない設計に起因する。
1-A 以前から存在する課題であり、1-A を選んだことで受け入れた非互換ではない。**
よって**タスク #4 の対象外とし**、`support` の持ち方（`ExtensionContext.Store` へ移すなど）は別課題として立てる。
`git show 8780eb8:src/test/java/nablarch/test/junit5/extension/event/TestRuleEmulationIntegrationTest.java`
は、この理由でクラス Javadoc（`:51-55`）に「`@Nested` は追加できない」と明記してある。

**(8) 例外の扱いが、渡すリストによって変わる。** §4.5 (4) を参照。

### 4.5 記録しておく実装上の選択

**(1) `resolveTestRules()` の基底実装を `singletonList(support.testName)` から `emptyList()` に変える。**

代案は「基底実装はそのまま `testName` を返し、`interceptTestMethod` でも `beforeEach` でも同じリストを適用する」
だが、`TestName` が 2 回適用される。値は同じなので実害はないものの、`resolveTestRules()` の意味が
「利用者がテスト本体を包みたいルール」に定まらない。空リストを採る。副作用として、解説書 rst:420-421 の
「必ず親クラスの `resolveTestRules()` が返すリストをベースにすること」が事実でなくなり、
rst:407-408 のコード例（`super.resolveTestRules()` をベースにする）も成り立たなくなる（§6）。

**(2) `protected resolveInternalTestRules()` を新設する。公開 API が 3 本＋interface 1 つ増えることを受け入れる。**

`TestEventDispatcherExtension` はクラス宣言に `@Published(tag = "architect")` が付いている
（`231eaa9` の `TestEventDispatcherExtension.java:36`）。`Published` は
「クラスの全てのAPIを公開APIとする」「利用者がオーバーライド可能なメソッドも公開APIとする」と定義されている
（`nablarch-core` 6-NEXT-SNAPSHOT sources `nablarch/core/util/annotation/Published.java:14,16`）。
**したがって、この変更で増える公開 API は `resolveInternalTestRules()` の 1 本ではない。**

| 増えるもの | 位置づけ |
|---|---|
| `protected List<TestRule> resolveInternalTestRules()` | NTF 内部専用（下記）。それでも公開 API |
| `public void interceptTestMethod(...)` | 本設計の中身そのもの。**`final` にする**（(5)） |
| `public void interceptTestTemplateMethod(...)` | 同上 |
| `implements InvocationInterceptor` | **以後この interface を外せない**という約束 |

**`final` にしても公開 API から外れるわけではない。** シグネチャと振る舞いの後方互換は保証し続ける。
外れるのは「利用者が override できる」という一点だけである（(5)）。

**`implements InvocationInterceptor` が開くのは、上の表の 2 本だけではない。** `InvocationInterceptor` は
`default` メソッドを **10 本**持つ（`javap -p org/junit/jupiter/api/extension/InvocationInterceptor.class`。
`junit-jupiter-api-5.11.0.jar`。一覧は §2.1 の表）。`final` にした 2 本を除く **8 本**（`interceptDynamicTest` の
2 オーバーロードを別に数えて 8、名前では 7）が、`@Published` クラスの **override 可能面**として開く。
表で「interface 1 つ」と数えているのはこの 8 本をまとめた意味であって、増える面が 1 つという意味ではない。

**判断2 のなかで唯一、選択の余地があった点はここである。新設で決定した。ただし「公開 API を増やさない」が
優先方針になった場合の切り替え先として、下の代替 A を残す**（代替 A / 代替 B が減らせるのは
上の表の 1 行目だけで、残る 3 つはどの案でも増える）。

**このメソッドの位置づけを先に決めておく。** `resolveInternalTestRules()` は **NTF 内部専用
（`SimpleRestTestExtension` のような NTF 側の Extension が override するためのもの）であり、利用者向け API ではない。**
`@Published(tag = "architect")` のクラスの `protected` メソッドである以上、利用者が override することを止める手段は
ないが、そこへルールを置くと「テスト本体を包まない」＋「例外が `RuntimeException` に包まれる」という別の意味論になる。
**タスク #5 の Javadoc に「利用者はこのメソッドを override しないこと。利用者のルールは
`resolveTestRules()` へ渡すこと」を明記する。**（この方針の帰結として、§5.1 と §6 でも
`resolveInternalTestRules()` を利用者向けの移行先としては案内しない。）

**代替案は 2 つある。どちらも `resolveInternalTestRules()` の 1 本を増やさずに済む。**

**代替 A — 内部ルールの適用先を基底の `beforeEach` に固定する。** 基底の `applyInternalTestRules` は
`Collections.singletonList(support.testName)` を直接使い、`SimpleRestTestExtension` は自分の `beforeEach`
（`SimpleRestTestExtension.java:24-28`。既に override 済み）の中で、`super.beforeEach(context)` の**前**に
`testDescription` を適用する。順序の問題は起きない。ただし基底の `convert(ExtensionContext)` は `private` なので
`SimpleRestTestExtension` 側で `Description` の構築を書き直すことになり、**作り方が基底と派生の 2 か所に散る。**
また基底側の変更も避けられない（`resolveInternalTestRules()` が消え、`applyInternalTestRules` が固定リストを使う形になる）。

**代替 B — `support` の `@Rule` 付きフィールドを反射で収集する。** JUnit 4 の
`BlockJUnit4ClassRunner#getTestRules`（`junit-4.13.1-sources` `:434-439`）が `TestClass` の
`collectAnnotatedMethodValues`（`org/junit/runners/model/TestClass.java:278`）と
`collectAnnotatedFieldValues`（`:244`）でやっているのと同じことを `support` のクラスに対して行う。
NTF 側にルールが追加されても追随でき、「次に触る人が `resolveTestRules()` に内部ルールを戻す」事故も起きない。
**しかし、利用者の独自サポートクラスが宣言した `@Rule` フィールドまで拾ってしまう。** 解説書の例（rst:377-391）の
`CustomTestSupport` は `@Rule public Timeout timeout` を宣言しており、これが内部ルール側に落ちると
**まさに直そうとしている不具合が再発する**（しかも利用者は同じ `timeout` を `resolveTestRules()` にも渡すので二重適用になる）。
パッケージ名で絞り込むことはできるが、利用者のクラス配置を前提にした heuristic になる。
なお「`Class#getDeclaredFields` は順不同だから採れない」という棄却理由は成り立たない（JUnit 4 自身が
`TestClass#getSortedDeclaredFields`（同 `:76-80`）で並べ替えている）。棄却の理由は上の 1 点で足りる。

**それでも新設（`resolveInternalTestRules()`）を採る。** 理由は 3 つ。(a) 代替 B は不具合を再発させうる。
(b) 代替 A は `Description` の構築が 2 か所に散り、NTF 側にルールが増えるたびに派生 Extension の `beforeEach` を触ることになる。
(c) 「内部で使うルール」という概念が名前で表れないと、次に触る人が `resolveTestRules()` に内部ルールを戻してしまう
（§1.5 と同じ事故の再発）。**公開 API を増やしたくないという方針が優先されるなら代替 A に切り替えてよい。**
その場合の変更は基底側・`SimpleRestTestExtension` 側の**両方**に及ぶ。

**(3) `skip()` は使わない。**

`InvocationInterceptor` の契約は `proceed()` **または** `skip()` を 1 回（§2.1）なので、
`skip()` を呼べば「テストを飛ばすルール」も表現しうる。しかし `apply()` が返した `Statement` を `evaluate()` する側には、
その中で `base.evaluate()` が呼ばれたかどうかを知る手段がない。呼ばれなかった場合にだけ `skip()` を呼ぶ、という
実装ができないため採らない。結果として §4.4 (1) の制約が残る。

**(4) 例外の扱いが、同じ `TestRule` 型でもリストによって変わる。**

`applyInternalTestRules`（`beforeEach` 側）は例外を `RuntimeException` で包み、`interceptTestMethod` 側は包まず、
ルールが投げた例外をそのまま伝播させる。**この非対称は意図的である。**

- **包まない理由** — `ExpectedException` / `ErrorCollector` は例外でテストの成否を表現するため、包むと
  機能しなくなる（§4.4 の一覧でこの 2 つが「動く」のはそのため）。§4.3 の既存テスト 1 件が落ちるのはこの変更による
- **包む側を残す理由** — `beforeEach` は `throws Exception` しか宣言できず、`Statement#evaluate()` は
  `Throwable` を投げるため、そのままでは伝播させられない

利用者から見ると「同じ `TestRule` を `resolveTestRules()` に渡すか `resolveInternalTestRules()` に渡すかで
例外の扱いが変わる」ことになる。タスク #5 の Javadoc で両方に明記する。
**§6 のとおり、解説書には入れない。**

**(5) `interceptTestMethod` / `interceptTestTemplateMethod` を `final` にする。**

**理由は、`final` にしないと静かな回帰が起きるからである。** 利用者の Extension が
`TestEventDispatcherExtension` を継承したうえで自分でも `InvocationInterceptor#interceptTestMethod` を
override していると、**基底の新実装が覆い隠され、`resolveTestRules()` が返したルールが一言もなく消える。**

実測（`~/.m2/repository` の旧 jar `nablarch-testing-junit5-6-NEXT-SNAPSHOT.jar`（`emulateTestRules` 時代のもの。
`javap -p` で `InvocationInterceptor` を実装していないことを確認済み）と、`231eaa9` の `src/main` を
`javac` でコンパイルしたものを差し替えて、同じ利用者コードを `LauncherFactory` で実行した）:

```
利用者コード: resolveTestRules() でルールを 1 本返し、かつ interceptTestMethod を自分で override して
              invocation.proceed() を呼ぶ Extension

旧: LOG=[rule-before, rule-after, user-intercept-before, test, user-intercept-after]   succeeded=1 failed=0
新: LOG=[user-intercept-before, test, user-intercept-after]                            succeeded=1 failed=0
```

**コンパイルも通り、テストも成功する。** ルールが 1 本まるごと消えたことは、どこにも現れない。
§1.2 が「最も問題」とした「静かに成功する」形そのものである。

**`final` にすると、これがコンパイルエラーに変わる。静かな喪失が、ビルド時に必ず気づく失敗になる。**

**したがって互換上の損失はゼロではない。** 変更前の基底（`bc85712`）は `InvocationInterceptor` を実装して
おらず `interceptTestMethod` も持たなかったが、**利用者が自分で `implements InvocationInterceptor` して
`interceptTestMethod` を override する**コードは書けた。それが `final` 化後は通らなくなる。確認: `bc85712` と
`273ddd4`（当時の HEAD）の `src/main` をそれぞれ `javac` でコンパイルし、上と同じ利用者クラスを両方に対して
コンパイルしたところ、`bc85712` 側は EXIT=0、HEAD 側は `オーバーライドされたメソッドはfinalです` で EXIT=1
だった（Temurin 21.0.11）。**受け入れたのはこの非互換であって、「損失がない」のではない。**

**利用者がやりたいことは、別の Extension クラスで実現できる。ただし但し書きが 2 つ要る。**
`InvocationInterceptor` を実装した独立した Extension を作り、
`@ExtendWith({利用者のExtension.class, その割り込み.class})` のように併記すればよい。

- **登録順で内外が決まる。** 割り込みを**後に**書くとルールの内側に入り
  `[rule-before, user-before, test, user-after, rule-after]`、**先に**書くと外側に出て
  `[user-before, rule-before, test, rule-after, user-after]` になる（どちらも `succeeded=1 failed=0`）
- **別 Extension からは基底の `protected support` フィールドに届かない。** JUnit 5 には他の Extension の
  インスタンスを取得する API がなく（`javap -p ExtensionContext` の 24 メソッドにそれらしいものはない。§2.2）、
  `protected` なので同一パッケージ外からは参照もできない（別クラスに `other.support` と書くと
  `supportはTestEventDispatcherExtensionでprotectedアクセスされます` でコンパイルエラーになる）。拾えるのは、
  テストクラスがインジェクション先フィールドを宣言していて `postProcessTestInstance`（`231eaa9` の
  `TestEventDispatcherExtension.java:65-85`）がそこへ `support` を代入している場合に、
  `getRequiredTestInstance()` から反射で読むときだけ。宣言していなければ手段がない。
  **従来 `interceptTestMethod` を override していた利用者は `support` を直接触れていたので、ここが移行時に効く**

上の登録順 2 通りとフィールド宣言あり／なしは、実装と同じ形の基底 Extension（`final interceptTestMethod` の
中でルールを `invocation.proceed()` に巻く）と別 Extension を **JUnit 5.11.0 単体**で組み、`LauncherFactory` で
実行して確かめた（`mvn` は使っていない）。

**このリポジトリの `src/main` には、他に `final` メソッドが 1 つもない**（`231eaa9` の全 23 ファイルを
grep して確認した。`final` の出現は `NOOP_STATEMENT` の `static final` フィールドと、
`postProcessTestInstance` / `createSupport` の `final` 引数の 3 か所だけ）。
**その慣行から外れることは承知のうえで、静かな喪失を防ぐ価値が上回ると判断した。**

**この変更は `49f73f6` で入った。`final` が外れていないことは、`6716f98` で追加したテストが守る（§4.6）。**
タスク #5 の Javadoc に、`final` である理由と、上の「別の Extension クラスとして実装する」逃げ道を、
**上の 2 つの但し書きつきで**書く。

### 4.6 タスク #4 で恒久的なテストとして残したもの

§1.1 実測2 と §4.2 が「再現物なし」になった原因は、確認をスパイク（使い捨てのテストクラス）で行い、その場で捨てたこと。
同じ主張が二度と出典なしにならないよう、タスク #4 で**リポジトリに残るテスト**に起こした。

**件数は「surefire が収集するテストメソッドの数」で数えてある**（`JupiterEngineRunner` が実行する入れ子の
フィクスチャクラスは含まない）。**`231eaa9` の時点で入っているもの（合わせて 20 件）。** 出典は `git show 231eaa9 --stat` と `git show 231eaa9:src/test/java/nablarch/test/junit5/extension/event/<クラス名>.java`。

| クラス | 件 | 内容 | 対応する記述 |
|---|---|---|---|
| `TimeoutRuleIntegrationTest` | 2 | 解説書 rst:377-391 / rst:395-414 と同じ形で `Timeout` を渡すとタイムアウトすること／素の `ThreadLocal` に `beforeEach` で置いた値が `Timeout` 配下のテスト本体から見えないこと | §1.1 実測2、§4.4 (5) |
| `TestRuleInvocationContractIntegrationTest` | 3 | `base` を呼ばないルール／`base` を 2 回呼ぶルール（テスト本体は 1 回だけ走る）／ルールが `base` の前に投げた例外がそのまま伝播すること | §4.4 (1)、§4.5 (4) |
| `TestRuleLifecycleIntegrationTest` | 2 | `ExternalResource#before()` が `@BeforeEach` の後・`after()` が `@AfterEach` の前であること／`@BeforeEach` が失敗するとルールの前処理も後処理も走らないこと | §4.4 (2)(3) |
| `TestRuleDescriptionIntegrationTest` | 2 | `Description` からテストメソッドのアノテーションを取得できること／付いていないアノテーションは取得できないこと | §4.4 (4) |
| `TestRuleEmulationIntegrationTest` | 3 | `@Test` / `@ParameterizedTest` / `@RepeatedTest` でルールがテスト本体を包むこと | §1.1 実測1、§4.2、§2.3 |
| `StandardTestRuleIntegrationTest` | 8 | §4.4 冒頭の「動く」一覧表を固定する。`TemporaryFolder` 2 件（一時ファイルがテスト本体から使えテスト後に消える／`@BeforeEach` の時点では未作成でルールの前処理で作られる。後者は `6716f98` で補強、下記）・`ExpectedException`・`ErrorCollector`（収集 2 件のケースのみ。1 件のケースは未固定）・`Verifier`・`Stopwatch`・`RuleChain`・`DisableOnDebug` | §4.4 の一覧表、§4.4 (2) |

補助として、失敗するテストを surefire に拾わせずに実行結果だけを観測する `JupiterEngineRunner`（下記）と、ルールを
差し替えられる `ConfigurableTestRuleExtension` を置いた。その後 `09f8934` で、重複したフィクスチャを `RuleIntegrationTestBase` と `RecordingRule` に寄せている。

**今回のレビューを受けて足したもの**（`8885ac9`「test: TestRuleの未固定だった4つの振る舞いを恒久テストにする」）。

- テスト本体が投げた例外がルールの後処理を経てから**同一インスタンスのまま**伝播すること
  （`TestRuleInvocationContractIntegrationTest` に 1 件追加。同クラスは 4 件になった。§4.5 (4)）
- `Timeout` × `DbAccessTestExtension` で、テスト本体から DB コネクションもトランザクションも取れないこと
  （`TimeoutDbAccessIntegrationTest` 2 件。`DbAccessTestExtension` 単体との対照つき。§4.4 (5)）
- `@TestFactory` が生成した動的テストにルールが適用されず、例外にもならないこと（`TestFactoryRuleIntegrationTest`
  1 件。§4.4 (6)）。**対応しない現状を固定する特性テストであり、「タスク #4 の対象外」という判断は変わらない。**
  将来 `interceptTestFactoryMethod` / `interceptDynamicTest` に手を入れるとき、このテストが出発点を示す
- `RestTestExtension#setUpDb()` の実行時点で `testDescription` が設定済みであること
  （`RestTestExtensionTest` に 1 件追加。§1.3 の条件 2 を固定する）

**`Timeout` × `DbAccessTestExtension` を恒久テストにしたのは、「(a) 実 DB が要る、(b) 失敗が環境設定の誤りと区別
しにくい」として除外していた以前の判断を覆した結果である。** (a) — `231eaa9` の `src/test` に
`db/MockConnectionFactory.java` と `MockTransactionFactory.java` があり `resources/unit-test.xml` に登録済みで、実 DB も
追加設定も要らない。(b) — 失敗は `IllegalArgumentException: specified database connection name is not register in
thread local. connection name = [transaction]` という固有のメッセージになる（§4.4 (5)）。
**一方、`Timeout` 成立時の並行実行（§4.4 (5)）は恒久テストにせず、別課題として残す** —— タイムアウトの成立とスレッド割り込みのタイミングに依存し、実行環境の負荷で結果が揺れる不安定なテストになりやすいため。

**さらに `6716f98`「test: finalを守るテストを追加し、TestRule統合テストの表明を補強する」で足したもの。**

- **intercept メソッド 2 本が `final` であること**をリフレクションで表明（`TestEventDispatcherExtensionTest.java:190-201`。
  同クラスは 13 件）。**それまでは `final` を外しても 61 件が全件成功していた**——#4 の QA レビューが変異プローブ 7 種を
  当て「`final` を外す」だけが 1 件も落ちないことを実測しており（`checks/4.md:187`）、§4.5 (5) が置いた `final` を
  リポジトリ側が何も守っていなかった。同じ主張が出典なしにならないようにする、という本節の趣旨そのものの穴である
- `TemporaryFolder` の「`@BeforeEach` の時点では未作成」を、`RuleChain` で記録用ルールを併用して実行ログ全体
  （`["@BeforeEach:root-not-created", "rule-before", "test:root-exists", "rule-after"]`）を表明する形に補強した
  （`StandardTestRuleIntegrationTest.java:94-110`）。**「作られていない」だけの表明は、ルールが一切適用されない実装でも成功する。**
  補強後は「適用位置がずれている」と「適用されていない」を区別できる

**`JupiterEngineRunner` は `junit-platform-launcher` ベースに置き換える。**

`5b47d2c` で追加した `JupiterEngineRunner`（183 行）は `org.junit.jupiter.engine.JupiterTestEngine` を直接使っていた。
これは JUnit の内部 API である（`junit-jupiter-engine-5.11.0.jar` の `JupiterTestEngine.class` を `javap -v` に掛けると
`RuntimeVisibleAnnotations` が `org.apiguardian.api.API(status=INTERNAL, since="5.0")`）。**INTERNAL API は
JUnit 側の都合で予告なく変更・削除されうるのに、テスト側にその依存を増やしていた。**

**置き換えは `c06e4da`「build: JupiterEngineRunnerをjunit-platform-launcherベースに置き換える」で完了した。**
`pom.xml` が `junit-platform-launcher` を test スコープで宣言し（版は junit-bom から。§2.1）、
`JupiterEngineRunner` は 183 行から 141 行になり、さらに `6716f98` で `ExecutionSummary` の未使用メソッド 4 本
（`getTestCount` / `getAbortedTestCount` / `getSkippedTestCount` / `getFailures`）を削って **102 行**になった
（`git show <コミット>:src/test/java/nablarch/test/junit5/extension/JupiterEngineRunner.java | wc -l` で確認）。

**内部 API への依存は消えた。** `JupiterEngineRunner.class` と入れ子の `JupiterEngineRunner$ExecutionSummary.class`
（`target/test-classes`。作り直せば同じものが出る）を `javap -v` に掛け、定数プールの `org/junit/**` 参照を数えると、
**`org/junit/jupiter/engine/*` への参照はゼロ**である。残るのは `org/junit/platform/launcher/*`（`Launcher` /
`LauncherDiscoveryRequest` / `TestExecutionListener` / `core/LauncherFactory` / `core/LauncherDiscoveryRequestBuilder` /
`listeners/SummaryGeneratingListener` / `listeners/TestExecutionSummary`）と **`org/junit/platform/engine/*`**
（`DiscoverySelector` / `discovery/DiscoverySelectors` / `discovery/ClassSelector`）で、後者も公開 API である。

**ただし置き換え先が全部 STABLE になったわけではない。** `javap -v` でクラス宣言の `@API` を読むと、上の 10 型のうち
`SummaryGeneratingListener` と `TestExecutionSummary` の 2 つが **MAINTAINED**、残る 8 つが **STABLE** である。
`INTERNAL` は 1 つも残っていないので置き換えの目的は達しているが、**「STABLE に置き換えた」と一括りにはできない。**

**懸念していた surefire 2.22.2 との衝突は起きなかった。** 根拠は `target/surefire-reports/` の `TEST-*.xml` 27 個を集計した
**tests 61 / failures 0 / errors 0 / skipped 0**（生成時刻 2026-08-24 08:09:18〜22）。コーディネーターが `mvn -o clean test` を
実行して `Tests run: 61, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS` を得た実行で、`src/` 配下の最終更新は
2026-08-21 23:27・`git status` はクリーン——**この 61 件は `6716f98` 時点の `src/` に対応する。**

**`target/` はビルドのたびに作り直される。件数を引くときは、この節を正とし、どのコミット時点かを添えること。**
**本文書の作成者は `mvn` を実行していない**（別のビルドと `target/` が衝突するため）。上は他のエージェントが走らせたビルドの成果物を自分で集計し直した結果である。

## 5. 判断ポイント

### 5.1 判断1 — `resolveTestRules()` を存続させるか（**決定済み**）

> **決定: 1-A（存続させて直す）。** 2026-08-21、`/rn:ty 1-a` によりユーザーが決定した。
> 以降のタスク #4〜#6 は 1-A を前提とする。以下は、その決定の根拠と、決定に伴って受け入れた制約の記録である。

**背景。** JUnit は JUnit 4 の Rule をネイティブサポートしないと明言し、移行用モジュール
`junit-jupiter-migrationsupport` も次のメジャーバージョンで削除されると公表している（§2.1）。
本モジュールの `resolveTestRules()` はその流れの外にあるので、「直す」の前に「続けるか」を決める必要があった。

**選択肢**

| | 内容 |
|---|---|
| **1-A 存続して直す** | §4 の差分を入れる。解説書のコード例がそのまま動くようになる |
| **1-B 非推奨化して撤退** | `resolveTestRules()` に `@Deprecated` を付け、解説書から「TestRule を再現できる」という記述を撤回し、JUnit 5 の同等機能（`Timeout` → `@Timeout`、`ExternalResource` / `TemporaryFolder` → `BeforeEachCallback` / `@TempDir`、`ExpectedException` → `assertThrows`）への置き換えを案内する。実装は現状のままとし、`TestName` / `TestDescription` を設定する内部経路だけを残す |
| **1-C doc-only** | 実装も非推奨マークも変えず、「ルールはテスト本体を包まない」という制約を Javadoc と解説書に明記するだけ |
| **1-D 1-A + `@Deprecated` 併走** | §4 の差分を入れて直したうえで、同時に `resolveTestRules()` に `@Deprecated` を付け、JUnit 5 の機能への移行を促す |

**比較**

軸の意味を先に書いておく。**「§1.2 の不具合」**とは「ルールがテスト本体を包まない」ことで、解説書の `Timeout` の例・
`ExternalResource` の後処理順・NTF 公開 API の `SystemPropertyResource` は、いずれもこの 1 つの不具合の現れ方である。
**「JUnit の公表方針」**とは §2.1 の「JUnit 4 rule support は 6.0.0 で deprecated for removal」を指す。

| 判断軸 | 1-A 存続 | 1-B 撤退 | 1-C doc-only | 1-D 併走 |
|---|---|---|---|---|
| **§1.2 の不具合が直るか** | 直る（判断時は §4.2 のプロトタイプ実測。現在は §4.6 の恒久テストが正） | 直らない。「動かない」と明示し、記述を撤回する | 直らない。「動かない」と書くだけ | 直る |
| **JUnit の公表方針との整合** | **逆行する。** JUnit が捨てる方向の機構を、動くように整備する | **沿う。** 移行先を示して縮小する | 中立。現状を説明するだけ | **逆行する。** 直したうえで非推奨にするので、方向としては 1-A と同じ |
| **公開 API への影響** | ルールの実行位置が変わる（下記の非互換）。公開 API が **3 本＋interface 1 つ**増える（`resolveInternalTestRules()` / `interceptTestMethod` / `interceptTestTemplateMethod` / `implements InvocationInterceptor`。§4.5 (2)） | `@Deprecated` が付く。内部経路が非推奨 API を通り続ける（下記） | なし | 1-A と同じ + 非推奨マーク |
| **保守コスト** | **恒久的な制約 6 件（§4.4 (1)(2)(3)(5)(6)(8)）・非対称な例外ポリシー（§4.5 (4)）・後方互換を保証する公開 API 3 本＋interface 1 つ**を抱え続ける | 縮小方向。移行が済めば JUnit 4 の Rule に関する説明は不要になる | 制約は残るが、動かすための機構は増えない | 1-A と同じ。加えて非推奨警告の運用 |

**1-B でも内部経路の扱いを決める必要があった。** 内部経路（`testName` / `testDescription` の設定）は
`resolveTestRules()` を通っており `SimpleRestTestExtension` が override しているので、`@Deprecated` を付けると
本モジュール自身のビルドが警告を出す（override 側にも 1 行足せば消えることは `javac -Xlint:deprecation` で実測）。
**分離が要るのは技術上の必然ではなく、内部経路が非推奨 API を通り続けるという意味論の問題である。**

**変更範囲。** 1-A は `src/main` の差分（**行数は §4.1 を正とする**）+ 既存テスト 1 件の書き換え + 新規テスト
（**件数は §4.6 を正とする**）+ Javadoc（#5）+ 解説書 4 か所（#6）。1-B / 1-C / 1-D はコード変更がほぼなく、
`@Deprecated` 1〜2 行と解説書の書き直しが主で、代わりに**利用者側**がルールごとに JUnit 5 の機能へ書き換える。
1-B / 1-C ではさらに、現行実装に対して FAIL する `TestRuleEmulationIntegrationTest`（`8780eb8`。§1.1 実測1）を
削除するか期待値を反転させるかを選ぶ必要があり、反転は**現在の壊れた挙動を仕様として固定する**ことを意味した。

**「JUnit 4 から離れる」ことにはどれもならない、という点が判断の要。** NTF 本体の
`TestEventDispatcher#testName` が JUnit 4 の `@Rule TestName` であり（§1.2）、本モジュールの
`junit:junit:4.13.1` は compile スコープで `resolveTestRules()` の存廃とは無関係に残る（§2.1）。
JUnit の流れに沿わせる本丸は §5.3 の別課題であって、判断1 ではない。

**推奨は 1-A だった。理由は 3 つ。**

1. **1-B / 1-C は不具合を残す。** `ExternalResource` の後処理がテスト本体より前に走る状態は、
   非推奨マークを付けても文書に書いても消えない。移行が済むまで誤った実行順で動き続ける。
   §1.2 のとおり、NTF 自身が公開している `SystemPropertyResource` も対象に含まれる
2. **コード変更は本モジュールに閉じる。** `src/main` の差分は 2 ファイルに収まり（行数は §4.1 を正とする）、
   既存テストは 1 件しか落ちず、それは仕様変更そのもの（§4.3）。
   **落ちるのが 1 件だけという点はプロトタイプ実測で、再現物がない**（§4.2）。
   **ただしドキュメント変更は閉じない。** タスク #6 は別リポジトリ `nablarch-document` の 4 か所の修正を
   必要とする（§6）。これはどの選択肢でも同じ
3. **1-A のあとで 1-B へ進みやすい。** `resolveTestRules()` の意味が「利用者がテスト本体を包みたいルール」に
   定まるので、後から非推奨にする判断がしやすくなる。（**逆向きが不可能なわけではない。** 1-B を選んでも
   `@Deprecated` を外して §4 の差分を入れる道は塞がらない）

**1-D を採らなかった理由。** 直したうえで同時に非推奨にすると 2 つの相反するメッセージを同時に出すことになり、
しかも JUnit 4 依存の解消（§5.3）の方針が決まる前では、移行先を示せないまま警告だけが出る。
**非推奨化は §5.3 の結論とセットで判断する。**

**1-A を選んだことで受け入れた非互換。** §4.4 の **(1)(2)(3)(5)(6)(8)** がその一覧である。
(4) はタスク #4 で塞ぐので非互換にならず、(7)（`@Nested`）は 1-A 以前からある別課題なのでここには数えない。
加えて、`resolveTestRules()` に**前処理だけのルール**（`TestName` / `TestDescription` 相当）を渡していた場合、
その実行位置が `beforeEach`（NTF 前処理と同時）からテストメソッド直前（`@BeforeEach` の後）へ移る。
**この場合の移行手順は「参照側をテストメソッド内へ移す」ことである。** `@BeforeEach` の中でルールが設定した値
（`TestName#getMethodName()` 相当）を読んでいたコードは、テストメソッド本体へ移す。
**`resolveInternalTestRules()` へ移すことは案内しない。** そこに置いたルールは「テスト本体を包まない」＋
「例外が `RuntimeException` に包まれる」という第 2 の意味論を持ち（§4.5 (4)）、それを公開 API として利用者に
開くことは §4.5 (2) が自ら退けた道だからである。`resolveTestRules()` を実際に使っているプロジェクトの
有無は未確認（§2.3）。

**さらに、`interceptTestMethod` / `interceptTestTemplateMethod` を `final` にすること（§4.5 (5)）で、
コンパイルエラーになる利用者がいる。** 変更前の基底には両メソッドがなかったが、**自分で
`implements InvocationInterceptor` して override する**コードは書けた（`bc85712` に対して `javac` が通ることを
確認。§4.5 (5)）。それが通らなくなる。**これは意図した非互換である。** `final` にしなければ、同じ利用者の
ルールが一言もなく消える。移行手順は「割り込み処理を別の Extension クラスへ切り出し、`@ExtendWith` に
併記する」ことだが、**別 Extension からは基底の `protected support` フィールドに届かない**（§4.5 (5)）ので、
`support` を直接触っていた割り込みはそのままでは移せない。

**公開 API の数え方が変わっても、1-A の決定は覆らない。** 増える 3 本のうち 2 本は本設計の実装そのもの、
残る 1 本は NTF 内部専用で、いずれも `resolveTestRules()` の意味を定める代償として不可避だからである。
**ただし 1-A の保守コストは当初の見積もりより重い。** 1-B / 1-C との差は「公開 API 1 本」ではなく、
`implements InvocationInterceptor` を含む 4 つの後方互換の約束——しかもその interface は `final` の 2 本の
ほかに 8 本の override 可能面を開く（§4.5 (2)）——である。

### 5.2 判断2 — 直し方（**要件を満たす形が 1 つしかない。決定済み**）

「テスト本体を包む」ためには `invocation` を引数で受け取る必要があり、それができる拡張ポイントは
`InvocationInterceptor` しかない（§2.2）。そして `InvocationInterceptor` は `@BeforeEach` より後に
呼ばれる（§2.1）ため、NTF の内部ルールをそちらへ移すと `TestEventDispatcherExtensionLifecycleMethodTest` が壊れる。
したがって「利用者のルールを `InvocationInterceptor` へ、内部ルールを `beforeEach` に残す」以外に形がない。

**選択肢が思いつかないのではなく、要件を満たすものが 1 つしかない、という意味である。**
たとえば型ごとのアダプタを書く道（JUnit 公式の `junit-jupiter-migrationsupport` 方式）は存在するが、
対応できるのが 3 種類だけで `Timeout` を含まず（§2.1）、解説書が唯一の例に挙げている `Timeout` を救えない。
そのうえ同モジュールは次のメジャーで削除される。実装量も §4 の差分より大きい。要件を満たさないので採れない。

**唯一選択の余地があったのは §4.5 (2) の 1 点** —— 内部ルール用の `protected` メソッドを新設するか、
新設しない代替（代替 A / 代替 B）を採るか。**新設で決定した。ただし「公開 API を増やさない」が
優先方針になった場合の切り替え先として代替 A を残してある。**
そのほかの実装上の細部（(1)(3)(4)(5)）は §4.5 に記録した。

### 5.3 判断1 の外にある別課題 — NTF 本体から JUnit 依存を分離する

**結論から言うと、これは本件とは別に立てるべき課題である。** NTF 本体の変更を伴うため本リポジトリだけでは完結せず、
既存の JUnit 4 利用者への後方互換の検討も必要になる（§1.4）。

**何をする課題か。** NTF のロジックを JUnit のライフサイクル注釈から切り離し、JUnit 4 用・JUnit 5 用の薄い
アダプタを両側に置く。`TestEventDispatcher#testName` を素のフィールドにできれば、TestRule 再現機構は NTF 内部
からは不要になり、利用者向けに残すかどうかを純粋に方針として決められる。JUnit の流れに沿うのはこれ。
§5.1 で「非推奨化は §5.3 の結論とセット」と書いたのはこの意味。

**規模感と障壁。** `nablarch-testing` 6-NEXT-SNAPSHOT sources jar の 185 java ファイル中、`org.junit` を
import しているのは **9 ファイル**（`unzip` して `grep -rl "^import.*org\.junit" --include=*.java .` と
`find . -name '*.java' | wc -l` で数えた）。内訳は **ライフサイクル注釈・ルールが 4、表明が 5（うち静的 import のみが 4）**。

- **ライフサイクル注釈・ルール（4）** — `nablarch/test/event/TestEventDispatcher`（`@Rule TestName` と
  `@BeforeClass` / `@Before` / `@After` / `@AfterClass`。`:5-10`）、`nablarch/test/core/db/DbAccessTestSupport`
  （`@Before` / `@After`。`:15-16`）、`nablarch/test/core/integration/IntegrationTestSupport`（`@Before`。`:9`）、
  `nablarch/test/SystemPropertyResource`（`extends org.junit.rules.ExternalResource`。`:4`）
- **表明（5）** — `nablarch/test/Assertion` だけが `org.junit.Assert` / `org.junit.ComparisonFailure` を
  `:13-14` で通常の import。残る 4 ファイル（`EntityTestSupport` / `SingleValidationTester` /
  `ServletForwardVerifier` / `MessagingRequestTestSupport`）は `org.junit.Assert` の静的 import のみ

表明側の 5 ファイルは `org.junit.Assert` の呼び出しを別の手段（Hamcrest 直呼びなど）に置き換えれば済み、
`Assertion.java` が `ComparisonFailure` を投げている点だけ代替の型を決める必要がある。残る 4 ファイルが本題だが、
`SystemPropertyResource` は `ExternalResource` を捨てて素のクラスにでき、`IntegrationTestSupport` は
`@Before` が 1 つだけ。**ただしこの規模感は import の分布から見た印象であり、実際の分離作業は行っていない（未確認）。**

## 6. 解説書の通りになるか（タスク #6 の下ごしらえ）

**1-A（存続させて直す）を選んだ前提での話。コード例はほぼそのまま動くようになるが、4 か所直す必要がある**
（既存 3 か所の修正 + 新規 1 か所の追加）。

出典はいずれも `nablarch/nablarch-document` のコミット `5391d5c`（`origin/main`、2026-08-05）の
`ja/development_tools/testing_framework/guide/development_guide/06_TestFWGuide/JUnit5_Extension.rst`。

| 解説書の箇所 | 修正後の状態 |
|---|---|
| rst:377-391 `CustomTestSupport`（`@Rule Timeout` を持つ独自サポートクラス）の例 | そのまま。変更不要 |
| rst:395-414 `CustomTestSupportExtension`（`resolveTestRules()` のオーバーライド）の例 | **要修正。** `:407-408` に `// 2. 親クラスの resolveTestRules() の結果をベースにしてリストを生成する` と `List<TestRule> rules = new ArrayList<>(super.resolveTestRules());` があり、基底実装が空リストを返すようになる（§4.5 (1)）ため成り立たない。`new ArrayList<>()` から始める形に書き換え、コメント 2 を削る |
| rst:416-418「これにより、JUnit 5のテスト上でもJUnit 4の `TestRule` を再現できるようになる」 | **要修正。** §4.4 の (1)(2)(3)(6) を追記する。すなわち、(a) 包む範囲がテストメソッドのみで `@BeforeEach` / `@AfterEach` を含まないため、**`ExternalResource#before()` は `@BeforeEach` の後、`after()` は `@AfterEach` の前に実行される**こと（`TemporaryFolder` を `@BeforeEach` から使うと `IllegalStateException` になり、`TestWatcher` は `@AfterEach` の失敗を観測できない）、(b) **`@BeforeEach` が失敗するとルールの前処理も後処理も一切走らない**こと、(c) `base` を呼ばないルール・2 回以上呼ぶルール（retry 系）は使えないこと、(d) `@TestFactory` / `DynamicTest` には適用されないこと |
| rst:420-421「必ず親クラスの `resolveTestRules()` が返すリストをベースにすること。そうしない場合、親クラスで登録している `TestRule` が再現されなくなる」 | **要修正。** 基底実装が空リストを返すようになるため、この理由づけは成り立たなくなる（§4.5 (1)）。あわせて**移行手順**を書く: 「前処理だけのルール（`TestName` / `TestDescription` 相当）をこれまで `resolveTestRules()` に渡していた場合、その実行位置は `@BeforeEach` の後へ移る。`@BeforeEach` からルールが設定した値（`TestName#getMethodName()` 相当）を参照していた場合は、参照側をテストメソッド内へ移すこと」（§5.1 の非互換）。**`resolveInternalTestRules()` は NTF 内部専用なので、移行先としては案内しない**（§4.5 (2)） |
| （新規）rst:395-414 の直後 | **要追加。** §4.4 (5) の警告。**この節の唯一の例が `Timeout` である以上、これは必須。** `Timeout` はテスト本体を別スレッドで実行するため、`DbAccessTestExtension`（`@DbAccessTest`）と併用するとテスト本体から DB コネクションが取れない。またタイムアウトが成立し、かつテスト本体が割り込みに反応しない処理を実行している場合は、テスト本体と後処理が並行実行される。どちらも**実測済み**（前者は恒久テストにもした。§4.4 (5)、§4.6） |

**例外の扱いの変更（`RuntimeException` に包まれなくなる。§4.5 (4)）と `resolveInternalTestRules()` は、
解説書には入れない。** 前者は当該節（rst:370-421）に例外の扱いの記述がなく、書かれていない前提が変わっただけだから。
後者は NTF 内部専用であり（§4.5 (2)）、利用者に開くと非対称な例外ポリシーを公開 API として抱えることになるから。
どちらも Javadoc（タスク #5）には明記する。**判断したことを残すためにここに書いておく。**
なお **intercept メソッドを `final` にすること（§4.5 (5)）も解説書には入れない。** 解説書の手順どおりに
`resolveTestRules()` を override するだけの利用者には影響がなく、影響を受ける利用者はコンパイルエラーで気づくため。

**利用者が書くコードはほとんど変わらない。** 解説書の手順どおりに `resolveTestRules()` をオーバーライドすれば
`Timeout` は実際にタイムアウトし、`ExternalResource` の後処理はテスト本体の後（ただし `@AfterEach` の前。
§4.4 (2)）に実行される。変わるのは `super.resolveTestRules()` を呼ぶ 1 行と、§4.4 の制約の範囲内で、である。
これはタスク #6 で ja / en 両方の差分案として起こす。en 側の対応箇所の行番号は**未確認**。
