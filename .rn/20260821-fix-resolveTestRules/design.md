# TestRule 再現機構 — design notes

Not read at runtime — for whoever maintains the design and needs to judge whether a decision is still
right when requirements change.

調査日 2026-08-21 / 基準コミット 47ec258 / JUnit 5.11.0・junit:junit 4.13.1・Java 17

## 1. Background & Goals

### 1.1 What is the goal?

解説書「JUnit 5用拡張機能 § [JUnit 4のTestRuleを再現する](https://nablarch.github.io/docs/LATEST/doc/development_tools/testing_framework/guide/development_guide/06_TestFWGuide/JUnit5_Extension.html#junit-4testrule)」
に書かれているとおりに実装すれば JUnit 4 の `TestRule` が再現される、という状態にする。

現状はそうなっていない。再現されるのは `TestRule` のうち「テスト本体より前に実行される部分」だけで、
後ろの部分はテスト本体より前に実行される。解説書が例に挙げている `Timeout` は、まったく機能しない。

**再現テストによる確認**

```
$ mvn -o clean test -Dtest=TestRuleEmulationIntegrationTest
Expected: is <[rule-before, test]>
     but: was <[rule-before, rule-after, test]>
```

出典: `src/test/java/nablarch/test/junit5/extension/event/TestRuleEmulationIntegrationTest.java`（本セッションで追加）

あわせて、解説書の `Timeout` の例をそのまま実装し、1 秒のタイムアウトに対して 2 秒スリープする
テストを実行したが、タイムアウトせずに成功した。

### 1.2 What goes wrong without this?

**NTF 本体は影響を受けていない。** NTF が使っている `TestRule` は
`TestEventDispatcher#testName`（`org.junit.rules.TestName`）と
`SimpleRestTestSupport#testDescription`（`nablarch.test.core.rule.TestDescription`）の 2 つだけで、
どちらも `Description` からテストメソッド名を控えるだけのもの。テスト本体を必要としない。

**壊れているのは、利用者が自分の `TestRule` を持ち込む経路だけ。** 具体的には次のようなルールが機能しない。

| ルールの種類 | 現状 |
|---|---|
| `TestName` のように前処理だけのもの | 動作する |
| `Timeout` のようにテスト本体の実行を監視するもの | 何も検知しない |
| `ExternalResource` / `TemporaryFolder` のように後処理を持つもの | 後処理がテスト本体より前に実行される |
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
- **nablarch-testing 本体の変更。** §5.1 の案 C にあたる。本リポジトリだけでは完結しないため、別課題とする。
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

- `junit:junit:4.13.1` を **compile スコープ**で依存（利用者へ推移的に伝播する）。出典: `pom.xml`
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

**JUnit 5 には、次の 2 つを同時に満たす拡張ポイントが存在しない。**

| | テストメソッドを呼び出す処理を引数で受け取れるか | 呼ばれる順番 |
|---|---|---|
| `BeforeEachCallback#beforeEach(ExtensionContext)` | 受け取れない | `@BeforeEach` より前 |
| `InvocationInterceptor#interceptTestMethod(Invocation, …)` | 受け取れる（`invocation`） | `@BeforeEach` より後 |

`ExtensionContext` にテストメソッドの呼び出しを表すものはない。`getRequiredTestMethod()` が返すのは
`java.lang.reflect.Method` で、これを自分で `invoke` すると JUnit 5 も改めて呼ぶためテストが 2 回走る。

これが §1.3 の 2 条件が両立しなかった構造的な理由であり、本設計の出発点である。

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
| `TestEventDispatcherExtension#beforeEach`（`BeforeEachCallback`） | 内部ルールの適用と NTF 前処理の実行。実行位置は現行から変えない |
| 内部ルール解決（新設、非公開 API） | `TestName` / `TestDescription` を返す。`SimpleRestTestExtension` が `testDescription` を追加する |
| `TestEventDispatcherExtension#interceptTestMethod`（`InvocationInterceptor`、新設） | `resolveTestRules()` が返すルールで `invocation.proceed()` を包む |
| `resolveTestRules()`（既存、`@Published`） | 利用者がテスト本体を包みたいルールを返す。基底実装は空リストを返す |

### 3.3 How does work move?

```
1. BeforeEachCallback#beforeEach
     内部ルール（TestName / TestDescription）を NOOP に対して適用 → テストメソッド名が確定
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

### 4.1 テスト本体を包むルールの適用（`interceptTestMethod`）

**保証すること**: `resolveTestRules()` が返した `TestRule` は、`base.evaluate()` の呼び出しが
実際のテストメソッドの実行に対応する形で適用される。すなわち JUnit 4 と同じ意味で機能する。

**破れの検出**: `TestRuleEmulationIntegrationTest` が、ルールの前処理・テスト本体・後処理の実行順を
`[rule-before, test, rule-after]` として検証する。現行実装では FAIL する（§1.1）。
加えて、解説書と同じ `Timeout` の例でタイムアウトが発生することを確認する。

**補足**: JUnit 5.11.0 上で `interceptTestMethod` の `invocation.proceed()` を `Timeout` で包む実験を行い、
`TestTimedOutException: test timed out after 1000 milliseconds` が発生することを確認済み。
`Timeout` はテスト本体を別スレッドで実行するが、別スレッドからの `proceed()` も JUnit 5 は許容する。

`@ParameterizedTest` / `@RepeatedTest` は `interceptTestMethod` を通らないため、
`interceptTestTemplateMethod` にも同じ処理を実装する。

### 4.2 テスト名を先に確定させる内部経路

**保証すること**: 利用者の `@BeforeEach` が実行される時点で、`support.testName` と
`SimpleRestTestSupport#testDescription` に実行中のテストメソッド名が設定されている。

**破れの検出**: `TestEventDispatcherExtensionLifecycleMethodTest:31-33` が、`@BeforeEach` の中で
Extension の前処理が実行済みであることと `testName.getMethodName()` が期待値であることを検証する。
`RestTestExtensionIntegrationTest` 系が `setUpDb()` 経由で `testDescription` の設定を間接的に検証する。

**なぜ空の `Statement` を使い続けるのか**: `TestName` の内部フィールドは private で、`apply()` が返す
`Statement` を評価する以外に値を設定する手段がない。`TestName` / `TestDescription` はいずれも
`TestWatcher` の `starting()` で値を控えるだけなので、`base` が空でも目的を達する。
`nablarch-testing` 本体を変更できるなら不要になる仕組みであり、その場合の扱いは §5.1 の案 C にあたる。

### 4.3 再現できない範囲の明示

**保証すること**: 利用者のルールが包む範囲は**テストメソッドの呼び出しのみ**であり、
`@BeforeEach` / `@AfterEach` および NTF の前後処理は含まれない。この差分が Javadoc と解説書に明記されている。

**なぜ包めないのか**: JUnit 5 に、自身の `BeforeEachCallback` を `Statement` で包む手段がない。
`InvocationInterceptor` は個々の呼び出しを個別に横取りするだけで、
「テスト 1 件のライフサイクル全体」を 1 つの単位として受け取ることはできない。

**破れの検出**: Javadoc と解説書の記述レビュー。実装で機械的に検出する手段はない。

## 5. Alternatives considered

### 5.1 Why this shape, and not another?

**案 A — 解説書と Javadoc だけを直す（実装は現状維持）**

「再現されるのはテスト本体より前の処理だけ」と明記し、`Timeout` の例を撤回して JUnit 5 の
`@Timeout` への置き換えを案内する。JUnit の方針とは整合する。

採らない理由は 2 つ。`ExternalResource` 系の後処理が誤った順序で実行される問題が残ること。
そしてそれを正しくするには JUnit 公式と同様に型ごとのアダプタが必要になり、結局採用案より実装量が増えること。

**案 B — TestRule の適用先を分離する（採用）**

§3.1 のとおり。変更が本モジュール内に閉じ、解説書の記述どおりに動くようになる。
案 C を将来選んだ場合も、公開 API の意味づけが整理された状態は無駄にならない。

**案 C — nablarch-testing 本体から JUnit 依存を分離する**

NTF のロジックを JUnit のライフサイクル注釈から切り離し、JUnit 4 用・JUnit 5 用の薄いアダプタを両側に置く。
`TestEventDispatcher#testName` を素のフィールドにできれば、TestRule 再現機構は NTF 内部からは不要になり、
利用者向けに残すかどうかを純粋に方針として決められる。

§2.1 のとおり結合は 5 クラスに限られており技術的な障壁は低い。しかし nablarch-testing 本体の変更を伴うため
本リポジトリだけでは完結せず、既存の JUnit 4 利用者への後方互換の検討も必要になる。
本件の不具合修正と同じ土俵で決めるべきではないため、別課題とする。

**比較**

| 観点 | 案 A | 案 B（採用） | 案 C |
|---|---|---|---|
| 解説書どおりに動くか | × 記述を撤回 | ○ | △ 方針次第 |
| 変更範囲 | 解説書・Javadoc | 本モジュール内 | nablarch-testing 本体 |
| JUnit の方針との整合 | 整合 | より広い | 整合 |
| JUnit 4 依存の解消 | しない | しない | する |
| 既存利用者への影響 | なし | ルールの実行位置が変わる | 要検討 |

### 5.2 What did we trade away?

- **JUnit 4 との完全一致。** 利用者のルールが包むのはテストメソッドのみで、`@BeforeEach` / `@AfterEach` は
  含まれない。`@BeforeEach` の中の処理までタイムアウト対象にしたい場合や、`@BeforeEach` で用意した
  リソースをルールで後始末したい場合は再現できない。JUnit 5 の構造上、回避手段がない（§4.3）。
- **JUnit の方針との整合。** JUnit は限定サポートすら「実行フローを変えないルール」に絞っているが、
  本設計は種類を問わず `apply()` を呼ぶ。既に本モジュールが踏み込んでいる範囲ではあるが、
  JUnit の方向性から外れた機能を持ち続けることになる。Javadoc と解説書で「新規に書くなら JUnit 5 の
  同等機能を使う。`resolveTestRules()` は既存資産をそのまま持ち込むための経路」という位置づけを明示する。
- **既存利用者への非互換。** `resolveTestRules()` に前処理だけのルールを渡していた場合、その実行位置が
  `beforeEach` からテストメソッド直前へ移る。利用実態が未確認（§2.1）のため、影響範囲は見積もれていない。
- **JUnit 4 依存そのもの。** 本設計では解消しない。`junit:junit` への compile 依存は残る（案 C の範囲）。
